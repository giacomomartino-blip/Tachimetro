package it.giacomo.tachimetro.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.libraries.navigation.ForceNightMode
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.SpeedAlertOptions
import com.google.android.libraries.navigation.SpeedAlertSeverity
import com.google.android.libraries.navigation.SpeedingListener
import com.google.android.libraries.navigation.Waypoint
import it.giacomo.tachimetro.BuildConfig
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.NavUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Navigazione con Google Maps dentro l'app (Navigation SDK). Waze si apre solo su richiesta. */
object NavController {
    var navigator: Navigator? = null
        private set
    private var pending: (() -> Unit)? = null
    var speedAlertMode = "tone"
    var tolerance = 5
    var onSpeedingTone: (() -> Unit)? = null
    private var lastTone = 0L
    private var initializing = false

    fun init(activity: Activity) {
        if (navigator != null || initializing) return
        if (!BuildConfig.HAS_MAPS_KEY) {
            Hub.mapsStatus.value = "Manca la chiave di Google Maps: la mappa si attiva appena la inserisci."
            return
        }
        initializing = true
        val start = {
            NavigationApi.getNavigator(activity, object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(n: Navigator) {
                    navigator = n
                    initializing = false
                    Hub.mapsStatus.value = null
                    n.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                    applySpeedOptions()
                    n.setSpeedingListener(SpeedingListener { _, severity ->
                        val over = severity != SpeedAlertSeverity.NONE
                        Hub.speeding.value = over
                        if (over && speedAlertMode == "tone" && System.currentTimeMillis() - lastTone > 30_000) {
                            lastTone = System.currentTimeMillis()
                            onSpeedingTone?.invoke()
                        }
                    })
                    pending?.invoke()
                    pending = null
                }

                override fun onError(errorCode: Int) {
                    initializing = false
                    Hub.mapsStatus.value = "Mappa non disponibile (codice $errorCode). Controlla la chiave di Google Maps e la connessione."
                }
            })
        }
        if (NavigationApi.areTermsAccepted(activity.application)) {
            start()
        } else {
            NavigationApi.showTermsAndConditionsDialog(activity, "Tachimetro") { accepted ->
                if (accepted) {
                    start()
                } else {
                    initializing = false
                    Hub.mapsStatus.value = "Per usare la mappa servono i termini di Google."
                }
            }
        }
    }

    fun applySpeedOptions() {
        val n = navigator ?: return
        val pct = (tolerance * 2).coerceIn(1, 40).toFloat()
        try {
            n.setSpeedAlertOptions(
                SpeedAlertOptions.Builder()
                    .setSpeedAlertThresholdPercentage(SpeedAlertSeverity.MINOR, pct)
                    .setSpeedAlertThresholdPercentage(SpeedAlertSeverity.MAJOR, pct + 10f)
                    .setSeverityUpgradeDurationSeconds(5.0)
                    .build(),
            )
        } catch (e: Exception) {
        }
    }

    suspend fun geocode(context: Context, query: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        try {
            val g = Geocoder(context, Locale.ITALY)
            val here = Hub.location.value
            @Suppress("DEPRECATION")
            val list = if (here != null) {
                g.getFromLocationName(query, 1, here.latitude - 1.5, here.longitude - 1.5, here.latitude + 1.5, here.longitude + 1.5)
                    ?.takeIf { it.isNotEmpty() } ?: g.getFromLocationName(query, 1)
            } else {
                g.getFromLocationName(query, 1)
            }
            val a = list?.firstOrNull() ?: return@withContext null
            Triple(a.latitude, a.longitude, a.featureName ?: query)
        } catch (e: Exception) {
            null
        }
    }

    fun go(context: Context, lat: Double, lng: Double, title: String) {
        val n = navigator
        if (n == null) {
            if (BuildConfig.HAS_MAPS_KEY && Hub.mapsStatus.value == null) {
                pending = { go(context, lat, lng, title) }
                return
            }
            // senza mappa integrata si usa l'app Google Maps
            openGoogleMaps(context, lat, lng, false)
            Hub.nav.value = NavUi(active = true, destination = title)
            return
        }
        val wp = Waypoint.builder().setLatLng(lat, lng).setTitle(title).build()
        n.setDestination(wp).setOnResultListener { status ->
            if (status == Navigator.RouteStatus.OK) {
                n.startGuidance()
                Hub.nav.value = NavUi(active = true, destination = title)
            } else {
                Hub.nav.value = NavUi()
                Hub.mapsStatus.value = "Percorso non trovato verso $title"
            }
        }
    }

    /** Libera la navigazione quando chiudi Tachimetro, così non resta nulla attivo. */
    fun release() {
        stop()
        // libera il navigatore (il nome del metodo è cambiato tra le versioni dell'SDK)
        val n = navigator
        try {
            if (n != null) n.javaClass.getMethod("cleanup").invoke(n)
        } catch (e: Exception) {
            try { NavigationApi::class.java.getMethod("cleanup").invoke(null) } catch (e2: Exception) { }
        }
        navigator = null
        initializing = false
        pending = null
    }

    fun stop() {
        navigator?.let {
            it.stopGuidance()
            it.clearDestinations()
        }
        Hub.nav.value = NavUi()
    }

    fun openGoogleMaps(context: Context, lat: Double, lng: Double, walking: Boolean) {
        val mode = if (walking) "&mode=w" else ""
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng$mode"))
            .setPackage("com.google.android.apps.maps")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        try {
            context.startActivity(i)
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Apre Waze accanto a Tachimetro, con la destinazione già impostata. */
    fun openWaze(context: Context, query: String?, lat: Double?, lng: Double?, title: String) {
        val url = if (lat != null && lng != null) {
            "https://waze.com/ul?ll=$lat,$lng&navigate=yes&utm_source=tachimetro"
        } else {
            "https://waze.com/ul?q=" + Uri.encode(query ?: title) + "&navigate=yes&utm_source=tachimetro"
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        try {
            context.startActivity(i)
            navigator?.stopGuidance()
            Hub.nav.value = NavUi(active = true, destination = title, viaWaze = true)
        } catch (e: Exception) {
            Hub.mapsStatus.value = "Waze non è installato"
        }
    }
}

/** La mappa di navigazione di Google, collegata al ciclo di vita della schermata. */
@Composable
fun NavMap(night: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember { NavigationView(context) }

    DisposableEffect(lifecycle) {
        view.onCreate(Bundle())
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
            view.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        (context as? Activity)?.let { NavController.init(it) }
        view.setSpeedometerEnabled(true)
        view.setSpeedLimitIconEnabled(true)
    }

    LaunchedEffect(night) {
        view.setForceNightMode(if (night) ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY)
    }

    // orario di arrivo e distanza residua
    LaunchedEffect(Unit) {
        while (true) {
            val n = NavController.navigator
            val cur = Hub.nav.value
            if (n != null && cur.active && !cur.viaWaze) {
                try {
                    val td = n.currentTimeAndDistance
                    if (td != null) {
                        val eta = LocalTime.now().plusSeconds(td.seconds.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                        val km = td.meters / 1000.0
                        val min = td.seconds / 60
                        Hub.nav.value = cur.copy(
                            etaText = eta,
                            remainingText = String.format(Locale.ITALIAN, "%d min · %.1f km", min, km),
                        )
                    }
                    if (!n.isGuidanceRunning) Hub.nav.value = NavUi()
                } catch (e: Exception) {
                }
            }
            delay(5000)
        }
    }

    AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
}

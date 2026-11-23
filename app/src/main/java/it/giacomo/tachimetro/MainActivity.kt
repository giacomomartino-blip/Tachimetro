package it.giacomo.tachimetro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.nav.NavController
import it.giacomo.tachimetro.ui.AppBarScreen
import it.giacomo.tachimetro.ui.DAY
import it.giacomo.tachimetro.ui.DriverDashboard
import it.giacomo.tachimetro.ui.FuelScreen
import it.giacomo.tachimetro.ui.LocalPal
import it.giacomo.tachimetro.ui.ModeChooser
import it.giacomo.tachimetro.ui.NIGHT
import it.giacomo.tachimetro.ui.ParkingScreen
import it.giacomo.tachimetro.ui.PassengerScreen
import it.giacomo.tachimetro.ui.SettingsScreen
import it.giacomo.tachimetro.ui.rememberAutoNight
import it.giacomo.tachimetro.voice.VoiceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        restartVoice()
    }

    private var settings = AppSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readScreenExtra(intent)

        lifecycleScope.launch {
            Prefs.flow(applicationContext).collect { s ->
                settings = s
                applyWindow(s, Hub.health.value.hot)
                NavController.speedAlertMode = s.speedAlert
                if (NavController.tolerance != s.tolerance) {
                    NavController.tolerance = s.tolerance
                    NavController.applySpeedOptions()
                }
            }
        }
        lifecycleScope.launch { Hub.health.collect { applyWindow(settings, it.hot) } }
        lifecycleScope.launch { Hub.actions.collect { handle(it) } }
        NavController.onSpeedingTone = { beep() }

        setContent { TachiRoot(onAskPermissions = { askPermissions.launch(permissions) }) }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions.launch(permissions)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!VoiceService.running) VoiceService.start(this)
        // la mappa riparte da sola dopo aver concesso la posizione
        if (BuildConfig.HAS_MAPS_KEY && NavController.navigator == null &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            NavController.init(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readScreenExtra(intent)
    }

    private fun readScreenExtra(i: Intent?) {
        val name = i?.getStringExtra(EXTRA_SCREEN) ?: return
        runCatching { Screen.valueOf(name) }.getOrNull()?.let { Hub.screen.value = it }
    }

    private fun restartVoice() {
        stopService(Intent(this, VoiceService::class.java))
        VoiceService.start(this)
    }

    private fun applyWindow(s: AppSettings, hot: Boolean) {
        if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = if (s.healthAlerts && hot) 0.35f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    private fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            window.decorView.postDelayed({ tg.release() }, 400)
        } catch (e: Exception) {
        }
    }

    private fun say(text: String) {
        startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_SAY).putExtra(VoiceService.EXTRA_TEXT, text))
    }

    private suspend fun handle(a: UiAction) {
        when (a) {
            is UiAction.NavigateTo -> {
                if (a.waze) {
                    NavController.openWaze(this, a.query, null, null, a.query)
                } else {
                    val g = NavController.geocode(this, a.query)
                    if (g == null) say("Non trovo ${a.query}.") else NavController.go(this, g.first, g.second, a.query)
                }
            }
            is UiAction.NavigateCoords -> {
                if (a.waze) NavController.openWaze(this, null, a.lat, a.lng, a.title)
                else NavController.go(this, a.lat, a.lng, a.title)
            }
            UiAction.StopNavigation -> NavController.stop()
            UiAction.BackToMaps -> {
                Hub.nav.value = NavUi()
                Hub.screen.value = Screen.DASHBOARD
            }
            is UiAction.Show -> Hub.screen.value = a.screen
            is UiAction.SetMode -> {
                Prefs.edit(applicationContext) { it[Keys.mode] = if (a.driver) "driver" else "passenger" }
                Hub.screen.value = if (a.driver) Screen.DASHBOARD else Screen.PASSENGER
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}

@Composable
fun TachiRoot(onAskPermissions: () -> Unit) {
    val context = LocalContext.current
    val flow = remember { Prefs.flow(context.applicationContext) }
    val sOrNull by flow.collectAsState(initial = null)
    val s = sOrNull
    val loc by Hub.location.collectAsState()
    val screen by Hub.screen.collectAsState()
    val auto = rememberAutoNight(loc?.latitude, loc?.longitude)
    if (s == null) {
        Box(Modifier.fillMaxSize().background(NIGHT.bg))
        return
    }
    val night = when (s.themeMode) {
        "day" -> false
        "night" -> true
        else -> auto
    }
    val pal = if (night) NIGHT else DAY
    var chosen by rememberSaveable { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(s.startMode) {
        if (!chosen && s.startMode != "ask") {
            chosen = true
            Prefs.edit(context) { it[Keys.mode] = s.startMode }
            Hub.screen.value = if (s.startMode == "passenger") Screen.PASSENGER else Screen.DASHBOARD
        }
    }

    // protezione anti burn-in: sposta di poco tutta l'interfaccia ogni minuto
    var dx by remember { mutableIntStateOf(0) }
    var dy by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.burnIn) {
        while (s.burnIn) {
            delay(60_000)
            dx = Random.nextInt(-3, 4)
            dy = Random.nextInt(-3, 4)
        }
        dx = 0
        dy = 0
    }

    CompositionLocalProvider(LocalPal provides pal) {
        Box(Modifier.fillMaxSize().background(pal.bg).offset(dx.dp, dy.dp)) {
            if (!chosen && s.startMode == "ask") {
                ModeChooser { driver ->
                    chosen = true
                    scope.launch { Prefs.edit(context) { it[Keys.mode] = if (driver) "driver" else "passenger" } }
                    Hub.screen.value = if (driver) Screen.DASHBOARD else Screen.PASSENGER
                }
            } else {
                when (screen) {
                    Screen.DASHBOARD -> if (s.mode == "passenger") PassengerScreen(s, night) else DriverDashboard(s, night)
                    Screen.PASSENGER -> PassengerScreen(s, night)
                    Screen.SETTINGS -> SettingsScreen(s, onAskPermissions)
                    Screen.APP_BAR -> AppBarScreen(s)
                    Screen.PARKING -> ParkingScreen(s)
                    Screen.FUEL -> FuelScreen(s)
                }
            }
        }
    }
}

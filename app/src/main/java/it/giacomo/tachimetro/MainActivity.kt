package it.giacomo.tachimetro

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import it.giacomo.tachimetro.car.Car
import it.giacomo.tachimetro.car.CarLink
import it.giacomo.tachimetro.car.Dnd
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.media.MediaSessions
import it.giacomo.tachimetro.media.Radio
import it.giacomo.tachimetro.nav.NavController
import it.giacomo.tachimetro.notify.NotificationReader
import it.giacomo.tachimetro.ui.AppBarScreen
import it.giacomo.tachimetro.ui.DAY
import it.giacomo.tachimetro.ui.DriverDashboard
import it.giacomo.tachimetro.ui.FuelScreen
import it.giacomo.tachimetro.ui.LocalPal
import it.giacomo.tachimetro.ui.ModeChooser
import it.giacomo.tachimetro.ui.NIGHT
import it.giacomo.tachimetro.ui.ParkingScreen
import it.giacomo.tachimetro.ui.WaitingForCar
import it.giacomo.tachimetro.ui.SetupScreen
import it.giacomo.tachimetro.ui.StatusScreen
import it.giacomo.tachimetro.ui.WakeTestScreen
import it.giacomo.tachimetro.ui.PassengerScreen
import it.giacomo.tachimetro.ui.SettingsScreen
import it.giacomo.tachimetro.ui.rememberAutoNight
import it.giacomo.tachimetro.voice.QuickAsk
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
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
    )

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        stopService(Intent(this, VoiceService::class.java))
        evaluateGate()
    }

    private var settings = AppSettings()
    private var settingsLoaded = false
    private var gateKey = ""
    private var quickAsk: QuickAsk? = null
    private var askedThisSession = HashSet<String>()
    private val ui = Handler(Looper.getMainLooper())

    /** Collegamento e scollegamento del Bluetooth mentre Tachimetro è aperto. */
    private val carReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                evaluateGate()
                return
            }
            @Suppress("DEPRECATION")
            val dev = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val addr = try { dev.address } catch (e: SecurityException) { return }
            when (i.action) {
                // l'autoradio collega vivavoce e musica qualche secondo dopo: riprovo più volte
                BluetoothDevice.ACTION_ACL_CONNECTED -> listOf(1500L, 4000L, 8000L).forEach { d ->
                    ui.postDelayed({ if (Hub.carGate.value != CarGate.READY || Hub.closingIn.value != null) evaluateGate() }, d)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (isKnownCar(addr)) onCarDisconnected()
            }
        }
    }

    private fun isKnownCar(addr: String) = settings.knownCars.any { it.startsWith(addr, true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // se l'app era stata chiusa di colpo, rimette «Non disturbare» com'era
        Dnd.restore(this)
        readScreenExtra(intent)

        lifecycleScope.launch {
            Prefs.flow(applicationContext).collect { s ->
                settings = s
                settingsLoaded = true
                val key = listOf(s.mode, s.requireCar, s.knownCars.joinToString(","), s.notCars.joinToString(",")).joinToString("|")
                if (key != gateKey) {
                    gateKey = key
                    evaluateGate()
                }
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

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, carReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent { TachiRoot(onAskPermissions = { askPermissions.launch(permissions) }) }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions.launch(permissions)
        }
    }

    override fun onResume() {
        super.onResume()
        if (settingsLoaded) evaluateGate()
    }

    override fun onDestroy() {
        try { unregisterReceiver(carReceiver) } catch (e: Exception) { }
        quickAsk?.release()
        if (isFinishing) shutdown()
        super.onDestroy()
    }

    /**
     * In modalità guidatore Tachimetro si attiva solo con l'auto collegata.
     * Riconosce da solo autoradio e vivavoce; per gli altri dispositivi chiede una volta e poi ricorda.
     * In modalità passeggero e in prova senza auto non serve.
     */
    private fun evaluateGate() {
        val s = settings
        if (s.mode == "passenger" || !s.requireCar || Hub.testMode.value) {
            activate()
            return
        }
        if (Hub.carGate.value != CarGate.READY) Hub.carGate.value = CarGate.CHECKING
        CarLink.scan(this) { list ->
            if (list == null) {
                Hub.carGate.value = CarGate.NO_PERMISSION
                deactivate()
                return@scan
            }
            val known = list.firstOrNull { isKnownCar(it.address) }
            if (known != null) {
                Hub.carName.value = known.name
                cancelClose()
                activate()
                return@scan
            }
            val car = list.firstOrNull { it.isCar && it.address !in s.notCars }
            if (car != null) {
                Hub.carName.value = car.name
                lifecycleScope.launch { Prefs.addKnownCar(applicationContext, car.address, car.name) }
                cancelClose()
                activate()
                return@scan
            }
            val other = list.firstOrNull { it.address !in s.notCars }
            if (other != null) {
                Hub.askCar.value = other
                Hub.carGate.value = CarGate.ASK
                deactivate()
                if (askedThisSession.add(other.address)) {
                    val q = quickAsk ?: QuickAsk(this).also { quickAsk = it }
                    q.ask("Sei in auto con ${other.name}?") { answer ->
                        if (answer != null && Hub.carGate.value == CarGate.ASK) handleCarAnswer(other, answer)
                    }
                }
                return@scan
            }
            if (Hub.closingIn.value == null) {
                Hub.carGate.value = CarGate.WAITING
                deactivate()
            }
        }
    }

    private fun handleCarAnswer(device: BtDevice, isCar: Boolean) {
        Hub.askCar.value = null
        lifecycleScope.launch {
            if (isCar) Prefs.addKnownCar(applicationContext, device.address, device.name)
            else Prefs.addNotCar(applicationContext, device.address)
        }
        if (isCar) {
            Hub.carName.value = device.name
            activate()
        } else {
            Hub.carGate.value = CarGate.WAITING
        }
    }

    private fun activate() {
        Hub.carGate.value = CarGate.READY
        NotificationReader.rebind(this)
        if (!VoiceService.running) VoiceService.start(this)
        if (settings.dnd && settings.mode != "passenger") Dnd.enable(this)
        // la mappa riparte da sola dopo aver concesso la posizione
        if (BuildConfig.HAS_MAPS_KEY && NavController.navigator == null &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            NavController.init(this)
        }
    }

    /** Spegne voce, posizione, radio e navigazione, ma lascia l'app aperta in attesa dell'auto. */
    private fun deactivate() {
        if (VoiceService.running) stopService(Intent(this, VoiceService::class.java))
        NavController.stop()
        Dnd.restore(this)
    }

    // ---------- scollegamento: un minuto di tolleranza prima di chiudere ----------

    private val tick = object : Runnable {
        override fun run() {
            val left = (Hub.closingIn.value ?: return) - 1
            if (left <= 0) {
                Hub.closingIn.value = null
                closeApp()
            } else {
                Hub.closingIn.value = left
                ui.postDelayed(this, 1000)
            }
        }
    }

    /** L'auto si è scollegata: salva subito il parcheggio e chiude tra un minuto, se non si ricollega. */
    private fun onCarDisconnected() {
        val s = settings
        if (s.mode == "passenger" || !s.requireCar || Hub.testMode.value || Hub.carGate.value != CarGate.READY) return
        if (Hub.closingIn.value != null) return
        val loc = Hub.location.value
        if (s.saveParking && loc != null) lifecycleScope.launch { Car.saveParking(applicationContext, loc) }
        Hub.closingIn.value = 60
        ui.removeCallbacks(tick)
        ui.postDelayed(tick, 1000)
    }

    private fun cancelClose() {
        ui.removeCallbacks(tick)
        Hub.closingIn.value = null
    }

    /** Chiusura completa: nessun servizio, ascolto, GPS o lettura notifiche resta attivo. */
    private fun shutdown() {
        cancelClose()
        stopService(Intent(this, VoiceService::class.java))
        NavController.release()
        Radio.release()
        MediaSessions.stop()
        NotificationReader.unbind()
        Dnd.restore(this)
        Hub.testMode.value = false
        Hub.carGate.value = CarGate.CHECKING
    }

    private fun closeApp() {
        shutdown()
        finishAndRemoveTask()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readScreenExtra(intent)
    }

    private fun readScreenExtra(i: Intent?) {
        val name = i?.getStringExtra(EXTRA_SCREEN) ?: return
        runCatching { Screen.valueOf(name) }.getOrNull()?.let { Hub.screen.value = it }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullScreen(settings.fullScreen)
    }

    /** Nasconde la barra di stato e i tasti di Android; riappaiono per un attimo scorrendo dal bordo. */
    private fun applyFullScreen(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyWindow(s: AppSettings, hot: Boolean) {
        applyFullScreen(s.fullScreen)
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
            UiAction.Close -> closeApp()
            UiAction.StayOpen -> {
                // resti in auto senza Bluetooth: Tachimetro resta attivo finché non lo chiudi
                cancelClose()
                Hub.testMode.value = true
            }
            UiAction.TestWithoutCar -> {
                Hub.testMode.value = true
                Hub.askCar.value = null
                activate()
            }
            is UiAction.CarAnswer -> handleCarAnswer(a.device, a.isCar)
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
    val gate by Hub.carGate.collectAsState()
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
        val insets = if (s.fullScreen) WindowInsets.displayCutout else WindowInsets.safeDrawing
        Box(Modifier.fillMaxSize().background(pal.bg).windowInsetsPadding(insets).offset(dx.dp, dy.dp)) {
            if (!s.setupDone) {
                SetupScreen(s, onAskPermissions)
            } else if (!chosen && s.startMode == "ask") {
                ModeChooser { driver ->
                    chosen = true
                    scope.launch { Prefs.edit(context) { it[Keys.mode] = if (driver) "driver" else "passenger" } }
                    Hub.screen.value = if (driver) Screen.DASHBOARD else Screen.PASSENGER
                }
            } else if (s.mode != "passenger" && s.requireCar && gate != CarGate.READY &&
                screen !in setOf(Screen.SETTINGS, Screen.STATUS, Screen.SETUP)
            ) {
                WaitingForCar(gate)
            } else {
                when (screen) {
                    Screen.DASHBOARD -> if (s.mode == "passenger") PassengerScreen(s, night) else DriverDashboard(s, night)
                    Screen.PASSENGER -> PassengerScreen(s, night)
                    Screen.SETTINGS -> SettingsScreen(s, onAskPermissions)
                    Screen.APP_BAR -> AppBarScreen(s)
                    Screen.PARKING -> ParkingScreen(s)
                    Screen.FUEL -> FuelScreen(s)
                    Screen.SETUP -> SetupScreen(s, onAskPermissions)
                    Screen.STATUS -> StatusScreen(s)
                    Screen.WAKE_TEST -> WakeTestScreen(s)
                }
            }
        }
    }
}

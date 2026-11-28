package it.giacomo.tachi.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import it.giacomo.tachi.BuildConfig
import it.giacomo.tachi.car.CarLink
import it.giacomo.tachi.data.Car
import it.giacomo.tachi.data.Prefs
import it.giacomo.tachi.service.DriveService
import it.giacomo.tachi.session.Phase
import it.giacomo.tachi.session.Session
import kotlinx.coroutines.launch

/** Le schermate raggiungibili oltre a quella principale. */
private enum class Route { HOME, STATUS, SETTINGS, ADD_CAR }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        Prefs.load(this)
        // ogni apertura riparte pulita; se il servizio è già attivo (app riaperta) non lo tocco
        if (savedInstanceState == null && !Session.serviceRunning.value) Session.opened()

        // quando l'app va chiusa del tutto, anche se la schermata non è in primo piano
        lifecycleScope.launch {
            Session.closed.collect { if (it) finishAndRemoveTask() }
        }
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        hideSystemBars()
        startServiceIfReady()
    }

    fun startServiceIfReady() {
        if (Prefs.setupDone.value && CarLink.hasPermission(this) && !Session.serviceRunning.value && !Session.closed.value) {
            DriveService.start(this)
        }
    }

    /** Chiusura completa da pulsante: nulla resta in esecuzione. */
    fun closeApp() {
        Session.requestClose()
        DriveService.stop(this)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun hasNotifications(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(this).areNotificationsEnabled()

    fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    fun openBluetoothSettings() {
        try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (e: Exception) { }
    }

    @Composable
    private fun App() {
        val setupDone by Prefs.setupDone.collectAsState()
        val cars by Prefs.cars.collectAsState()
        val session by Session.state.collectAsState()
        val serviceRunning by Session.serviceRunning.collectAsState()

        // al ritorno dalle impostazioni del telefono ricontrollo permessi e Bluetooth
        var refresh by remember { mutableIntStateOf(0) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        val hasBt = remember(refresh) { CarLink.hasPermission(this) }
        val hasNotif = remember(refresh) { hasNotifications() }
        val btOn = remember(refresh, session) { CarLink.isEnabled(this) }

        val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; startServiceIfReady() }
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
        val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

        fun askBt() {
            if (Prefs.askedBluetooth(this) && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                openAppSettings()
            } else {
                Prefs.setAskedBluetooth(this)
                btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        fun askNotif() {
            val denied = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (!denied || (Prefs.askedNotifications(this) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                // permesso già dato ma notifiche spente, oppure rifiutato per sempre: pagina dell'app
                openAppSettings()
            } else {
                Prefs.setAskedNotifications(this)
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // ---- configurazione guidata (o permesso Bluetooth tolto dopo) ----
        var setupStep by rememberSaveable { mutableIntStateOf(1) }
        if (!setupDone || !hasBt) {
            if (setupStep == 1 || !hasBt) {
                PermissionsScreen(hasBt, hasNotif, ::askBt, ::askNotif) {
                    if (setupDone) startServiceIfReady() else setupStep = 2
                }
                return
            }
            CarPicker(onboarding = true, cars = cars, onDone = {
                Prefs.setSetupDone(this)
                startServiceIfReady()
            }, onBack = { setupStep = 1 })
            BackHandler { setupStep = 1 }
            return
        }

        // ---- app configurata ----
        var route by rememberSaveable { mutableStateOf(Route.HOME) }
        BackHandler {
            if (route != Route.HOME) route = Route.HOME else moveTaskToBack(true)
        }

        when (route) {
            Route.STATUS -> {
                val connected = session.phase == Phase.ACTIVE || session.phase == Phase.LEAVING
                val rows = listOf(
                    StatusRow("Bluetooth", if (btOn) "Acceso" else "Spento: accendilo", btOn),
                    StatusRow("Permesso dispositivi vicini", if (hasBt) "Concesso" else "Mancante", hasBt),
                    StatusRow("Permesso notifiche", if (hasNotif) "Concesso" else "Mancante: tocca Impostazioni", hasNotif),
                    StatusRow("Auto scelta", if (cars.isEmpty()) "Nessuna: aggiungila nelle Impostazioni" else cars.joinToString(", ") { it.name }, cars.isNotEmpty()),
                    StatusRow(
                        "Auto collegata",
                        when (session.phase) {
                            Phase.ACTIVE -> "Sì, ${session.carName}"
                            Phase.LEAVING -> "Scollegata da poco"
                            Phase.TEST -> "No, sei in prova senza auto"
                            else -> "Non ancora: accendi l'auto"
                        },
                        session.phase == Phase.ACTIVE,
                    ),
                    StatusRow(
                        "Notifica «Tachimetro attivo»",
                        when {
                            !serviceRunning -> "Non attiva"
                            !hasNotif -> "Attiva ma nascosta: mancano le notifiche"
                            else -> "Visibile, con il pulsante Chiudi"
                        },
                        serviceRunning && hasNotif,
                    ),
                )
                val problems = rows.filterNot { it.ok }
                val summary = when {
                    problems.isEmpty() -> "Tutto in ordine."
                    problems.size == 1 && problems[0].title == "Auto collegata" && !connected ->
                        "Tutto in ordine tranne il collegamento: Tachimetro si attiva appena l'auto è accesa."
                    else -> "Da sistemare: " + problems.joinToString(", ") { it.title.lowercase() } + "."
                }
                StatusScreen(rows, summary) { route = Route.HOME }
            }
            Route.SETTINGS -> SettingsScreen(
                cars = cars,
                version = BuildConfig.VERSION_NAME,
                onRemove = { Prefs.removeCar(this, it.address) },
                onAdd = { route = Route.ADD_CAR },
                onOpenSystem = ::openAppSettings,
                onBack = { route = Route.HOME },
            )
            Route.ADD_CAR -> CarPicker(onboarding = false, cars = cars, onDone = { route = Route.SETTINGS }, onBack = { route = Route.SETTINGS })
            Route.HOME -> when (session.phase) {
                Phase.ACTIVE, Phase.TEST -> ActiveScreen(
                    carName = session.carName,
                    test = session.phase == Phase.TEST,
                    onSettings = { route = Route.SETTINGS },
                    onStatus = { route = Route.STATUS },
                    onClose = ::closeApp,
                )
                Phase.LEAVING, Phase.CLOSING -> LeavingScreen(session.secondsLeft, onStay = { Session.stayHere() }, onCloseNow = ::closeApp)
                Phase.WAITING -> WaitingScreen(
                    btOn = btOn,
                    carNames = cars.map { it.name },
                    onEnableBt = { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    onAddCar = { route = Route.ADD_CAR },
                    onTest = { Session.startTest() },
                    onSettings = { route = Route.SETTINGS },
                    onStatus = { route = Route.STATUS },
                    onClose = ::closeApp,
                )
            }
        }
    }

    @Composable
    private fun CarPicker(onboarding: Boolean, cars: List<Car>, onDone: () -> Unit, onBack: () -> Unit) {
        var refresh by remember { mutableIntStateOf(0) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        val devices = remember(refresh) { CarLink.paired(this) }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        if (!onboarding) BackHandler(onBack = onBack)
        CarPickerScreen(
            devices = devices,
            already = cars.map { it.address }.toSet(),
            selected = selected,
            onboarding = onboarding,
            onSelect = { selected = it },
            onBack = onBack,
            onSave = {
                devices.firstOrNull { it.address == selected }?.let { d ->
                    Prefs.addCar(this, Car(d.address, d.name))
                    onDone()
                }
            },
            onOpenBluetooth = ::openBluetoothSettings,
        )
    }
}

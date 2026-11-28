package it.giacomo.tachi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import it.giacomo.tachi.R
import it.giacomo.tachi.car.CarLink
import it.giacomo.tachi.data.Prefs
import it.giacomo.tachi.session.Phase
import it.giacomo.tachi.session.Session
import it.giacomo.tachi.session.SessionState
import it.giacomo.tachi.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tiene viva l'app mentre è aperta, con la notifica fissa «Tachimetro attivo».
 * Controlla il collegamento con l'auto e gestisce il conto alla rovescia.
 * Si ferma quando chiudi l'app: nulla resta in esecuzione.
 */
class DriveService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotified: SessionState? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // gli stati del Bluetooth si assestano in qualche secondo: ricontrollo più volte
            scheduleCheck()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (Session.state.value.phase == Phase.LEAVING) {
                Session.tick()
                if (Session.state.value.phase == Phase.LEAVING) main.postDelayed(this, 1000)
            }
        }
    }

    private val periodic = object : Runnable {
        override fun run() {
            check()
            main.postDelayed(this, if (Session.state.value.phase == Phase.LEAVING) 5_000 else 15_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.load(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(Session.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        Session.serviceRunning.value = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        CarLink.openProfiles(this) { main.post { check() } }

        scope.launch {
            Session.state.collect { s -> onState(s) }
        }
        scope.launch {
            Prefs.cars.collect { check() }
        }
        main.post(periodic)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> closeEverything()
            ACTION_STAY -> Session.stayHere()
            else -> check()
        }
        return START_NOT_STICKY
    }

    private fun scheduleCheck() {
        for (delay in listOf(300L, 1_500L, 4_000L, 8_000L)) main.postDelayed({ check() }, delay)
    }

    /** Guarda se una delle tue auto è collegata e aggiorna lo stato. */
    private fun check() {
        val car = CarLink.connectedCar(this, Prefs.cars.value)
        if (car != null) Session.carConnected(car.name) else Session.noCar()
    }

    private fun onState(s: SessionState) {
        when (s.phase) {
            Phase.LEAVING -> {
                if (lastNotified?.phase != Phase.LEAVING) {
                    main.removeCallbacks(tick)
                    main.postDelayed(tick, 1000)
                }
            }
            Phase.CLOSING -> {
                closeEverything()
                return
            }
            else -> main.removeCallbacks(tick)
        }
        val n = getSystemService(NotificationManager::class.java)
        n.notify(NOTIFICATION_ID, buildNotification(s))
        lastNotified = s
    }

    /** Chiusura completa: la schermata si chiude, il servizio si ferma, la notifica sparisce. */
    private fun closeEverything() {
        Session.requestClose()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // tolta dalle app recenti: si chiude tutto
        closeEverything()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        CarLink.closeProfiles(this)
        Session.serviceRunning.value = false
        Session.reset()
        super.onDestroy()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Tachimetro attivo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Visibile solo mentre Tachimetro è aperto"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(s: SessionState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val close = PendingIntent.getService(
            this, 1, Intent(this, DriveService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (s.phase) {
            Phase.ACTIVE -> "Collegato a ${s.carName}"
            Phase.TEST -> "Prova senza auto"
            Phase.LEAVING -> "Auto scollegata: si chiude tra ${s.secondsLeft} secondi"
            else -> "In attesa della tua auto"
        }
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notifica)
            .setContentTitle("Tachimetro attivo")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.phase == Phase.LEAVING) {
            val stay = PendingIntent.getService(
                this, 2, Intent(this, DriveService::class.java).setAction(ACTION_STAY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Resto qui", stay).build())
        }
        b.addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Chiudi", close).build())
        return b.build()
    }

    companion object {
        private const val CHANNEL = "attivo"
        private const val NOTIFICATION_ID = 1
        const val ACTION_CLOSE = "it.giacomo.tachi.CLOSE"
        const val ACTION_STAY = "it.giacomo.tachi.STAY"

        fun start(context: Context) {
            if (!CarLink.hasPermission(context) || Session.closed.value) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, DriveService::class.java))
            } catch (e: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DriveService::class.java))
        }
    }
}

package it.giacomo.tachimetro.car

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.provider.Settings
import androidx.core.app.NotificationCompat
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.MainActivity
import it.giacomo.tachimetro.R
import it.giacomo.tachimetro.Screen
import it.giacomo.tachimetro.TachiApp
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val carScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Si accorge quando il telefono si collega o si scollega dal Bluetooth dell'auto scelta nelle impostazioni. */
class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val address = try { device.address } catch (e: SecurityException) { return }
        val pending = goAsync()
        carScope.launch {
            try {
                val s = Prefs.get(context)
                if (s.carBtAddress.isBlank() || !s.carBtAddress.equals(address, true)) return@launch
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (s.autoStart) Car.openApp(context)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (s.saveParking) {
                        Hub.location.value?.let { Car.saveParking(context, it) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ParkingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Car.notify(context, 2, "Strisce blu", "Il parcheggio scade tra 10 minuti", Screen.PARKING)
    }
}

object Car {
    fun openApp(context: Context) {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (Settings.canDrawOverlays(context)) {
            context.startActivity(i)
        } else {
            notify(context, 1, "Sei in auto", "Tocca per aprire Tachimetro", Screen.DASHBOARD)
        }
    }

    suspend fun saveParking(context: Context, loc: Location) {
        Prefs.edit(context) {
            it[Keys.parkingLat] = loc.latitude
            it[Keys.parkingLng] = loc.longitude
            it[Keys.parkingTime] = System.currentTimeMillis()
            it[Keys.parkingReminderAt] = 0L
        }
        notify(context, 3, "Parcheggio salvato", "Di' «Ehi Tachi, dov'è la macchina?» per ritrovarla", Screen.PARKING)
    }

    fun setReminder(context: Context, minutes: Int): Long {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 10, Intent(context, ParkingReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - 10 * 60_000L, pi)
        carScope.launch { Prefs.edit(context) { it[Keys.parkingReminderAt] = at } }
        return at
    }

    fun cancelReminder(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 10, Intent(context, ParkingReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        carScope.launch { Prefs.edit(context) { it[Keys.parkingReminderAt] = 0L } }
    }

    fun notify(context: Context, id: Int, title: String, text: String, screen: Screen) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(MainActivity.EXTRA_SCREEN, screen.name)
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, TachiApp.CH_CAR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

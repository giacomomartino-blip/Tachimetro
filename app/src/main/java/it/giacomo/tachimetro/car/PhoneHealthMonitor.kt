package it.giacomo.tachimetro.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.PhoneHealth

/** Controlla carica e temperatura: lo schermo sempre acceso, il GPS e l'ascolto consumano molto. */
class PhoneHealthMonitor(private val context: Context) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = update(i)
    }
    private var thermal = PowerManager.THERMAL_STATUS_NONE
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermal = status
        Hub.health.value = Hub.health.value.copy(hot = isHot(Hub.health.value.batteryTempC))
    }

    fun start() {
        val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        sticky?.let { update(it) }
        context.getSystemService(PowerManager::class.java)?.addThermalStatusListener(thermalListener)
    }

    fun stop() {
        try { context.unregisterReceiver(receiver) } catch (e: Exception) { }
        context.getSystemService(PowerManager::class.java)?.removeThermalStatusListener(thermalListener)
    }

    private fun isHot(tempC: Double) = tempC >= 42.0 || thermal >= PowerManager.THERMAL_STATUS_MODERATE

    private fun update(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        Hub.health.value = PhoneHealth(
            batteryPct = pct,
            charging = plugged,
            batteryTempC = temp,
            hot = isHot(temp),
            lowBattery = !plugged && pct in 0..20,
        )
    }
}

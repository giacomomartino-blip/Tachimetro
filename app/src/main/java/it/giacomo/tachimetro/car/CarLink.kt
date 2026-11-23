package it.giacomo.tachimetro.car

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/** Controlla se il telefono è collegato adesso al Bluetooth dell'auto scelta nelle impostazioni. */
object CarLink {
    fun check(context: Context, address: String, onResult: (Boolean?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (address.isBlank()) {
            main.post { onResult(false) }
            return
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            main.post { onResult(null) }
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            main.post { onResult(false) }
            return
        }
        val profiles = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
        var pending = profiles.size
        var found = false
        var done = false
        fun finishOne() {
            pending--
            if (pending <= 0 && !done) {
                done = true
                main.post { onResult(found) }
            }
        }
        for (p in profiles) {
            val ok = adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        if (proxy.connectedDevices.any { it.address.equals(address, true) }) found = true
                    } catch (e: SecurityException) {
                    }
                    try { adapter.closeProfileProxy(profile, proxy) } catch (e: Exception) { }
                    main.post { finishOne() }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, p)
            if (!ok) main.post { finishOne() }
        }
        // sicurezza: se il sistema non risponde entro 3 secondi
        main.postDelayed({
            if (!done) {
                done = true
                onResult(found)
            }
        }, 3000)
    }
}

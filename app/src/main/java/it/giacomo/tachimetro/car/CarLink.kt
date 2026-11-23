package it.giacomo.tachimetro.car

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import it.giacomo.tachimetro.BtDevice

/**
 * Elenca i dispositivi Bluetooth audio collegati in questo momento (vivavoce e musica)
 * e indica quali si dichiarano autoradio o vivavoce da auto.
 */
object CarLink {
    private fun isCarClass(cls: Int?): Boolean = cls == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
        cls == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE

    /** onResult riceve null se manca il permesso Bluetooth. */
    fun scan(context: Context, onResult: (List<BtDevice>?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            main.post { onResult(null) }
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            main.post { onResult(emptyList()) }
            return
        }
        val found = LinkedHashMap<String, BtDevice>()
        val profiles = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
        var pending = profiles.size
        var done = false
        fun deliver() {
            if (!done) {
                done = true
                onResult(found.values.toList())
            }
        }
        fun finishOne() {
            pending--
            if (pending <= 0) deliver()
        }
        for (p in profiles) {
            val ok = adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        for (d in proxy.connectedDevices) {
                            val addr = d.address
                            val name = d.name ?: addr
                            val car = isCarClass(d.bluetoothClass?.deviceClass)
                            val prev = found[addr]
                            found[addr] = BtDevice(addr, name, car || (prev?.isCar == true))
                        }
                    } catch (e: SecurityException) {
                    }
                    try { adapter.closeProfileProxy(profile, proxy) } catch (e: Exception) { }
                    main.post { finishOne() }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, p)
            if (!ok) main.post { finishOne() }
        }
        main.postDelayed({ deliver() }, 3000)
    }
}

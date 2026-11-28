package it.giacomo.tachi.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import it.giacomo.tachi.data.Car

/** Tipo di dispositivo, solo per l'icona e l'ordine nell'elenco. */
enum class DeviceKind { CAR, HEADPHONES, OTHER }

data class Paired(val address: String, val name: String, val kind: DeviceKind)

/** Tutto ciò che riguarda il Bluetooth del telefono. */
@SuppressLint("MissingPermission")
object CarLink {
    private var proxies = mutableMapOf<Int, BluetoothProfile>()

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isEnabled(context: Context): Boolean = try {
        adapter(context)?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** Dispositivi già associati al telefono, prima quelli che sembrano autoradio. */
    fun paired(context: Context): List<Paired> {
        if (!hasPermission(context)) return emptyList()
        return try {
            adapter(context)?.bondedDevices.orEmpty().map { d ->
                Paired(d.address, d.name?.takeIf { it.isNotBlank() } ?: d.address, kindOf(d))
            }.sortedWith(compareBy<Paired>({ it.kind.ordinal }, { it.name.lowercase() }))
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun kindOf(d: BluetoothDevice): DeviceKind {
        val c = try { d.bluetoothClass } catch (e: SecurityException) { null } ?: return DeviceKind.OTHER
        return when (c.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO, BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> DeviceKind.CAR
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> DeviceKind.HEADPHONES
            else -> DeviceKind.OTHER
        }
    }

    /** Apre i collegamenti ai profili audio e chiamate: servono per sapere chi è collegato. */
    fun openProfiles(context: Context, onReady: () -> Unit) {
        if (!hasPermission(context)) return
        val a = adapter(context) ?: return
        for (p in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            try {
                a.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        proxies[profile] = proxy
                        onReady()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxies.remove(profile)
                    }
                }, p)
            } catch (e: Exception) {
            }
        }
    }

    fun closeProfiles(context: Context) {
        val a = adapter(context)
        for ((p, proxy) in proxies) {
            try { a?.closeProfileProxy(p, proxy) } catch (e: Exception) { }
        }
        proxies.clear()
    }

    /** La prima delle tue auto che risulta collegata in questo momento, oppure null. */
    fun connectedCar(context: Context, cars: List<Car>): Car? {
        if (cars.isEmpty() || !hasPermission(context) || !isEnabled(context)) return null
        val viaProfiles = try {
            proxies.values.flatMap { it.connectedDevices }.map { it.address }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val a = adapter(context) ?: return null
        return cars.firstOrNull { car ->
            car.address in viaProfiles || try {
                isLinked(a.getRemoteDevice(car.address))
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Collegamento Bluetooth di base attivo: Android non lo espone pubblicamente, ma Samsung lo fornisce. */
    private fun isLinked(d: BluetoothDevice): Boolean = try {
        d.javaClass.getMethod("isConnected").invoke(d) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }
}

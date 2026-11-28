package it.giacomo.tachi.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un'auto scelta da te: indirizzo Bluetooth e nome come lo vede il telefono. */
data class Car(val address: String, val name: String)

/** Impostazioni salvate solo sul telefono. */
object Prefs {
    private const val FILE = "tachimetro"
    private const val KEY_SETUP = "setupDone"
    private const val KEY_CARS = "cars"
    private const val KEY_ASKED_BT = "askedBt"
    private const val KEY_ASKED_NOTIF = "askedNotif"

    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    val cars: StateFlow<List<Car>> = _cars.asStateFlow()

    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        _setupDone.value = p.getBoolean(KEY_SETUP, false)
        _cars.value = decode(p.getStringSet(KEY_CARS, emptySet()) ?: emptySet())
        loaded = true
    }

    fun setSetupDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_SETUP, true).apply()
        _setupDone.value = true
    }

    fun addCar(context: Context, car: Car) {
        val list = _cars.value.filterNot { it.address == car.address } + car
        save(context, list)
    }

    fun removeCar(context: Context, address: String) {
        save(context, _cars.value.filterNot { it.address == address })
    }

    /** Se già chiesto una volta, un nuovo rifiuto di Android vuol dire «non chiedere più»: si apre la pagina dell'app. */
    fun askedBluetooth(context: Context) = prefs(context).getBoolean(KEY_ASKED_BT, false)
    fun setAskedBluetooth(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED_BT, true).apply()
    fun askedNotifications(context: Context) = prefs(context).getBoolean(KEY_ASKED_NOTIF, false)
    fun setAskedNotifications(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED_NOTIF, true).apply()

    private fun save(context: Context, list: List<Car>) {
        prefs(context).edit().putStringSet(KEY_CARS, list.map { it.address + "|" + it.name }.toSet()).apply()
        _cars.value = list
    }

    private fun decode(set: Set<String>): List<Car> = set.mapNotNull {
        val i = it.indexOf('|')
        if (i <= 0) null else Car(it.substring(0, i), it.substring(i + 1))
    }.sortedBy { it.name.lowercase() }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

package it.giacomo.tachimetro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "impostazioni")

/** Voce della barra: "fn:home", "fn:phone", "fn:msg", "fn:radio", "fn:music", oppure "app:<pacchetto>". */
val DEFAULT_BAR = listOf("fn:home", "fn:phone", "fn:msg", "fn:music", "fn:radio", "app:com.waze")
val DEFAULT_BAR_PASSENGER = listOf("fn:home", "fn:music", "fn:radio")

data class AppSettings(
    val themeMode: String = "auto",          // auto, day, night
    val startMode: String = "ask",           // ask, driver, passenger
    val mode: String = "driver",             // modalità attuale
    val modeChosen: Boolean = false,
    val readMessages: Boolean = true,
    val geminiKey: String = "",
    val home: String = "",
    val work: String = "",
    val fuelType: String = "Benzina",
    val speedAlert: String = "tone",         // tone, visual, none
    val tolerance: Int = 5,
    val cameras: Boolean = true,
    val mobileCameras: Boolean = true,
    val cameraWarnMeters: Int = 400,
    val musicApp: String = "spotify",        // spotify, ytmusic, amazon
    val radioRegion: String = "",
    val bar: List<String> = DEFAULT_BAR,
    val barPassenger: List<String> = DEFAULT_BAR_PASSENGER,
    val tabletConnection: String = "auto",   // auto, sim, hotspot
    val weatherOn: Boolean = true,
    val autoStart: Boolean = false,
    val keepScreenOn: Boolean = true,
    val fullScreen: Boolean = true,
    val requireCar: Boolean = true,
    val burnIn: Boolean = true,
    val saveParking: Boolean = true,
    val healthAlerts: Boolean = true,
    val carBtAddress: String = "",
    val carBtName: String = "",
    val split: Float = 0.6f,
    val parkingLat: Double = 0.0,
    val parkingLng: Double = 0.0,
    val parkingTime: Long = 0L,
    val parkingReminderAt: Long = 0L,
)

object Keys {
    val themeMode = stringPreferencesKey("themeMode")
    val startMode = stringPreferencesKey("startMode")
    val mode = stringPreferencesKey("mode")
    val modeChosen = booleanPreferencesKey("modeChosen")
    val readMessages = booleanPreferencesKey("readMessages")
    val geminiKey = stringPreferencesKey("geminiKey")
    val home = stringPreferencesKey("home")
    val work = stringPreferencesKey("work")
    val fuelType = stringPreferencesKey("fuelType")
    val speedAlert = stringPreferencesKey("speedAlert")
    val tolerance = intPreferencesKey("tolerance")
    val cameras = booleanPreferencesKey("cameras")
    val mobileCameras = booleanPreferencesKey("mobileCameras")
    val cameraWarnMeters = intPreferencesKey("cameraWarnMeters")
    val musicApp = stringPreferencesKey("musicApp")
    val radioRegion = stringPreferencesKey("radioRegion")
    val bar = stringPreferencesKey("bar")
    val barPassenger = stringPreferencesKey("barPassenger")
    val tabletConnection = stringPreferencesKey("tabletConnection")
    val weatherOn = booleanPreferencesKey("weatherOn")
    val autoStart = booleanPreferencesKey("autoStart")
    val keepScreenOn = booleanPreferencesKey("keepScreenOn")
    val fullScreen = booleanPreferencesKey("fullScreen")
    val requireCar = booleanPreferencesKey("requireCar")
    val burnIn = booleanPreferencesKey("burnIn")
    val saveParking = booleanPreferencesKey("saveParking")
    val healthAlerts = booleanPreferencesKey("healthAlerts")
    val carBtAddress = stringPreferencesKey("carBtAddress")
    val carBtName = stringPreferencesKey("carBtName")
    val split = floatPreferencesKey("split")
    val parkingLat = doublePreferencesKey("parkingLat")
    val parkingLng = doublePreferencesKey("parkingLng")
    val parkingTime = longPreferencesKey("parkingTime")
    val parkingReminderAt = longPreferencesKey("parkingReminderAt")
}

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        themeMode = this[Keys.themeMode] ?: d.themeMode,
        startMode = this[Keys.startMode] ?: d.startMode,
        mode = this[Keys.mode] ?: d.mode,
        modeChosen = this[Keys.modeChosen] ?: d.modeChosen,
        readMessages = this[Keys.readMessages] ?: d.readMessages,
        geminiKey = this[Keys.geminiKey] ?: d.geminiKey,
        home = this[Keys.home] ?: d.home,
        work = this[Keys.work] ?: d.work,
        fuelType = this[Keys.fuelType] ?: d.fuelType,
        speedAlert = this[Keys.speedAlert] ?: d.speedAlert,
        tolerance = this[Keys.tolerance] ?: d.tolerance,
        cameras = this[Keys.cameras] ?: d.cameras,
        mobileCameras = this[Keys.mobileCameras] ?: d.mobileCameras,
        cameraWarnMeters = this[Keys.cameraWarnMeters] ?: d.cameraWarnMeters,
        musicApp = this[Keys.musicApp] ?: d.musicApp,
        radioRegion = this[Keys.radioRegion] ?: d.radioRegion,
        bar = this[Keys.bar]?.split("\n")?.filter { it.isNotBlank() } ?: d.bar,
        barPassenger = this[Keys.barPassenger]?.split("\n")?.filter { it.isNotBlank() } ?: d.barPassenger,
        tabletConnection = this[Keys.tabletConnection] ?: d.tabletConnection,
        weatherOn = this[Keys.weatherOn] ?: d.weatherOn,
        autoStart = this[Keys.autoStart] ?: d.autoStart,
        keepScreenOn = this[Keys.keepScreenOn] ?: d.keepScreenOn,
        fullScreen = this[Keys.fullScreen] ?: d.fullScreen,
        requireCar = this[Keys.requireCar] ?: d.requireCar,
        burnIn = this[Keys.burnIn] ?: d.burnIn,
        saveParking = this[Keys.saveParking] ?: d.saveParking,
        healthAlerts = this[Keys.healthAlerts] ?: d.healthAlerts,
        carBtAddress = this[Keys.carBtAddress] ?: d.carBtAddress,
        carBtName = this[Keys.carBtName] ?: d.carBtName,
        split = this[Keys.split] ?: d.split,
        parkingLat = this[Keys.parkingLat] ?: d.parkingLat,
        parkingLng = this[Keys.parkingLng] ?: d.parkingLng,
        parkingTime = this[Keys.parkingTime] ?: d.parkingTime,
        parkingReminderAt = this[Keys.parkingReminderAt] ?: d.parkingReminderAt,
    )
}

object Prefs {
    fun flow(context: Context): Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun get(context: Context): AppSettings = flow(context).first()

    suspend fun edit(context: Context, block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setBar(context: Context, passenger: Boolean, items: List<String>) {
        edit(context) { it[if (passenger) Keys.barPassenger else Keys.bar] = items.joinToString("\n") }
    }

    /** Esporta indirizzi, barra e impostazioni in un testo JSON. La chiave di Gemini non viene esportata. */
    suspend fun exportJson(context: Context): String {
        val prefs = context.dataStore.data.first()
        val o = JSONObject()
        prefs.asMap().forEach { (k, v) -> if (k.name != Keys.geminiKey.name) o.put(k.name, v) }
        return o.toString(2)
    }

    suspend fun importJson(context: Context, text: String) {
        val o = JSONObject(text)
        context.dataStore.edit { p ->
            o.keys().forEach { name ->
                when (val v = o.get(name)) {
                    is Boolean -> p[booleanPreferencesKey(name)] = v
                    is Int -> p[intPreferencesKey(name)] = v
                    is Long -> p[longPreferencesKey(name)] = v
                    is Double -> if (name in listOf("parkingLat", "parkingLng")) p[doublePreferencesKey(name)] = v else p[floatPreferencesKey(name)] = v.toFloat()
                    is String -> p[stringPreferencesKey(name)] = v
                }
            }
        }
    }
}

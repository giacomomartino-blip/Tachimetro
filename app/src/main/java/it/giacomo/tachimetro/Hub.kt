package it.giacomo.tachimetro

import android.app.PendingIntent
import android.app.RemoteInput
import android.location.Location
import it.giacomo.tachimetro.data.FuelStation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

enum class Screen { DASHBOARD, PASSENGER, SETTINGS, APP_BAR, PARKING, FUEL, SETUP, STATUS, WAKE_TEST }

/** Stato del collegamento con l'auto: in modalità guidatore Tachimetro si attiva solo con l'auto collegata. */
enum class CarGate { CHECKING, READY, WAITING, ASK, NOT_SET, NO_PERMISSION }

data class BtDevice(val address: String, val name: String, val isCar: Boolean)

data class IncomingCall(val key: String, val caller: String, val time: Long)

data class WeatherNow(val tempC: Double, val description: String, val code: Int)

data class NowPlaying(
    val title: String,
    val artist: String,
    val sourceLabel: String,
    val packageName: String,
    val isPlaying: Boolean,
)

data class RadioState(val name: String, val isPlaying: Boolean, val error: String? = null)

/** Messaggio arrivato da WhatsApp, SMS o Teams. Resta solo in memoria, non viene mai salvato. */
data class IncomingMessage(
    val key: String,
    val app: String,
    val sender: String,
    val group: String?,
    val text: String,
    val time: Long,
    val replyIntent: PendingIntent?,
    val remoteInput: RemoteInput?,
)

enum class VoicePhase { OFF, LOADING, WAITING_WAKE, LISTENING, THINKING, SPEAKING }

data class VoiceUi(
    val phase: VoicePhase = VoicePhase.OFF,
    val transcript: String = "",
    val answer: String = "",
    val error: String? = null,
)

data class NavUi(
    val active: Boolean = false,
    val destination: String = "",
    val etaText: String = "",
    val remainingText: String = "",
    val viaWaze: Boolean = false,
)

data class CameraAlert(val distanceM: Int, val label: String, val maxSpeed: Int?)

data class PhoneHealth(
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val batteryTempC: Double = 0.0,
    val hot: Boolean = false,
    val lowBattery: Boolean = false,
)

sealed interface UiAction {
    data class NavigateTo(val query: String, val waze: Boolean) : UiAction
    data class NavigateCoords(val lat: Double, val lng: Double, val title: String, val waze: Boolean) : UiAction
    data object StopNavigation : UiAction
    data object BackToMaps : UiAction
    data class Show(val screen: Screen) : UiAction
    data class SetMode(val driver: Boolean) : UiAction
    data object Close : UiAction
    data object StayOpen : UiAction
    data object TestWithoutCar : UiAction
    data class CarAnswer(val device: BtDevice, val isCar: Boolean) : UiAction
}

/** Punto di incontro tra il servizio vocale, i lettori di notifiche e l'interfaccia. */
object Hub {
    val location = MutableStateFlow<Location?>(null)
    val weather = MutableStateFlow<WeatherNow?>(null)
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val radio = MutableStateFlow<RadioState?>(null)
    val messages = MutableStateFlow<List<IncomingMessage>>(emptyList())
    val voice = MutableStateFlow(VoiceUi())
    val screen = MutableStateFlow(Screen.DASHBOARD)
    val nav = MutableStateFlow(NavUi())
    val camera = MutableStateFlow<CameraAlert?>(null)
    val health = MutableStateFlow(PhoneHealth())
    val mapsStatus = MutableStateFlow<String?>(null)
    val speeding = MutableStateFlow(false)
    val carGate = MutableStateFlow(CarGate.CHECKING)
    val carName = MutableStateFlow("")
    val askCar = MutableStateFlow<BtDevice?>(null)
    val testMode = MutableStateFlow(false)
    val closingIn = MutableStateFlow<Int?>(null)
    val wakeHeard = MutableStateFlow("")
    val wakeDetectedAt = MutableStateFlow(0L)
    val incomingCall = MutableStateFlow<IncomingCall?>(null)
    val fuel = MutableStateFlow<List<FuelStation>>(emptyList())
    val actions = MutableSharedFlow<UiAction>(extraBufferCapacity = 16)

    fun send(action: UiAction) {
        actions.tryEmit(action)
    }

    fun addMessage(m: IncomingMessage) {
        val others = messages.value.filterNot { it.key == m.key }
        messages.value = (others + m).takeLast(20)
    }

    fun removeMessage(key: String) {
        messages.value = messages.value.filterNot { it.key == key }
    }
}

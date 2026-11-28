package it.giacomo.tachi.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In che situazione si trova l'app mentre è aperta. */
enum class Phase {
    /** Aperta, nessuna auto collegata. */
    WAITING,

    /** Collegata a una delle tue auto. */
    ACTIVE,

    /** «Prova senza auto»: attiva anche senza Bluetooth, fino alla chiusura. */
    TEST,

    /** L'auto si è scollegata: conto alla rovescia prima della chiusura. */
    LEAVING,

    /** Da chiudere: il conto alla rovescia è finito. */
    CLOSING,
}

data class SessionState(
    val phase: Phase = Phase.WAITING,
    val carName: String? = null,
    val secondsLeft: Int = 0,
)

/**
 * Le regole del modulo Base, senza nulla di Android: così si provano con test automatici.
 * Ogni funzione riceve lo stato attuale e restituisce quello nuovo.
 */
object SessionRules {
    const val COUNTDOWN_SECONDS = 60

    /** Una delle tue auto risulta collegata. */
    fun carConnected(s: SessionState, name: String): SessionState =
        when (s.phase) {
            Phase.CLOSING -> s
            else -> SessionState(Phase.ACTIVE, name, 0)
        }

    /** Nessuna delle tue auto risulta collegata. */
    fun noCar(s: SessionState): SessionState =
        when (s.phase) {
            Phase.ACTIVE -> SessionState(Phase.LEAVING, s.carName, COUNTDOWN_SECONDS)
            else -> s
        }

    /** È passato un secondo. */
    fun tick(s: SessionState): SessionState =
        when {
            s.phase != Phase.LEAVING -> s
            s.secondsLeft <= 1 -> SessionState(Phase.CLOSING, s.carName, 0)
            else -> s.copy(secondsLeft = s.secondsLeft - 1)
        }

    /** «Resto qui»: niente chiusura, l'app resta aperta e aspetta l'auto. */
    fun stayHere(s: SessionState): SessionState =
        if (s.phase == Phase.LEAVING) SessionState(Phase.WAITING, null, 0) else s

    /** «Prova senza auto». */
    fun startTest(s: SessionState): SessionState =
        when (s.phase) {
            Phase.WAITING -> SessionState(Phase.TEST, null, 0)
            else -> s
        }
}

/** Lo stato condiviso tra schermate e servizio. Si usa solo dal thread principale. */
object Session {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Diventa vero quando l'app va chiusa del tutto: la schermata lo ascolta e si chiude. */
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    /** Vero mentre il servizio con la notifica fissa è in esecuzione. */
    val serviceRunning = MutableStateFlow(false)

    fun reset() {
        _state.value = SessionState()
    }

    fun carConnected(name: String) = update { SessionRules.carConnected(it, name) }
    fun noCar() = update { SessionRules.noCar(it) }
    fun tick() = update { SessionRules.tick(it) }
    fun stayHere() = update { SessionRules.stayHere(it) }
    fun startTest() = update { SessionRules.startTest(it) }

    fun requestClose() {
        _closed.value = true
    }

    /** Nuova apertura dell'app. */
    fun opened() {
        _closed.value = false
        _state.value = SessionState()
    }

    private inline fun update(f: (SessionState) -> SessionState) {
        _state.value = f(_state.value)
    }
}

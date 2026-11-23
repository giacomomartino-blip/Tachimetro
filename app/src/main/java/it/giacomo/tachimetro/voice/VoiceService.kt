package it.giacomo.tachimetro.voice

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import it.giacomo.tachimetro.CameraAlert
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.IncomingCall
import it.giacomo.tachimetro.IncomingMessage
import it.giacomo.tachimetro.MainActivity
import it.giacomo.tachimetro.R
import it.giacomo.tachimetro.Screen
import it.giacomo.tachimetro.TachiApp
import it.giacomo.tachimetro.UiAction
import it.giacomo.tachimetro.VoicePhase
import it.giacomo.tachimetro.VoiceUi
import it.giacomo.tachimetro.car.Dnd
import it.giacomo.tachimetro.car.PhoneHealthMonitor
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Gemini
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.data.SpeedCameras
import it.giacomo.tachimetro.data.Weather
import it.giacomo.tachimetro.media.Apps
import it.giacomo.tachimetro.media.MediaSessions
import it.giacomo.tachimetro.media.Radio
import it.giacomo.tachimetro.notify.NotificationReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import android.speech.RecognitionListener as AndroidRecognitionListener
import org.vosk.android.RecognitionListener as VoskListener

/**
 * Servizio sempre attivo mentre usi Tachimetro: ascolta «Ehi Tachi» sul telefono (senza internet),
 * capisce il comando, risponde a voce e tiene d'occhio posizione, autovelox, meteo e messaggi.
 */
class VoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var speaker: Speaker
    private lateinit var health: PhoneHealthMonitor
    private var settings = AppSettings()

    private var model: Model? = null
    private var wake: SpeechService? = null
    private var free: SpeechService? = null
    private var recognizer: SpeechRecognizer? = null
    private var listeningCommand = false
    private var pausedUntil = 0L

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastWeatherAt = 0L
    private var lastWeatherPlace: Location? = null
    private var alertsMuted = false
    private val announced = HashSet<String>()

    /** Conversazione guidata sui messaggi: leggo? rispondo? cosa? invio? */
    private sealed interface Dialog {
        data class AskRead(val msg: IncomingMessage) : Dialog
        data class AskReply(val msg: IncomingMessage) : Dialog
        data class ReplyText(val msg: IncomingMessage) : Dialog
        data class ReplyConfirm(val msg: IncomingMessage, val text: String) : Dialog
        data class AskAnswer(val call: IncomingCall) : Dialog
    }

    private var dialog: Dialog? = null
    private var dialogRetry = 0
    /** Coda dei messaggi da annunciare; il secondo valore indica «dimmi solo chi ha scritto». */
    private val pendingMessages = ArrayDeque<Pair<IncomingMessage, Boolean>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground()
        speaker = Speaker(this)
        health = PhoneHealthMonitor(this).also { it.start() }
        MediaSessions.start(applicationContext)
        scope.launch { Prefs.flow(applicationContext).collect { settings = it } }
        scope.launch { Hub.messages.collect { onMessages(it) } }
        scope.launch { Hub.incomingCall.collect { onIncomingCall(it) } }
        startLocation()
        loadModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> main.post { onWake() }
            ACTION_SAY -> intent.getStringExtra(EXTRA_TEXT)?.let { say(it) }
        }
        // se Android chiude il servizio non lo fa ripartire da solo
        return START_NOT_STICKY
    }

    /** L'app è stata chiusa dall'elenco delle app recenti: si spegne tutto. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Dnd.restore(this)
        NotificationReader.unbind()
        MediaSessions.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        stopWake()
        stopFree()
        recognizer?.destroy()
        recognizer = null
        try { fused.removeLocationUpdates(locationCallback) } catch (e: Exception) { }
        health.stop()
        speaker.shutdown()
        Radio.release()
        model?.close()
        scope.cancel()
        Hub.voice.value = VoiceUi(VoicePhase.OFF)
        super.onDestroy()
    }

    // ---------------- primo piano ----------------

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, TachiApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tachimetro è attivo")
            .setContentText("Di' «Ehi Tachi» per dare un comando")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (granted(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        ServiceCompat.startForeground(this, 7, n, types)
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    // ---------------- parola chiave ----------------

    private fun loadModel() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            Hub.voice.value = VoiceUi(VoicePhase.OFF, error = "Serve il permesso del microfono")
            return
        }
        Hub.voice.value = VoiceUi(VoicePhase.LOADING)
        StorageService.unpack(
            this, "model-it", "model",
            { m ->
                model = m
                startWake()
            },
            { e ->
                Hub.voice.value = VoiceUi(VoicePhase.OFF, error = "Modello vocale non disponibile: ${e.message}")
            },
        )
    }

    private fun inCall(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return false
        return am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun startWake() {
        val m = model ?: return
        if (wake != null || listeningCommand || speaker.speaking) return
        val wait = pausedUntil - System.currentTimeMillis()
        if (wait > 0 || inCall()) {
            main.postDelayed({ startWake() }, if (wait > 0) wait else 5000L)
            return
        }
        try {
            val grammar = when (settings.wakeSensitivity) {
                "low" -> WAKE_GRAMMAR_LOW
                "high" -> WAKE_GRAMMAR_HIGH
                else -> WAKE_GRAMMAR
            }
            val rec = Recognizer(m, 16000f, grammar)
            wake = SpeechService(rec, 16000f).also { it.startListening(wakeListener) }
            Hub.voice.value = VoiceUi(VoicePhase.WAITING_WAKE)
        } catch (e: Exception) {
            Hub.voice.value = VoiceUi(VoicePhase.OFF, error = "Microfono occupato")
            main.postDelayed({ startWake() }, 8000L)
        }
    }

    private fun stopWake() {
        wake?.let {
            it.stop()
            it.shutdown()
        }
        wake = null
    }

    private val wakeListener = object : VoskListener {
        override fun onPartialResult(hypothesis: String?) = check(hypothesis, "partial")
        override fun onResult(hypothesis: String?) = check(hypothesis, "text")
        override fun onFinalResult(hypothesis: String?) = check(hypothesis, "text")
        override fun onError(exception: Exception?) {
            stopWake()
            main.postDelayed({ startWake() }, 3000L)
        }

        override fun onTimeout() {
            stopWake()
            startWake()
        }

        private fun check(json: String?, field: String) {
            val text = try { JSONObject(json ?: return).optString(field) } catch (e: Exception) { return }
            if (text.isNotBlank()) Hub.wakeHeard.value = text
            if (isWake(text, field == "partial")) main.post {
                Hub.wakeDetectedAt.value = System.currentTimeMillis()
                onWake()
            }
        }
    }

    /** Sensibilità: bassa vuole «ehi» + parola sulla frase finita, alta accetta anche varianti più lontane. */
    private fun isWake(text: String, partial: Boolean): Boolean {
        val t = text.lowercase()
        return when (settings.wakeSensitivity) {
            "low" -> !partial && Regex("(ehi|hey|hei) (tachi|tacchi|taxi|ta chi)").containsMatchIn(t)
            "high" -> (WAKE_WORDS + listOf("taki", "tacci", "tachy", "tazzi", "dacchi")).any { t.contains(it) }
            else -> WAKE_WORDS.any { t.contains(it) }
        }
    }

    private fun onWake() {
        if (listeningCommand) return
        stopWake()
        speaker.stop()
        speaker.beep(45)
        main.postDelayed({ listenCommand() }, 250)
    }

    // ---------------- comando ----------------

    private fun listenCommand() {
        listeningCommand = true
        Hub.voice.value = VoiceUi(VoicePhase.LISTENING)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
            sr.setRecognitionListener(androidListener)
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            sr.startListening(i)
        } else {
            listenFree()
        }
    }

    /** Riserva: riconoscimento completo con il modello sul telefono, se manca il servizio vocale di Google. */
    private fun listenFree() {
        val m = model ?: return endCommand()
        try {
            free = SpeechService(Recognizer(m, 16000f), 16000f).also {
                it.startListening(object : VoskListener {
                    override fun onPartialResult(hypothesis: String?) {
                        val p = try { JSONObject(hypothesis ?: "{}").optString("partial") } catch (e: Exception) { "" }
                        if (p.isNotBlank()) Hub.voice.value = Hub.voice.value.copy(transcript = p)
                    }

                    override fun onResult(hypothesis: String?) {
                        val t = try { JSONObject(hypothesis ?: "{}").optString("text") } catch (e: Exception) { "" }
                        if (t.isNotBlank()) main.post { stopFree(); onCommandText(t) }
                    }

                    override fun onFinalResult(hypothesis: String?) {}

                    override fun onError(exception: Exception?) {
                        main.post { stopFree(); endCommand() }
                    }

                    override fun onTimeout() {
                        main.post { stopFree(); endCommand() }
                    }
                }, 8000)
            }
        } catch (e: Exception) {
            endCommand()
        }
    }

    private fun stopFree() {
        free?.let { it.stop(); it.shutdown() }
        free = null
    }

    private val androidListener = object : AndroidRecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.THINKING)
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                val d = dialog
                if (d != null) {
                    listeningCommand = false
                    handleDialog(d, "")
                    return
                }
                endCommand()
            } else {
                // servizio di Google non disponibile: si usa il modello sul telefono
                listenFree()
            }
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull().orEmpty()
            if (text.isBlank()) endCommand() else onCommandText(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!p.isNullOrBlank()) Hub.voice.value = Hub.voice.value.copy(transcript = p)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun endCommand() {
        listeningCommand = false
        main.postDelayed({ nextPendingMessage() }, 1200)
        if (!speaker.speaking) {
            Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.WAITING_WAKE)
        }
        main.postDelayed({ startWake() }, 300)
    }

    private fun say(text: String, then: (() -> Unit)? = null) {
        stopWake()
        Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.SPEAKING, answer = text)
        speaker.say(text) {
            then?.invoke()
            if (!listeningCommand) {
                Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.WAITING_WAKE)
                startWake()
            }
        }
    }

    /** Fa una domanda e ascolta subito la risposta, senza bisogno di ripetere «Ehi Tachi». */
    private fun ask(text: String) {
        stopWake()
        listeningCommand = true
        Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.SPEAKING, answer = text)
        speaker.say(text) { main.postDelayed({ listenCommand() }, 200) }
    }

    private fun onCommandText(text: String) {
        listeningCommand = false
        Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.THINKING, transcript = text)
        val d = dialog
        if (d != null) {
            handleDialog(d, text)
            return
        }
        val cmd = CommandParser.parse(text)
        execute(cmd)
        if (!speaker.speaking && !listeningCommand) endCommand()
    }

    private fun yesNo(text: String): Boolean? {
        val t = CommandParser.normalize(text).trim()
        if (t.isBlank()) return null
        if (Regex("^(no|non|nah|lascia|dopo|annulla|niente|basta|ignora)").containsMatchIn(t)) return false
        if (Regex("^(s[iì]|certo|ok|okay|va bene|leggi|leggilo|rispondi|invia|invialo|manda|mandalo|dai|perfetto|esatto)").containsMatchIn(t)) return true
        return null
    }

    /** Ripete la domanda una volta se non ha capito, poi lascia perdere. */
    private fun retryOrDrop(d: Dialog, question: String) {
        if (dialogRetry < 1) {
            dialogRetry++
            dialog = d
            ask("Non ho capito. $question Sì o no?")
        } else {
            endDialog("Lascio stare.")
        }
    }

    private fun endDialog(text: String) {
        dialog = null
        dialogRetry = 0
        say(text) { main.postDelayed({ nextPendingMessage() }, 800) }
    }

    private fun handleDialog(d: Dialog, text: String) {
        when (d) {
            is Dialog.AskRead -> when (yesNo(text)) {
                true -> {
                    dialogRetry = 0
                    dialog = Dialog.AskReply(d.msg)
                    ask("${d.msg.text}. Vuoi rispondere?")
                }
                false -> endDialog("Va bene.")
                null -> retryOrDrop(d, "Vuoi che lo legga?")
            }
            is Dialog.AskReply -> when (yesNo(text)) {
                true -> {
                    dialogRetry = 0
                    if (d.msg.replyIntent == null) {
                        endDialog("Da qui non posso rispondere a questo messaggio.")
                    } else {
                        dialog = Dialog.ReplyText(d.msg)
                        ask("Cosa rispondo?")
                    }
                }
                false -> {
                    Hub.removeMessage(d.msg.key)
                    endDialog("Va bene.")
                }
                null -> retryOrDrop(d, "Vuoi rispondere?")
            }
            is Dialog.ReplyText -> {
                if (text.isBlank()) {
                    retryOrDrop(d, "Cosa rispondo?")
                } else {
                    dialogRetry = 0
                    dialog = Dialog.ReplyConfirm(d.msg, text)
                    ask("Ho scritto: $text. Lo invio?")
                }
            }
            is Dialog.AskAnswer -> when (yesNo(text)) {
                true -> {
                    dialog = null
                    dialogRetry = 0
                    answerCall(true)
                }
                false -> {
                    dialog = null
                    dialogRetry = 0
                    answerCall(false)
                    say("Rifiutata.")
                }
                null -> if (Hub.incomingCall.value != null) retryOrDrop(d, "Rispondo?") else { dialog = null; endCommand() }
            }
            is Dialog.ReplyConfirm -> when (yesNo(text)) {
                true -> {
                    val ok = NotificationReader.reply(this, d.msg, d.text)
                    if (ok) Hub.removeMessage(d.msg.key)
                    endDialog(if (ok) "Inviato." else "Non riesco a rispondere da qui a questo messaggio.")
                }
                false -> endDialog("Va bene, non lo invio.")
                null -> retryOrDrop(d, "Lo invio?")
            }
        }
    }

    private fun who(m: IncomingMessage): String =
        if (m.group != null) "${m.sender} nel gruppo ${m.group} su ${m.app}" else "${m.sender} su ${m.app}"

    /** Annuncia il messaggio e chiede se leggerlo. */
    private fun announce(m: IncomingMessage) {
        dialogRetry = 0
        dialog = Dialog.AskRead(m)
        ask("Messaggio in arrivo da ${who(m)}. Vuoi che lo legga?")
    }

    /** Passa al prossimo messaggio in coda, se non sei già impegnato con un altro. */
    private fun nextPendingMessage() {
        if (dialog != null || listeningCommand || speaker.speaking || Hub.incomingCall.value != null) return
        val (m, nameOnly) = pendingMessages.removeFirstOrNull() ?: return
        if (Hub.messages.value.none { it.key == m.key }) return nextPendingMessage()
        if (nameOnly) {
            say("Messaggio da ${who(m)}.") { main.postDelayed({ nextPendingMessage() }, 800) }
        } else {
            announce(m)
        }
    }

    private fun isFavorite(name: String): Boolean {
        val favs = settings.favorites.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val n = name.lowercase()
        return favs.any { n.contains(it) }
    }

    // ---------------- chiamate in arrivo ----------------

    private fun onIncomingCall(call: IncomingCall?) {
        if (call == null) {
            // ha già risposto o la chiamata è finita: smetto di chiedere
            if (dialog is Dialog.AskAnswer) {
                dialog = null
                dialogRetry = 0
                recognizer?.cancel()
                speaker.stop()
                listeningCommand = false
                main.postDelayed({ startWake() }, 1500)
            }
            return
        }
        if (!settings.announceCalls || settings.mode != "driver") return
        stopWake()
        recognizer?.cancel()
        speaker.stop()
        dialogRetry = 0
        dialog = Dialog.AskAnswer(call)
        ask("Chiamata da ${call.caller}. Rispondo?")
    }

    @Suppress("DEPRECATION")
    private fun answerCall(accept: Boolean) {
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) {
            say("Mi manca il permesso per rispondere alle chiamate.")
            return
        }
        try {
            val tm = getSystemService(android.telecom.TelecomManager::class.java)
            if (accept) tm?.acceptRingingCall() else tm?.endCall()
        } catch (e: SecurityException) {
        }
        listeningCommand = false
    }

    private fun bringToFront(screen: Screen? = null) {
        screen?.let { Hub.screen.value = it }
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (e: Exception) {
        }
    }

    private fun execute(cmd: Command) {
        when (cmd) {
            is Command.Navigate -> navigate(cmd)
            Command.BackToMaps -> {
                Hub.send(UiAction.BackToMaps)
                bringToFront(Screen.DASHBOARD)
                say("Torno a Google Maps.")
            }
            Command.StopNavigation -> {
                Hub.send(UiAction.StopNavigation)
                say("Navigazione terminata.")
            }
            is Command.PlayMusic -> {
                Radio.stop()
                val key = cmd.app ?: settings.musicApp
                val (label, pkg) = Apps.MUSIC[key] ?: Apps.MUSIC.getValue("spotify")
                if (!Apps.isInstalled(this, pkg)) {
                    say("$label non è installata.")
                } else {
                    Apps.playFromSearch(this, pkg, cmd.query)
                    pausedUntil = System.currentTimeMillis() + 1500
                    main.postDelayed({ bringToFront() }, 2500)
                }
            }
            is Command.PlayRadio -> playRadio(cmd.station)
            Command.NextRadio -> {
                val cur = Radio.current
                val idx = Radio.NATIONAL.indexOfFirst { it.equals(cur, true) }
                playRadio(Radio.NATIONAL[(idx + 1).mod(Radio.NATIONAL.size)])
            }
            Command.Pause -> if (Radio.isActive()) Radio.pause() else MediaSessions.pause(this)
            Command.Resume -> if (Hub.radio.value != null && !MediaSessions.hasSession()) Radio.resume() else MediaSessions.play(this)
            Command.Next -> MediaSessions.next(this)
            Command.Previous -> MediaSessions.previous(this)
            is Command.Volume -> MediaSessions.volume(this, cmd.up)
            is Command.Call -> call(cmd.name)
            Command.ReadMessages -> readMessages()
            Command.Reply -> {
                val m = Hub.messages.value.lastOrNull { it.replyIntent != null }
                if (m == null) say("Non ci sono messaggi a cui rispondere.") else {
                    dialogRetry = 0
                    dialog = Dialog.ReplyText(m)
                    ask("Cosa rispondo a ${who(m)}?")
                }
            }
            Command.JoinMeeting -> joinMeeting()
            is Command.OpenApp -> openApp(cmd.name)
            is Command.SearchVideo -> {
                if (!Apps.searchVideo(this, cmd.app, cmd.title)) say("Non trovo l'app ${cmd.app}.")
            }
            is Command.SetMode -> {
                Hub.send(UiAction.SetMode(cmd.driver))
                bringToFront()
                say(if (cmd.driver) "Modalità guidatore." else "Modalità passeggero.")
            }
            Command.WhereIsCar -> whereIsCar()
            Command.FindFuel -> {
                bringToFront(Screen.FUEL)
                say("Cerco i distributori più economici qui vicino.")
            }
            is Command.AddToBar -> editBar(cmd.app, add = true)
            is Command.RemoveFromBar -> editBar(cmd.app, add = false)
            Command.Dashboard -> bringToFront(Screen.DASHBOARD)
            Command.Settings -> bringToFront(Screen.SETTINGS)
            Command.Mute -> {
                alertsMuted = true
                say("Avvisi silenziati per questo viaggio.")
            }
            Command.Weather -> {
                val w = Hub.weather.value
                if (w == null) say("Non ho ancora i dati del meteo.") else say("${w.description}, ${w.tempC.toInt()} gradi.")
            }
            Command.Cancel -> say("Va bene.")
            Command.CloseApp -> say("Chiudo Tachimetro.") { Hub.send(UiAction.Close) }
            is Command.AskGemini -> askGemini(cmd.question)
        }
    }

    private fun navigate(cmd: Command.Navigate) {
        when (cmd.place) {
            "@macchina" -> return whereIsCar()
            "@distributore" -> {
                val f = Hub.fuel.value.firstOrNull()
                if (f == null) {
                    bringToFront(Screen.FUEL)
                    say("Prima cerco i distributori.")
                } else {
                    Hub.send(UiAction.NavigateCoords(f.lat, f.lng, f.name, cmd.waze))
                    bringToFront(Screen.DASHBOARD)
                    say("Ti porto da ${f.name}.")
                }
                return
            }
            "@appuntamento" -> {
                val loc = nextAppointmentPlace()
                if (loc == null) say("Non trovo appuntamenti con un indirizzo nelle prossime ore.") else {
                    Hub.send(UiAction.NavigateTo(loc, cmd.waze))
                    bringToFront(Screen.DASHBOARD)
                    say("Ti porto al prossimo appuntamento.")
                }
                return
            }
        }
        val place = when (cmd.place) {
            "@casa" -> settings.home.ifBlank { return say("Non hai ancora impostato l'indirizzo di casa.") }
            "@lavoro" -> settings.work.ifBlank { return say("Non hai ancora impostato l'indirizzo del lavoro.") }
            else -> cmd.place
        }
        Hub.send(UiAction.NavigateTo(place, cmd.waze))
        bringToFront(Screen.DASHBOARD)
        val nice = when (cmd.place) {
            "@casa" -> "casa"
            "@lavoro" -> "il lavoro"
            else -> place
        }
        say(if (cmd.waze) "Apro Waze verso $nice." else "Calcolo il percorso verso $nice.")
    }

    private fun playRadio(spoken: String) {
        val name = if (spoken.isBlank()) (Radio.current ?: Radio.NATIONAL.first()) else (Radio.match(spoken) ?: spoken)
        scope.launch {
            try {
                val st = Radio.resolve(name)
                if (st == null) {
                    say("Non trovo la radio $name.")
                } else {
                    MediaSessions.pause(this@VoiceService)
                    Radio.play(this@VoiceService, st)
                }
            } catch (e: Exception) {
                say("La radio non è raggiungibile in questo momento.")
            }
        }
    }

    private fun call(name: String) {
        if (!granted(Manifest.permission.READ_CONTACTS) || !granted(Manifest.permission.CALL_PHONE)) {
            return say("Per chiamare servono i permessi per contatti e telefono, nelle impostazioni.")
        }
        val found = findContact(name) ?: name.split(" ").firstOrNull()?.let { findContact(it) }
        if (found == null) return say("Non trovo $name tra i contatti.")
        say("Chiamo ${found.first}.") {
            try {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + found.second)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }

    private fun findContact(name: String): Pair<String, String>? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        return try {
            contentResolver.query(
                uri, proj,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"),
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY + " DESC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }
        } catch (e: Exception) {
            null
        }
    }

    private fun readMessages() {
        val list = Hub.messages.value
        if (list.isEmpty()) return say("Nessun messaggio nuovo.")
        pendingMessages.clear()
        list.drop(1).forEach { pendingMessages.addLast(it to false) }
        announce(list.first())
    }

    private fun onMessages(list: List<IncomingMessage>) {
        for (m in list) {
            if (!announced.add(m.key)) continue
            if (!settings.readMessages || settings.mode != "driver") continue
            if (System.currentTimeMillis() - m.time > 120_000) continue
            val fav = isFavorite(m.sender)
            if (m.group != null && !fav) {
                when (settings.groupMode) {
                    "never" -> continue
                    "name" -> {
                        pendingMessages.addLast(m to true)
                        continue
                    }
                }
            }
            pendingMessages.addLast(m to false)
        }
        if (announced.size > 300) announced.clear()
        nextPendingMessage()
    }

    private fun calendarEvents(fromMs: Long, toMs: Long): List<Triple<String, String, String>> {
        if (!granted(Manifest.permission.READ_CALENDAR)) return emptyList()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, fromMs)
        ContentUris.appendId(b, toMs)
        val proj = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = ArrayList<Triple<String, String, String>>()
        try {
            contentResolver.query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) out += Triple(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun joinMeeting() {
        val now = System.currentTimeMillis()
        val re = Regex("https://teams\\.(microsoft|live)\\.com/(l/meetup-join|meet)/[^\\s\"<>]+")
        val ev = calendarEvents(now - 15 * 60_000L, now + 60 * 60_000L)
            .firstNotNullOfOrNull { e -> re.find(e.second + " " + e.third)?.let { e.first to it.value } }
        if (ev == null) {
            val ok = Apps.findByName(this, "Teams")?.let { Apps.launch(this, it.packageName) } ?: false
            say(if (ok) "Non trovo il link della riunione nel calendario, apro Teams." else "Teams non è installato.")
        } else {
            say("Entro nella riunione ${ev.first}. Ricordati di spegnere la videocamera.") { Apps.openUrl(this, ev.second) }
        }
    }

    private fun nextAppointmentPlace(): String? {
        val now = System.currentTimeMillis()
        return calendarEvents(now, now + 12 * 3600_000L).firstOrNull { it.third.isNotBlank() && !it.third.contains("teams", true) }?.third
    }

    private fun openApp(name: String) {
        val app = Apps.findByName(this, name) ?: return say("Non trovo l'app $name.")
        Apps.launch(this, app.packageName)
        pausedUntil = System.currentTimeMillis() + 1500
    }

    private fun editBar(name: String, add: Boolean) {
        val app = Apps.findByName(this, name) ?: return say("Non trovo l'app $name.")
        val passenger = settings.mode == "passenger"
        val cur = if (passenger) settings.barPassenger else settings.bar
        val id = "app:" + app.packageName
        val next = if (add) (if (id in cur) cur else cur + id) else cur - id
        scope.launch { Prefs.setBar(applicationContext, passenger, next) }
        say(if (add) "Ho aggiunto ${app.label} alla barra." else "Ho tolto ${app.label} dalla barra.")
    }

    private fun whereIsCar() {
        if (settings.parkingTime == 0L) return say("Non ho ancora salvato un parcheggio.")
        bringToFront(Screen.PARKING)
        val here = Hub.location.value
        if (here == null) return say("Ecco dove hai parcheggiato.")
        val d = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, settings.parkingLat, settings.parkingLng, d)
        val txt = if (d[0] < 1000) "${(d[0] / 10).toInt() * 10} metri" else String.format(java.util.Locale.ITALIAN, "%.1f chilometri", d[0] / 1000)
        say("La macchina è a $txt da qui.")
    }

    private fun askGemini(q: String) {
        val key = settings.geminiKey
        if (key.isBlank()) {
            pausedUntil = System.currentTimeMillis() + 20_000
            try {
                startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                say("Non ho capito il comando.")
            }
            return
        }
        Hub.voice.value = Hub.voice.value.copy(phase = VoicePhase.THINKING)
        scope.launch {
            try {
                say(Gemini.ask(key, q))
            } catch (e: Exception) {
                say("Gemini non risponde in questo momento.")
            }
        }
    }

    // ---------------- posizione, autovelox, meteo ----------------

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            Hub.location.value = loc
            onLocation(loc)
        }
    }

    private fun startLocation() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(500L).build()
        try {
            fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
        }
    }

    private var camerasBusy = false

    private fun onLocation(loc: Location) {
        // meteo ogni 15 minuti o dopo 10 km
        val wp = lastWeatherPlace
        if (settings.weatherOn && (System.currentTimeMillis() - lastWeatherAt > 15 * 60_000L || (wp != null && wp.distanceTo(loc) > 10_000))) {
            lastWeatherAt = System.currentTimeMillis()
            lastWeatherPlace = loc
            scope.launch {
                try { Hub.weather.value = Weather.fetch(loc.latitude, loc.longitude) } catch (e: Exception) { }
            }
        }
        // autovelox
        if (!settings.cameras) {
            Hub.camera.value = null
            return
        }
        if (!camerasBusy) {
            camerasBusy = true
            scope.launch {
                try { SpeedCameras.refreshIfNeeded(loc) } catch (e: Exception) { } finally { camerasBusy = false }
            }
        }
        val hit = SpeedCameras.ahead(loc, settings.cameraWarnMeters)
        if (hit == null) {
            Hub.camera.value = null
            return
        }
        val (cam, dist) = hit
        Hub.camera.value = CameraAlert(dist, "Autovelox fisso", cam.maxSpeed)
        if (SpeedCameras.firstWarning(cam.id) && !alertsMuted) {
            when (settings.speedAlert) {
                "tone" -> speaker.beep(35)
                else -> {}
            }
        }
    }

    companion object {
        const val ACTION_LISTEN = "it.giacomo.tachimetro.LISTEN"
        const val ACTION_SAY = "it.giacomo.tachimetro.SAY"
        const val EXTRA_TEXT = "text"

        @Volatile
        var running = false

        private val WAKE_WORDS = listOf("tachi", "tacchi", "taxi", "ta chi")
        private const val WAKE_GRAMMAR_LOW = "[\"ehi tachi\", \"ehi tacchi\", \"ehi taxi\", \"[unk]\"]"
        private const val WAKE_GRAMMAR_HIGH =
            "[\"ehi tachi\", \"ehi tacchi\", \"ehi taxi\", \"hey tacchi\", \"e tacchi\", \"ehi ta chi\", \"tacchi\", \"taxi\", \"tachi\", \"[unk]\"]"
        private const val WAKE_GRAMMAR =
            "[\"ehi tachi\", \"ehi tacchi\", \"ehi taxi\", \"hey tacchi\", \"e tacchi\", \"ehi ta chi\", \"[unk]\"]"

        fun start(context: Context) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            context.startForegroundService(Intent(context, VoiceService::class.java))
        }

        fun listenNow(context: Context) {
            context.startService(Intent(context, VoiceService::class.java).setAction(ACTION_LISTEN))
        }
    }
}

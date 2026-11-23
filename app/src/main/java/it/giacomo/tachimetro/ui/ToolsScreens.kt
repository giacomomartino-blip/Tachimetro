package it.giacomo.tachimetro.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import it.giacomo.tachimetro.BuildConfig
import it.giacomo.tachimetro.CarGate
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.Screen
import it.giacomo.tachimetro.VoicePhase
import it.giacomo.tachimetro.car.Dnd
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.notify.NotificationReader
import it.giacomo.tachimetro.notify.WhatsAppSender
import it.giacomo.tachimetro.voice.VoiceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

/** Ricalcola lo stato quando torni nell'app dalle impostazioni di Android. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                tick++
                delay(2000)
            }
        }
    }
    return tick
}

@Composable
private fun Dot(ok: Boolean?) {
    val p = LocalPal.current
    val c = when (ok) {
        true -> p.acc
        false -> p.voice
        null -> p.muted
    }
    Box(Modifier.size(14.dp).clip(CircleShape).background(c))
}

@Composable
private fun StatusRow(label: String, ok: Boolean?, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot(ok)
        Column(Modifier.weight(1f)) {
            T(label, 15.sp, FontWeight.Bold)
            Muted(detail, 13.sp)
        }
    }
}

// ---------------- configurazione guidata ----------------

@Composable
fun SetupScreen(s: AppSettings, onAskPermissions: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    val tick = rememberResumeTick()
    val steps = 5

    fun finish() {
        scope.launch { Prefs.edit(context) { it[Keys.setupDone] = true } }
        Hub.screen.value = if (s.mode == "passenger") Screen.PASSENGER else Screen.DASHBOARD
    }

    Column(
        Modifier.fillMaxSize().background(p.bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        T("Tachimetro · configurazione ${step + 1} di $steps", 14.sp, FontWeight.Bold, p.acc)
        if (tick < 0) return@Column
        when (step) {
            0 -> {
                val ok = granted(context, Manifest.permission.RECORD_AUDIO) && granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                T("Permessi", 28.sp, FontWeight.ExtraBold)
                Muted("Microfono per «Ehi Tachi», posizione per mappa e autovelox, contatti e telefono per chiamare, calendario per le riunioni, Bluetooth per riconoscere l'auto. Tutto resta su questo dispositivo.", 15.sp)
                StatusRow("Permessi principali", ok, if (ok) "Concessi" else "Da concedere")
                Btn(if (ok) "Rivedi i permessi" else "Concedi i permessi", !ok) { onAskPermissions() }
            }
            1 -> {
                val ok = NotificationReader.connected
                T("Messaggi e musica", 28.sp, FontWeight.ExtraBold)
                Muted("Per leggerti WhatsApp, SMS e Teams e per comandare Spotify e le altre app di musica serve l'accesso alle notifiche. Nella pagina che si apre attiva Tachimetro, poi torna qui.", 15.sp)
                StatusRow("Accesso alle notifiche", ok, if (ok) "Attivo" else "Non ancora attivo")
                Btn("Apri l'impostazione", !ok) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            2 -> {
                val ok = Dnd.hasAccess(context)
                T("Non disturbare mentre guidi", 28.sp, FontWeight.Bold)
                Muted("Mentre Tachimetro è attivo il telefono non suona per notifiche e messaggi: te li annuncio io, uno alla volta. Chiamate e allarmi passano. Quando chiudi torna tutto come prima.", 15.sp)
                StatusRow("Permesso Non disturbare", ok, if (ok) "Concesso" else "Da concedere")
                Btn("Apri l'impostazione", !ok) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            3 -> {
                T("Batteria", 28.sp, FontWeight.ExtraBold)
                Muted("Samsung può sospendere le app per risparmiare batteria, e Tachimetro smetterebbe di ascoltarti mentre usi Waze o Spotify. Nella pagina che si apre scegli Tachimetro e imposta «Senza restrizioni». Quando chiudi Tachimetro non resta comunque nulla acceso.", 15.sp)
                Btn("Apri l'impostazione") {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> {
                T("Casa e lavoro", 28.sp, FontWeight.ExtraBold)
                Muted("Così basta dire «portami a casa» o «portami al lavoro».", 15.sp)
                Field("Indirizzo di casa", s.home) { v -> scope.launch { Prefs.edit(context) { it[Keys.home] = v } } }
                Field("Indirizzo del lavoro", s.work) { v -> scope.launch { Prefs.edit(context) { it[Keys.work] = v } } }
                Muted("L'auto la riconosco da sola la prima volta che ti colleghi al suo Bluetooth.", 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) Btn("Indietro") { step-- }
            if (step < steps - 1) Btn("Avanti", true) { step++ } else Btn("Fine", true) { finish() }
            Btn("Salta") { finish() }
        }
    }
}

// ---------------- stato ----------------

@Composable
fun StatusScreen(s: AppSettings) {
    val context = LocalContext.current
    val p = LocalPal.current
    val tick = rememberResumeTick()
    val gate by Hub.carGate.collectAsState()
    val carName by Hub.carName.collectAsState()
    val voice by Hub.voice.collectAsState()
    val loc by Hub.location.collectAsState()
    val maps by Hub.mapsStatus.collectAsState()
    val test by Hub.testMode.collectAsState()
    val back = if (s.mode == "passenger") Screen.PASSENGER else Screen.DASHBOARD

    Column(
        Modifier.fillMaxSize().background(p.bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Tachimetro ${BuildConfig.VERSION_NAME}", 13.sp, FontWeight.Bold, p.acc)
                T("Stato", 28.sp, FontWeight.ExtraBold)
            }
            Btn("Indietro") { Hub.screen.value = back }
        }
        if (tick < 0) return@Column
        CardBox(pad = 16.dp, gap = 14.dp) {
            StatusRow(
                "Auto",
                when { test -> true; gate == CarGate.READY -> true; s.mode == "passenger" || !s.requireCar -> null; else -> false },
                when {
                    s.mode == "passenger" -> "Modalità passeggero: l'auto non serve"
                    test -> "Prova senza auto"
                    gate == CarGate.READY -> "Collegata: ${carName.ifBlank { "auto riconosciuta" }}"
                    gate == CarGate.NO_PERMISSION -> "Manca il permesso Bluetooth"
                    gate == CarGate.ASK -> "In attesa della tua risposta"
                    else -> "Non collegata · auto conosciute: ${s.knownCars.size}"
                },
            )
            val mic = granted(context, Manifest.permission.RECORD_AUDIO)
            StatusRow("Microfono", mic, if (mic) "Permesso concesso" else "Permesso mancante")
            StatusRow(
                "Parola chiave «Ehi Tachi»",
                when (voice.phase) { VoicePhase.WAITING_WAKE, VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING -> true; VoicePhase.LOADING -> null; else -> false },
                voice.error ?: when (voice.phase) {
                    VoicePhase.OFF -> if (VoiceService.running) "Spenta" else "Spenta: Tachimetro non è attivo"
                    VoicePhase.LOADING -> "Preparo il modello vocale"
                    else -> "In ascolto · sensibilità ${mapOf("low" to "bassa", "normal" to "normale", "high" to "alta")[s.wakeSensitivity]}"
                },
            )
            val gErr by Hub.geminiError.collectAsState()
            StatusRow(
                "Gemini",
                if (s.geminiKey.isBlank()) null else gErr == null,
                if (s.geminiKey.isBlank()) "Nessuna chiave: le domande aprono l'assistente del telefono" else gErr?.let { "Ultimo errore: $it" } ?: "Chiave inserita",
            )
            val google = SpeechRecognizer.isRecognitionAvailable(context)
            StatusRow("Riconoscimento dei comandi", true, if (google) "Servizio vocale di Google" else "Modello interno del telefono")
            val gps = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
            StatusRow("Posizione", gps && loc != null, if (!gps) "Permesso mancante" else if (loc == null) "In attesa del GPS" else "Ricevuta")
            StatusRow("Mappa Google", if (!BuildConfig.HAS_MAPS_KEY) false else maps == null, if (!BuildConfig.HAS_MAPS_KEY) "Chiave mancante" else maps ?: "Pronta")
            StatusRow("Lettura notifiche", NotificationReader.connected, if (NotificationReader.connected) "Attiva" else "Da attivare: serve per messaggi e musica")
            val dnd = Dnd.hasAccess(context)
            StatusRow("Non disturbare", if (!s.dnd) null else dnd, if (!s.dnd) "Spento nelle impostazioni" else if (dnd) (if (Dnd.isApplied(context)) "Attivo adesso" else "Pronto") else "Permesso mancante")
            val calls = granted(context, Manifest.permission.ANSWER_PHONE_CALLS) && granted(context, Manifest.permission.READ_PHONE_STATE)
            StatusRow("Chiamate a voce", if (!s.announceCalls) null else calls, if (!s.announceCalls) "Spente nelle impostazioni" else if (calls) "Pronte" else "Permessi telefono mancanti")
            val contacts = granted(context, Manifest.permission.READ_CONTACTS) && granted(context, Manifest.permission.CALL_PHONE)
            StatusRow("Contatti e chiamate in uscita", contacts, if (contacts) "Pronti" else "Permessi mancanti")
            val cal = granted(context, Manifest.permission.READ_CALENDAR)
            StatusRow("Calendario", cal, if (cal) "Pronto per le riunioni Teams" else "Permesso mancante")
            val sms = granted(context, Manifest.permission.SEND_SMS)
            StatusRow("Invio SMS a voce", sms, if (sms) "Pronto" else "Permesso mancante")
            val wa = WhatsAppSender.isEnabled(context)
            StatusRow(
                "Invio WhatsApp a voce",
                if (!s.waAutoSend) null else wa,
                if (!s.waAutoSend) "Apre la chat pronta, tocchi tu Invia" else if (wa) "Invia da solo" else "Da attivare in Accessibilità",
            )
        }
        Muted("Se qualcosa non va, manda uno screenshot di questa pagina.", 14.sp)
    }
}

// ---------------- prova della parola chiave ----------------

@Composable
fun WakeTestScreen(s: AppSettings) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val heard by Hub.wakeHeard.collectAsState()
    val detectedAt by Hub.wakeDetectedAt.collectAsState()
    val voice by Hub.voice.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var count by remember { mutableIntStateOf(0) }
    var lastSeen by remember { mutableStateOf(detectedAt) }
    LaunchedEffect(detectedAt) {
        if (detectedAt != lastSeen) {
            lastSeen = detectedAt
            count++
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(300)
        }
    }
    val recent = now - detectedAt < 2500
    Column(
        Modifier.fillMaxSize().background(p.bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Tachimetro", 13.sp, FontWeight.Bold, p.acc)
                T("Prova «Ehi Tachi»", 28.sp, FontWeight.ExtraBold)
            }
            Btn("Indietro") { Hub.screen.value = Screen.SETTINGS }
        }
        if (!VoiceService.running) {
            Muted("La voce è spenta: collega l'auto oppure usa «Prova senza auto» dalla schermata di attesa.", 15.sp)
        }
        Muted("Di' «Ehi Tachi» più volte, anche con la radio accesa e il finestrino aperto. Qui vedi cosa sente il telefono e quando la riconosce.", 15.sp)
        Box(
            Modifier.fillMaxWidth().height(140.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .background(if (recent) p.acc else p.surf),
            contentAlignment = Alignment.Center,
        ) {
            T(if (recent) "Riconosciuta!" else "In ascolto…", 30.sp, FontWeight.ExtraBold, if (recent) p.onAcc else p.text)
        }
        CardBox(pad = 16.dp) {
            Muted("Ultima cosa sentita")
            T(heard.ifBlank { "…" }, 20.sp, FontWeight.Bold)
            Muted("Riconosciuta $count volte in questa prova · stato: ${voice.phase.name.lowercase()}")
        }
        Muted("Sensibilità")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("low" to "Bassa", "normal" to "Normale", "high" to "Alta").forEach { (k, l) ->
                Chip(l, s.wakeSensitivity == k) { scope.launch { Prefs.edit(context) { it[Keys.wakeSensitivity] = k } } }
            }
        }
        Muted("Alta: si attiva più facilmente, ma può scattare per sbaglio. Bassa: serve dire chiaramente «Ehi Tachi».", 14.sp)
    }
}

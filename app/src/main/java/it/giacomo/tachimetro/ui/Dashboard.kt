package it.giacomo.tachimetro.ui

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giacomo.tachimetro.BuildConfig
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.Screen
import it.giacomo.tachimetro.UiAction
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.media.Apps
import it.giacomo.tachimetro.media.MediaSessions
import it.giacomo.tachimetro.media.Radio
import it.giacomo.tachimetro.nav.NavController
import it.giacomo.tachimetro.nav.NavMap
import it.giacomo.tachimetro.voice.VoiceService
import kotlinx.coroutines.launch

enum class Panel { MUSIC, RADIO, MESSAGES }

fun monoKey(pkg: String): String = when {
    pkg.contains("spotify") -> "sp"
    pkg.contains("whatsapp") -> "wa"
    pkg.contains("teams") -> "tm"
    pkg.contains("waze") -> "wz"
    pkg.contains("youtube") -> "yt"
    pkg.contains("gemini") || pkg.contains("bard") -> "gm"
    pkg.contains("amazon") -> "am"
    else -> listOf("sp", "wa", "tm", "wz", "yt", "gm", "am", "cl")[(pkg.hashCode() and 0x7fffffff) % 8]
}

fun monoLabel(label: String): String {
    val words = label.split(" ").filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
        else -> label.take(2).replaceFirstChar { it.uppercase() }
    }
}

// ---------------- barra delle app ----------------

@Composable
fun AppBarItems(
    items: List<String>,
    panel: Panel,
    onPanel: (Panel) -> Unit,
    vertical: Boolean,
    size: Dp,
) {
    val context = LocalContext.current
    val p = LocalPal.current
    val messages by Hub.messages.collectAsState()
    val screen by Hub.screen.collectAsState()
    val all = items + listOf("fn:all", "fn:settings", "fn:close")
    @Composable
    fun item(id: String) {
        val active = when (id) {
            "fn:home" -> screen == Screen.DASHBOARD && panel == Panel.MUSIC
            "fn:music" -> screen == Screen.DASHBOARD && panel == Panel.MUSIC
            "fn:radio" -> screen == Screen.DASHBOARD && panel == Panel.RADIO
            "fn:msg" -> screen == Screen.DASHBOARD && panel == Panel.MESSAGES
            "fn:settings" -> screen == Screen.SETTINGS
            "fn:all" -> screen == Screen.APP_BAR
            else -> false
        }
        val click: () -> Unit = {
            when (id) {
                "fn:home", "fn:music" -> { Hub.screen.value = Screen.DASHBOARD; onPanel(Panel.MUSIC) }
                "fn:radio" -> { Hub.screen.value = Screen.DASHBOARD; onPanel(Panel.RADIO) }
                "fn:msg" -> { Hub.screen.value = Screen.DASHBOARD; onPanel(Panel.MESSAGES) }
                "fn:phone" -> try {
                    context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
                "fn:all" -> Hub.screen.value = Screen.APP_BAR
                "fn:settings" -> Hub.screen.value = Screen.SETTINGS
                "fn:close" -> Hub.send(UiAction.Close)
                else -> Apps.launch(context, id.removePrefix("app:"))
            }
        }
        val label = when (id) {
            "fn:home" -> "Cruscotto"
            "fn:phone" -> "Telefono"
            "fn:msg" -> "Messaggi"
            "fn:music" -> "Musica"
            "fn:radio" -> "Radio"
            "fn:all" -> "Barra delle app"
            "fn:settings" -> "Impostazioni"
            "fn:close" -> "Chiudi Tachimetro"
            else -> Apps.label(context, id.removePrefix("app:"))
        }
        Box(
            Modifier.size(size).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = click),
            contentAlignment = Alignment.Center,
        ) {
            if (id.startsWith("app:")) {
                Mono(monoLabel(label), monoKey(id), size, if (size > 52.dp) 17.sp else 15.sp)
            } else {
                val icon = when (id) {
                    "fn:home" -> Ic.home
                    "fn:phone" -> Ic.phone
                    "fn:msg" -> Ic.msg
                    "fn:music" -> Ic.music
                    "fn:radio" -> Ic.radio
                    "fn:all" -> Ic.grid
                    "fn:close" -> Ic.power
                    else -> Ic.gear
                }
                IconBox(icon, if (active) p.acc else p.surf2, if (active) p.onAcc else p.text, size, if (size > 52.dp) 24.dp else 22.dp, desc = label)
            }
            if (id == "fn:msg" && messages.isNotEmpty()) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(p.voice),
                    contentAlignment = Alignment.Center,
                ) { T(messages.size.coerceAtMost(9).toString(), 11.sp, FontWeight.ExtraBold, p.onVoice) }
            }
        }
    }
    val scroll = rememberScrollState()
    if (vertical) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(scroll).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { all.forEach { item(it) } }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { (all.take(5) + "fn:close").forEach { item(it) } }
    }
}

// ---------------- intestazione ----------------

@Composable
fun Header(s: AppSettings, night: Boolean, bigger: Boolean = false) {
    val context = LocalContext.current
    val p = LocalPal.current
    val voice by Hub.voice.collectAsState()
    val health by Hub.health.collectAsState()
    val weather by Hub.weather.collectAsState()
    val scope = rememberCoroutineScope()
    val fs = if (bigger) 14.sp else 12.sp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoicePill(voice.phase, onTap = { VoiceService.listenNow(context) }, fontSize = fs)
        Spacer(Modifier.weight(1f))
        if (s.weatherOn && weather != null) {
            Pill {
                Icon(Ic.cloud, null, tint = p.acc, modifier = Modifier.size(16.dp))
                T("${weather!!.tempC.toInt()}°", fs, FontWeight.Bold)
            }
        }
        if (health.batteryPct >= 0) {
            Icon(
                Ic.battery,
                contentDescription = if (health.charging) "In carica, ${health.batteryPct}%" else "Batteria ${health.batteryPct}%",
                tint = if (health.lowBattery) p.voice else p.acc,
                modifier = Modifier.size(18.dp),
            )
        }
        ThemePill(night, s.themeMode == "auto") {
            val next = when (s.themeMode) { "auto" -> "night"; "night" -> "day"; else -> "auto" }
            scope.launch { Prefs.edit(context) { it[Keys.themeMode] = next } }
        }
        Clock(if (bigger) 20.sp else 17.sp)
    }
}

// ---------------- pannello ----------------

@Composable
fun MusicCard(scale: Float = 1f) {
    val context = LocalContext.current
    val p = LocalPal.current
    val np by Hub.nowPlaying.collectAsState()
    val radio by Hub.radio.collectAsState()
    val r = radio
    if (r != null && (np == null || np?.isPlaying != true)) {
        CardBox(pad = 14.dp, gap = 10.dp) {
            Muted("Radio · DAB via internet")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBox(Ic.radio, p.tints.getValue("cl").first, p.tints.getValue("cl").second, 52.dp)
                Column(Modifier.weight(1f)) {
                    T(r.name, 16.sp, FontWeight.ExtraBold, maxLines = 1)
                    Muted(r.error ?: if (r.isPlaying) "In diretta" else "In pausa")
                }
                RoundBtn(if (r.isPlaying) Ic.pause else Ic.play, if (r.isPlaying) "Pausa" else "Riprendi", 52.dp, strong = true) {
                    if (r.isPlaying) Radio.pause() else Radio.resume()
                }
            }
        }
        return
    }
    val n = np
    CardBox(pad = if (scale > 1.1f) 18.dp else 14.dp, gap = 10.dp) {
        if (n == null) {
            T("Nessuna musica in riproduzione", 15.sp, FontWeight.Bold)
            Muted("Di' «Ehi Tachi, metti Vasco su Spotify» oppure scegli un'app")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Apps.MUSIC.values.forEach { (label, pkg) ->
                    if (Apps.isInstalled(context, pkg)) Btn(label, height = 44.dp) { Apps.launch(context, pkg) }
                }
            }
        } else {
            Muted("${n.sourceLabel}")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size((56 * scale).dp).clip(RoundedCornerShape(14.dp)).background(p.surf2)) {
                    Box(Modifier.align(Alignment.BottomEnd).size((40 * scale).dp).clip(CircleShape).background(p.voice.copy(alpha = 0.7f)))
                }
                Column(Modifier.weight(1f)) {
                    T(n.title, if (scale > 1.1f) 19.sp else 16.sp, FontWeight.ExtraBold, maxLines = 2)
                    if (n.artist.isNotBlank()) Muted(n.artist, 13.sp, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                val b = if (scale > 1.1f) 56.dp else 48.dp
                RoundBtn(Ic.prev, "Brano precedente", b) { MediaSessions.previous(context) }
                RoundBtn(if (n.isPlaying) Ic.pause else Ic.play, if (n.isPlaying) "Pausa" else "Riproduci", b + 8.dp, strong = true) { MediaSessions.playPause(context) }
                RoundBtn(Ic.next, "Brano successivo", b) { MediaSessions.next(context) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioPanel(s: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var regional by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, s.radioRegion) {
        if (tab == 1) {
            error = null
            regional = try { Radio.regional(s.radioRegion) } catch (e: Exception) { error = "Elenco regionale non raggiungibile"; emptyList() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Nazionali", tab == 0) { tab = 0 }
            Chip("Regionali", tab == 1) { tab = 1 }
        }
        MusicCard()
        val list = if (tab == 0) Radio.NATIONAL else regional
        if (tab == 1 && s.radioRegion.isBlank()) Muted("Imposta la tua regione nelle impostazioni, alla voce Musica e radio.")
        error?.let { Muted(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { name ->
                Btn(name, height = 46.dp) {
                    scope.launch {
                        try {
                            val st = Radio.resolve(name)
                            if (st != null) {
                                MediaSessions.pause(context)
                                Radio.play(context, st)
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessagesPanel() {
    val context = LocalContext.current
    val p = LocalPal.current
    val messages by Hub.messages.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Messaggi · ${messages.size}", 13.sp, FontWeight.ExtraBold)
        if (messages.isEmpty()) {
            Muted("Nessun messaggio nuovo. I messaggi di WhatsApp, SMS e Teams compaiono qui e vengono letti ad alta voce.")
        }
        messages.reversed().take(6).forEach { m ->
            CardBox(pad = 12.dp, gap = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Mono(m.sender.take(1).uppercase(), when (m.app) { "WhatsApp" -> "wa"; "Teams" -> "tm"; else -> "gm" }, 40.dp, 14.sp, 20.dp)
                    Column(Modifier.weight(1f)) {
                        T(m.sender, 15.sp, FontWeight.ExtraBold, maxLines = 1)
                        Muted(listOfNotNull(m.group, m.app).joinToString(" · "))
                    }
                }
                T(m.text, 15.sp, maxLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Leggi", true, Modifier.weight(1f), 44.dp, Ic.speak) {
                        context.startService(
                            Intent(context, VoiceService::class.java).setAction(VoiceService.ACTION_SAY)
                                .putExtra(VoiceService.EXTRA_TEXT, "${m.sender} su ${m.app}: ${m.text}"),
                        )
                    }
                    Btn("Fatto", false, Modifier.weight(1f), 44.dp) { Hub.removeMessage(m.key) }
                }
            }
        }
        if (messages.isNotEmpty()) Muted("All'arrivo ti chiedo se leggerlo e se rispondere. La risposta te la rileggo prima di inviarla.")
    }
}

@Composable
fun StatusCards(s: AppSettings) {
    val p = LocalPal.current
    val nav by Hub.nav.collectAsState()
    val cam by Hub.camera.collectAsState()
    val health by Hub.health.collectAsState()
    val maps by Hub.mapsStatus.collectAsState()
    cam?.let { c ->
        CardBox(pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBox(Ic.camera, p.voice, p.onVoice, 40.dp)
                Column(Modifier.weight(1f)) {
                    T("${c.label} tra ${c.distanceM} m", 15.sp, FontWeight.ExtraBold)
                    Muted(c.maxSpeed?.let { "Limite $it km/h" } ?: "Rispetta il limite")
                }
                c.maxSpeed?.let { SpeedSign(it, 40.dp) }
            }
        }
    }
    if (nav.active) {
        CardBox(pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (nav.viaWaze) Mono("Wz", "wz", 40.dp, 13.sp, 12.dp) else IconBox(Ic.nav, p.surf2, p.route, 40.dp)
                Column(Modifier.weight(1f)) {
                    T((if (nav.viaWaze) "Con Waze verso " else "Verso ") + nav.destination, 14.sp, FontWeight.ExtraBold, maxLines = 1)
                    Muted(if (nav.viaWaze) "Di' «torna a Google Maps» per la mappa integrata" else listOf(nav.etaText.let { if (it.isBlank()) "" else "Arrivo $it" }, nav.remainingText).filter { it.isNotBlank() }.joinToString(" · "))
                }
                RoundBtn(Ic.stop, "Termina la navigazione", 44.dp) { Hub.send(UiAction.StopNavigation) }
            }
        }
    }
    if (s.healthAlerts && health.hot) {
        CardBox(pad = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBox(Ic.thermo, p.surf2, p.voice, 36.dp, 20.dp, 10.dp)
                Column {
                    T("Il telefono è a ${health.batteryTempC.toInt()} °C", 13.sp, FontWeight.ExtraBold)
                    Muted("Ho abbassato la luminosità. Se puoi, toglilo dal sole")
                }
            }
        }
    } else if (s.healthAlerts && health.lowBattery) {
        CardBox(pad = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBox(Ic.battery, p.surf2, p.voice, 36.dp, 20.dp, 10.dp)
                Column {
                    T("Batteria al ${health.batteryPct}% e non in carica", 13.sp, FontWeight.ExtraBold)
                    Muted("Collega il caricatore: schermo, GPS e voce consumano molto")
                }
            }
        }
    }
    maps?.let { msg ->
        if (!nav.active) CardBox(pad = 10.dp) { Muted(msg) }
    }
}

// ---------------- mappa ----------------

@Composable
fun MapArea(night: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val loc by Hub.location.collectAsState()
    val nav by Hub.nav.collectAsState()
    val speeding by Hub.speeding.collectAsState()
    val maps by Hub.mapsStatus.collectAsState()
    Box(modifier.background(p.surf2)) {
        if (BuildConfig.HAS_MAPS_KEY) {
            NavMap(night, Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Ic.nav, null, tint = p.route, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                T("Mappa in attesa della chiave di Google", 16.sp, FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Muted(maps ?: "Intanto «portami a…» apre la navigazione nell'app Google Maps.")
            }
        }
        // velocità attuale quando la guida di Google non è attiva (durante la guida la mostra la mappa stessa)
        val kmh = loc?.let { if (it.hasSpeed()) (it.speed * 3.6f).toInt() else null }
        if (kmh != null && (!nav.active || nav.viaWaze || !BuildConfig.HAS_MAPS_KEY)) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(p.surf)
                    .border(1.dp, p.line, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    T(kmh.toString(), 30.sp, FontWeight.ExtraBold, if (speeding) p.voice else p.text)
                    Muted("km/h", 11.sp)
                }
            }
        }
    }
}

// ---------------- cruscotto del guidatore ----------------

@Composable
fun DriverDashboard(s: AppSettings, night: Boolean) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf(Panel.MUSIC) }
    var split by remember(s.split) { mutableFloatStateOf(s.split) }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val landscape = maxWidth > maxHeight
        val tablet = minOf(maxWidth, maxHeight) >= 600.dp
        val panelContent: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 2.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Header(s, night, tablet)
                StatusCards(s)
                when (panel) {
                    Panel.MUSIC -> MusicCard(if (tablet) 1.25f else 1f)
                    Panel.RADIO -> RadioPanel(s)
                    Panel.MESSAGES -> MessagesPanel()
                }
                if (tablet) Muted("Video disponibili solo ad auto ferma, nella modalità passeggero")
            }
        }
        if (landscape) {
            val railW = if (tablet) 88.dp else 68.dp
            val avail = maxWidth - railW - 12.dp
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(railW).fillMaxHeight().background(p.surf)) {
                    AppBarItems(s.bar, panel, { panel = it }, vertical = true, size = if (tablet) 56.dp else 48.dp)
                }
                MapArea(night, Modifier.width(avail * split).fillMaxHeight())
                DragHandle(vertical = true, onDrag = { d -> split = (split + d / avail.value).coerceIn(0.35f, 0.75f) }) {
                    scope.launch { Prefs.edit(context) { it[Keys.split] = split } }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { panelContent() }
            }
        } else {
            val avail = maxHeight - 90.dp
            Column(Modifier.fillMaxSize()) {
                MapArea(night, Modifier.fillMaxWidth().height(avail * split))
                DragHandle(vertical = false, onDrag = { d -> split = (split + d / avail.value).coerceIn(0.35f, 0.75f) }) {
                    scope.launch { Prefs.edit(context) { it[Keys.split] = split } }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp)) { panelContent() }
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(22.dp)).background(p.surf)) {
                    AppBarItems(s.bar, panel, { panel = it }, vertical = false, size = 50.dp)
                }
            }
        }
    }
}

@Composable
fun DragHandle(vertical: Boolean, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val p = LocalPal.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val m = if (vertical) Modifier.width(12.dp).fillMaxHeight() else Modifier.height(16.dp).fillMaxWidth()
    Box(
        m.pointerInput(Unit) {
            detectDragGestures(onDragEnd = onEnd) { change, amount ->
                change.consume()
                val px = if (vertical) amount.x else amount.y
                onDrag(with(density) { px.toDp().value })
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (vertical) 4.dp else 44.dp, if (vertical) 44.dp else 4.dp).clip(RoundedCornerShape(2.dp)).background(p.line))
    }
}

// ---------------- passeggero ----------------

fun audioOutputLabel(context: Context): String {
    val am = context.getSystemService(AudioManager::class.java) ?: return "Altoparlante"
    val bt = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }
    return if (bt) "Audio sulle cuffie Bluetooth" else "Audio sull'altoparlante"
}

fun connectionLabel(context: Context, pref: String): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return ""
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    return when {
        pref == "sim" || (pref == "auto" && cell) -> "Rete: SIM o eSIM del dispositivo"
        pref == "hotspot" || (pref == "auto" && wifi) -> "Rete: hotspot del telefono o Wi-Fi"
        caps == null -> "Nessuna connessione"
        else -> "Connesso"
    }
}

@Composable
fun PassengerScreen(s: AppSettings, night: Boolean) {
    val context = LocalContext.current
    val p = LocalPal.current
    val voice by Hub.voice.collectAsState()
    val weather by Hub.weather.collectAsState()
    val nav by Hub.nav.collectAsState()
    val scope = rememberCoroutineScope()
    var audio by remember { mutableStateOf(audioOutputLabel(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            audio = audioOutputLabel(context)
            kotlinx.coroutines.delay(5000)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg).padding(20.dp)) {
        val wide = maxWidth > 700.dp
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(bg = p.voice) { T("Passeggero", 14.sp, FontWeight.ExtraBold, p.onVoice) }
                Pill {
                    Icon(Ic.head, null, tint = p.acc, modifier = Modifier.size(18.dp))
                    T(audio, 13.sp, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                VoicePill(voice.phase, onTap = { VoiceService.listenNow(context) }, fontSize = 13.sp)
                if (s.weatherOn && weather != null) Pill {
                    Icon(Ic.cloud, null, tint = p.acc, modifier = Modifier.size(18.dp))
                    T("${weather!!.tempC.toInt()}° esterni", 13.sp, FontWeight.Bold)
                }
                ThemePill(night, s.themeMode == "auto") {
                    val next = when (s.themeMode) { "auto" -> "night"; "night" -> "day"; else -> "auto" }
                    scope.launch { Prefs.edit(context) { it[Keys.themeMode] = next } }
                }
                Clock(20.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    T("Cosa guardiamo?", 30.sp, FontWeight.ExtraBold)
                    val keys = listOf("cl", "yt", "wz", "tm", "sp", "gm")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (wide) 3 else 2),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.height(if (wide) 420.dp else 560.dp),
                    ) {
                        items(Apps.VIDEO.withIndex().toList()) { (i, name) ->
                            val app = remember(name) { Apps.findByName(context, name) }
                            val (bg, fg) = p.tints.getValue(keys[i % keys.size])
                            Column(
                                Modifier.height(190.dp).clip(RoundedCornerShape(24.dp)).background(bg)
                                    .clickable(role = Role.Button) { app?.let { Apps.launch(context, it.packageName) } }
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                T(name, 28.sp, FontWeight.ExtraBold, fg)
                                T(if (app == null) "Non installata" else if (name == "Netflix" || name == "Prime Video") "Ricerca vocale del titolo" else "Apertura e comandi a voce", 13.sp, FontWeight.SemiBold, fg)
                            }
                        }
                    }
                    Muted("Di' «Ehi Tachi, apri Netflix», «cerca Stranger Things su Netflix», «pausa», «alza il volume»", 15.sp)
                }
                if (wide) Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardBox(pad = 18.dp, gap = 4.dp) {
                        if (nav.active) {
                            Muted("Verso ${nav.destination}", 14.sp)
                            T(if (nav.etaText.isBlank()) "In viaggio" else "Arrivo ${nav.etaText}", 32.sp, FontWeight.ExtraBold, p.route)
                            Muted(nav.remainingText, 15.sp)
                        } else {
                            Muted("Fuori", 14.sp)
                            T(weather?.let { "${it.tempC.toInt()}° · ${it.description}" } ?: "Meteo in arrivo", 28.sp, FontWeight.ExtraBold)
                            Muted(connectionLabel(context, s.tabletConnection), 14.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { RoundBtn(Ic.volDown, "Abbassa il volume", 56.dp) { MediaSessions.volume(context, false) } }
                        Box(Modifier.weight(1f)) { RoundBtn(Ic.pause, "Pausa o riprendi", 56.dp, strong = true) { MediaSessions.playPause(context) } }
                        Box(Modifier.weight(1f)) { RoundBtn(Ic.volUp, "Alza il volume", 56.dp) { MediaSessions.volume(context, true) } }
                    }
                    Btn("Passa a modalità guidatore", modifier = Modifier.fillMaxWidth(), height = 56.dp) { Hub.send(UiAction.SetMode(true)) }
                    Btn("Impostazioni", modifier = Modifier.fillMaxWidth(), height = 48.dp, icon = Ic.gear) { Hub.screen.value = Screen.SETTINGS }
                }
            }
            if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Modalità guidatore", modifier = Modifier.weight(1f)) { Hub.send(UiAction.SetMode(true)) }
                Btn("Impostazioni", modifier = Modifier.weight(1f), icon = Ic.gear) { Hub.screen.value = Screen.SETTINGS }
            }
        }
    }
}

// ---------------- in attesa dell'auto ----------------

@Composable
fun WaitingForCar(gate: it.giacomo.tachimetro.CarGate, carName: String) {
    val p = LocalPal.current
    val (title, text) = when (gate) {
        it.giacomo.tachimetro.CarGate.NOT_SET -> "Scegli la tua auto" to "Nelle impostazioni, alla voce Auto, tocca il Bluetooth della tua auto. Tachimetro si attiverà solo quando il telefono è collegato."
        it.giacomo.tachimetro.CarGate.NO_PERMISSION -> "Serve il permesso Bluetooth" to "Senza il permesso Bluetooth Tachimetro non può sapere se sei in auto. Concedilo nelle impostazioni, alla voce Permessi."
        it.giacomo.tachimetro.CarGate.CHECKING -> "Controllo il collegamento…" to "Un attimo."
        else -> "Collega il Bluetooth dell'auto" to "Tachimetro si attiva quando il telefono è collegato a ${carName.ifBlank { "la tua auto" }}. Finché non lo è, microfono, GPS e servizi restano spenti."
    }
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(28.dp)) {
            Icon(Ic.car, null, tint = p.acc, modifier = Modifier.size(56.dp))
            T("Tachimetro", 15.sp, FontWeight.Bold, p.acc)
            T(title, 26.sp, FontWeight.ExtraBold)
            Muted(text, 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Impostazioni", gate == it.giacomo.tachimetro.CarGate.NOT_SET || gate == it.giacomo.tachimetro.CarGate.NO_PERMISSION, icon = Ic.gear) { Hub.screen.value = Screen.SETTINGS }
                Btn("Modalità passeggero") { Hub.send(UiAction.SetMode(false)) }
                Btn("Chiudi", icon = Ic.power) { Hub.send(UiAction.Close) }
            }
        }
    }
}

// ---------------- scelta della modalità all'avvio ----------------

@Composable
fun ModeChooser(onChoose: (Boolean) -> Unit) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
            T("Tachimetro", 15.sp, FontWeight.Bold, p.acc)
            T("Chi usa questo schermo?", 28.sp, FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn("Guidatore", true, Modifier.width(220.dp), 64.dp, Ic.nav) { onChoose(true) }
                Btn("Passeggero", false, Modifier.width(220.dp), 64.dp, Ic.play) { onChoose(false) }
            }
            Muted("Puoi cambiare a voce: «Ehi Tachi, modalità passeggero»")
        }
    }
}

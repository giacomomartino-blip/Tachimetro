package it.giacomo.tachimetro.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.Screen
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.media.Apps
import it.giacomo.tachimetro.notify.NotificationReader
import kotlinx.coroutines.launch

@Composable
fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    val p = LocalPal.current
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.text,
            unfocusedTextColor = p.text,
            focusedBorderColor = p.acc,
            unfocusedBorderColor = p.line,
            focusedLabelColor = p.acc,
            unfocusedLabelColor = p.muted,
            cursorColor = p.acc,
        ),
    )
}

@Composable
fun Row2(k: String, v: String, strong: Boolean = false) {
    val p = LocalPal.current
    Column {
        Muted(k)
        T(v, 14.sp, if (strong) FontWeight.ExtraBold else FontWeight.SemiBold, if (strong) p.acc else p.text)
    }
}

private fun granted(c: Context, perm: String) = c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(s: AppSettings, onAskPermissions: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    fun set(block: (MutablePreferences) -> Unit) {
        scope.launch { Prefs.edit(context, block) }
    }
    var importText by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Tachimetro", 13.sp, FontWeight.Bold, p.acc)
                T("Impostazioni", 28.sp, FontWeight.ExtraBold)
            }
            Btn("Torna al cruscotto") { Hub.screen.value = if (s.mode == "passenger") Screen.PASSENGER else Screen.DASHBOARD }
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalItemSpacing = 14.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                if (refresh < 0) return@item
                val mic = granted(context, Manifest.permission.RECORD_AUDIO)
                val loc = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                val notif = NotificationReader.connected
                val overlay = Settings.canDrawOverlays(context)
                Section("Permessi") {
                    Muted("Servono perché Tachimetro funzioni a voce. Tutto resta su questo dispositivo.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(if (mic && loc) "Microfono, posizione e altri: concessi" else "Concedi microfono, posizione, contatti, calendario", !(mic && loc)) {
                            onAskPermissions()
                            refresh++
                        }
                        Btn(if (notif) "Lettura notifiche: attiva" else "Attiva lettura notifiche e musica", !notif) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        Btn(if (overlay) "Apertura automatica: consentita" else "Consenti apertura automatica", !overlay) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        Btn("Non limitare la batteria") {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
            item {
                Section("Aspetto") {
                    Option("Automatico", s.themeMode == "auto") { set { it[Keys.themeMode] = "auto" } }
                    Option("Sempre giorno", s.themeMode == "day") { set { it[Keys.themeMode] = "day" } }
                    Option("Sempre notte", s.themeMode == "night") { set { it[Keys.themeMode] = "night" } }
                    Muted("Automatico: notte dal tramonto all'alba e quando il sensore rileva poca luce, per esempio in galleria")
                }
            }
            item {
                Section("Modalità") {
                    Option("Chiedi all'avvio", s.startMode == "ask") { set { it[Keys.startMode] = "ask" } }
                    Option("Sempre guidatore", s.startMode == "driver") { set { it[Keys.startMode] = "driver" } }
                    Option("Sempre passeggero", s.startMode == "passenger") { set { it[Keys.startMode] = "passenger" } }
                    Muted("A voce: «modalità passeggero» o «modalità guidatore»")
                }
            }
            item {
                Section("Voce") {
                    Row2("Parola chiave", "Ehi Tachi", true)
                    Toggle("Leggi WhatsApp, SMS e Teams", s.readMessages) { v -> set { it[Keys.readMessages] = v } }
                    Field("Chiave di Gemini (facoltativa)", s.geminiKey, secret = true) { v -> set { it[Keys.geminiKey] = v.trim() } }
                    Muted("Con la chiave, le domande generali ricevono una risposta letta ad alta voce. Senza chiave si apre l'assistente del telefono. Gemini riceve solo il testo della domanda.")
                }
            }
            item {
                Section("Navigazione") {
                    Row2("Predefinito", "Google Maps", true)
                    Row2("Waze", "Solo su richiesta vocale")
                    Field("Indirizzo di casa", s.home) { v -> set { it[Keys.home] = v } }
                    Field("Indirizzo del lavoro", s.work) { v -> set { it[Keys.work] = v } }
                    Muted("Carburante")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Benzina", "Gasolio", "GPL", "Metano").forEach { f ->
                            Chip(f, s.fuelType == f) { set { it[Keys.fuelType] = f } }
                        }
                    }
                }
            }
            item {
                Section("Velocità e limiti") {
                    Muted("Avviso quando superi il limite")
                    Option("Tono breve e discreto", s.speedAlert == "tone") { set { it[Keys.speedAlert] = "tone" } }
                    Option("Solo visivo", s.speedAlert == "visual") { set { it[Keys.speedAlert] = "visual" } }
                    Option("Nessun avviso", s.speedAlert == "none") { set { it[Keys.speedAlert] = "none" } }
                    Muted("Tolleranza")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 10).forEach { t -> Chip("+$t km/h", s.tolerance == t) { set { it[Keys.tolerance] = t } } }
                    }
                    Muted("Limite e velocità compaiono sulla mappa durante la navigazione con Google Maps")
                }
            }
            item {
                Section("Autovelox") {
                    Toggle("Autovelox fissi", s.cameras) { v -> set { it[Keys.cameras] = v } }
                    Toggle("Controlli mobili del giorno", s.mobileCameras) { v -> set { it[Keys.mobileCameras] = v } }
                    Muted("Preavviso")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(250, 400, 600).forEach { m -> Chip("$m m", s.cameraWarnMeters == m) { set { it[Keys.cameraWarnMeters] = m } } }
                    }
                    Muted("In questa versione i fissi arrivano da OpenStreetMap. L'elenco ufficiale MIT e i controlli mobili della Polizia Stradale arrivano con il prossimo aggiornamento.")
                }
            }
            item {
                Section("Musica e radio") {
                    Muted("App di musica predefinita")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Apps.MUSIC.forEach { (k, v) -> Chip(v.first, s.musicApp == k) { set { it[Keys.musicApp] = k } } }
                    }
                    Field("Regione per le radio regionali", s.radioRegion) { v -> set { it[Keys.radioRegion] = v.trim() } }
                    Row2("Audio del tablet passeggero", "Cuffie Bluetooth, riserva altoparlante")
                }
            }
            item {
                Section("Barra delle app") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.bar.filter { it.startsWith("app:") }.forEach { id ->
                            val label = Apps.label(context, id.removePrefix("app:"))
                            Mono(monoLabel(label), monoKey(id), 36.dp, 12.sp, 10.dp)
                        }
                    }
                    Muted("Scegli quali app mettere nella barra e in che ordine, per guidatore e passeggero")
                    Btn("Personalizza", true) { Hub.screen.value = Screen.APP_BAR }
                }
            }
            item {
                Section("Tablet") {
                    Muted("Connessione")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Option("Automatica", s.tabletConnection == "auto", Modifier.weight(1f)) { set { it[Keys.tabletConnection] = "auto" } }
                        Option("SIM o eSIM", s.tabletConnection == "sim", Modifier.weight(1f)) { set { it[Keys.tabletConnection] = "sim" } }
                        Option("Hotspot", s.tabletConnection == "hotspot", Modifier.weight(1f)) { set { it[Keys.tabletConnection] = "hotspot" } }
                    }
                    Muted(connectionLabel(context, s.tabletConnection))
                    Row2("Chiamate", "SIM o eSIM: deviazione · Hotspot: WhatsApp e Teams")
                    Row2("Posizione", "GPS del tablet se c'è")
                }
            }
            item {
                Section("Auto") {
                    Muted("Bluetooth dell'auto, per l'apertura automatica e il parcheggio")
                    val devices = remember(refresh) { bondedDevices(context) }
                    if (devices.isEmpty()) Muted("Nessun dispositivo Bluetooth associato, oppure manca il permesso")
                    devices.forEach { (name, addr) ->
                        Option(name, s.carBtAddress.equals(addr, true)) {
                            set {
                                it[Keys.carBtAddress] = addr
                                it[Keys.carBtName] = name
                            }
                        }
                    }
                    Toggle("Meteo e temperatura esterna", s.weatherOn) { v -> set { it[Keys.weatherOn] = v } }
                    Toggle("Apri quando entro in auto", s.autoStart) { v -> set { it[Keys.autoStart] = v } }
                    Toggle("Schermo intero, senza barre di Android", s.fullScreen) { v -> set { it[Keys.fullScreen] = v } }
                    Toggle("Schermo sempre acceso", s.keepScreenOn) { v -> set { it[Keys.keepScreenOn] = v } }
                    Toggle("Protezione anti burn-in", s.burnIn) { v -> set { it[Keys.burnIn] = v } }
                    Toggle("Salva dove parcheggi", s.saveParking) { v -> set { it[Keys.saveParking] = v } }
                    Toggle("Avvisi batteria e calore", s.healthAlerts) { v -> set { it[Keys.healthAlerts] = v } }
                }
            }
            item {
                Section("Privacy") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Ic.lock, null, tint = p.acc, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        T("Tutto resta su questo dispositivo", 13.sp, FontWeight.Bold)
                    }
                    Row2("Parola chiave", "Riconosciuta sul telefono, senza internet")
                    Row2("Notifiche", "Lette ad alta voce, mai salvate")
                    Row2("Gemini", "Riceve solo le domande generali")
                    Row2("Servizi esterni", "Meteo, prezzi carburanti, autovelox e radio ricevono solo la zona in cui ti trovi")
                }
            }
            item {
                Section("Dati") {
                    Muted("Indirizzi, barra e impostazioni restano su questo dispositivo. L'esportazione non include la chiave di Gemini.")
                    Btn("Esporta", true, Modifier.fillMaxWidth()) {
                        scope.launch {
                            val json = Prefs.exportJson(context)
                            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, json)
                            context.startActivity(Intent.createChooser(send, "Esporta impostazioni").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    Field("Incolla qui le impostazioni da importare", importText) { importText = it }
                    Btn("Importa", false, Modifier.fillMaxWidth()) {
                        scope.launch {
                            try {
                                Prefs.importJson(context, importText)
                                importText = ""
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bondedDevices(context: Context): List<Pair<String, String>> {
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
    return try {
        val bm = context.getSystemService(BluetoothManager::class.java)
        bm?.adapter?.bondedDevices?.map { (it.name ?: it.address) to it.address }?.sortedBy { it.first } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }
}

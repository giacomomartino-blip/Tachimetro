package it.giacomo.tachimetro.ui

import android.content.Intent
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.Screen
import it.giacomo.tachimetro.UiAction
import it.giacomo.tachimetro.car.Car
import it.giacomo.tachimetro.data.AppSettings
import it.giacomo.tachimetro.data.Fuel
import it.giacomo.tachimetro.data.FuelStation
import it.giacomo.tachimetro.data.Keys
import it.giacomo.tachimetro.data.Prefs
import it.giacomo.tachimetro.media.Apps
import it.giacomo.tachimetro.media.InstalledApp
import it.giacomo.tachimetro.nav.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun TopBar(title: String, subtitle: String? = null, back: Screen = Screen.DASHBOARD) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("Tachimetro", 13.sp, FontWeight.Bold, p.acc)
            T(title, 26.sp, FontWeight.ExtraBold)
            subtitle?.let { Muted(it, 13.sp) }
        }
        Btn("Indietro") { Hub.screen.value = back }
    }
}

// ---------------- barra delle app ----------------

@Composable
fun AppBarScreen(s: AppSettings) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var passenger by remember { mutableStateOf(s.mode == "passenger") }
    val current = if (passenger) s.barPassenger else s.bar
    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) { installed = withContext(Dispatchers.IO) { Apps.installed(context) } }
    fun save(list: List<String>) = scope.launch { Prefs.setBar(context, passenger, list) }
    val fixed = listOf("fn:home", "fn:phone", "fn:msg", "fn:music", "fn:radio")

    Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TopBar("Barra delle app", "Tocca le frecce per cambiare l'ordine. Anche a voce: «aggiungi Calendario alla barra»", if (s.mode == "passenger") Screen.PASSENGER else Screen.DASHBOARD)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Guidatore", !passenger) { passenger = false }
            Chip("Passeggero", passenger) { passenger = true }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { T("Nella barra", 16.sp, FontWeight.ExtraBold) }
                itemsIndexed(current) { i, id ->
                    val label = when (id) {
                        "fn:home" -> "Cruscotto"
                        "fn:phone" -> "Telefono"
                        "fn:msg" -> "Messaggi"
                        "fn:music" -> "Musica"
                        "fn:radio" -> "Radio"
                        else -> Apps.label(context, id.removePrefix("app:"))
                    }
                    CardBox(pad = 8.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (id.startsWith("app:")) Mono(monoLabel(label), monoKey(id), 44.dp, 14.sp, 12.dp)
                            else IconBox(
                                when (id) { "fn:home" -> Ic.home; "fn:phone" -> Ic.phone; "fn:msg" -> Ic.msg; "fn:music" -> Ic.music; else -> Ic.radio },
                                p.surf2, p.text, 44.dp,
                            )
                            T(label, 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                            RoundBtn(Ic.up, "Sposta $label in su", 44.dp) {
                                if (i > 0) save(current.toMutableList().apply { add(i - 1, removeAt(i)) })
                            }
                            RoundBtn(Ic.down, "Sposta $label in giù", 44.dp) {
                                if (i < current.size - 1) save(current.toMutableList().apply { add(i + 1, removeAt(i)) })
                            }
                            if (id !in fixed || current.count { it in fixed } > 1) {
                                RoundBtn(Ic.x, "Togli $label dalla barra", 44.dp) { save(current - id) }
                            }
                        }
                    }
                }
                item {
                    val missing = fixed.filter { it !in current }
                    if (missing.isNotEmpty()) {
                        Muted("Funzioni da rimettere")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            missing.forEach { id ->
                                Chip(when (id) { "fn:home" -> "Cruscotto"; "fn:phone" -> "Telefono"; "fn:msg" -> "Messaggi"; "fn:music" -> "Musica"; else -> "Radio" }, false) { save(current + id) }
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { T("Aggiungi", 16.sp, FontWeight.ExtraBold) }
                if (installed.isEmpty()) item { Muted("Carico le app installate…") }
                items(installed.filter { "app:" + it.packageName !in current }) { app ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(p.surf)
                            .clickable(role = Role.Button) { save(current + ("app:" + app.packageName)) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Mono(monoLabel(app.label), monoKey(app.packageName), 40.dp, 13.sp, 12.dp)
                        T(app.label, 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        IconBox(Ic.plus, p.acc, p.onAcc, 32.dp, 18.dp, 16.dp, "Aggiungi ${app.label}")
                    }
                }
            }
        }
    }
}

// ---------------- parcheggio ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParkingScreen(s: AppSettings) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val here by Hub.location.collectAsState()
    var address by remember(s.parkingLat, s.parkingLng) { mutableStateOf<String?>(null) }
    LaunchedEffect(s.parkingLat, s.parkingLng) {
        if (s.parkingTime == 0L) return@LaunchedEffect
        address = withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.ITALY).getFromLocation(s.parkingLat, s.parkingLng, 1)?.firstOrNull()?.let {
                    listOfNotNull(it.thoroughfare?.let { t -> t + (it.subThoroughfare?.let { n -> " $n" } ?: "") }, it.locality).joinToString(", ")
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    val back = if (s.mode == "passenger") Screen.PASSENGER else Screen.DASHBOARD
    Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TopBar(if (s.parkingTime == 0L) "Nessun parcheggio salvato" else "La tua auto è qui", back = back)
        if (s.parkingTime == 0L) {
            Muted("Scegli il Bluetooth dell'auto nelle impostazioni: quando il telefono si scollega, Tachimetro salva la posizione da solo. Oppure salvala adesso.", 14.sp)
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(s.parkingTime))
            var dist = ""
            here?.let {
                val d = FloatArray(1)
                android.location.Location.distanceBetween(it.latitude, it.longitude, s.parkingLat, s.parkingLng, d)
                dist = if (d[0] < 1000) " · ${(d[0] / 10).toInt() * 10} m, circa ${(d[0] / 80).toInt().coerceAtLeast(1)} min a piedi" else String.format(Locale.ITALIAN, " · %.1f km", d[0] / 1000)
            }
            CardBox(pad = 16.dp, gap = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBox(Ic.pin, p.acc, p.onAcc, 52.dp, 28.dp, 26.dp)
                    Column(Modifier.weight(1f)) {
                        T(address ?: "Posizione salvata", 17.sp, FontWeight.ExtraBold)
                        Muted("Salvata alle $time$dist", 13.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Portami a piedi", true, Modifier.weight(1f), 52.dp, Ic.nav) { NavController.openGoogleMaps(context, s.parkingLat, s.parkingLng, true) }
                Btn("Condividi", false, Modifier.weight(1f), 52.dp) {
                    val url = "https://maps.google.com/?q=${s.parkingLat},${s.parkingLng}"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "La macchina è qui: $url")
                    context.startActivity(Intent.createChooser(send, "Condividi il parcheggio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            CardBox(pad = 14.dp, gap = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Ic.clock, null, tint = p.voice, modifier = Modifier.size(22.dp))
                    T("Promemoria strisce blu", 15.sp, FontWeight.ExtraBold)
                }
                if (s.parkingReminderAt > System.currentTimeMillis()) {
                    val at = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(s.parkingReminderAt))
                    Muted("Il parcheggio scade alle $at, ti avviso 10 minuti prima")
                    Btn("Annulla promemoria") { Car.cancelReminder(context) }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30 to "30 min", 60 to "1 ora", 120 to "2 ore", 180 to "3 ore").forEach { (m, l) ->
                            Chip(l, false) { Car.setReminder(context, m) }
                        }
                    }
                    Muted("Ti avviso 10 minuti prima della scadenza")
                }
            }
        }
        Btn("Salva qui adesso", false, Modifier.fillMaxWidth()) {
            here?.let { loc -> scope.launch { Car.saveParking(context, loc) } }
        }
        Pill { T("«Ehi Tachi, dov'è la macchina?»", 13.sp) }
    }
}

// ---------------- distributori ----------------

@Composable
fun FuelScreen(s: AppSettings) {
    val context = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val here by Hub.location.collectAsState()
    val list by Hub.fuel.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var type by remember(s.fuelType) { mutableStateOf(s.fuelType) }

    fun load() {
        val loc = here ?: run { error = "Aspetto la posizione…"; return }
        loading = true
        error = null
        scope.launch {
            try {
                Hub.fuel.value = Fuel.nearby(loc, type)
                if (Hub.fuel.value.isEmpty()) error = "Nessun distributore trovato entro 10 km"
            } catch (e: Exception) {
                error = "Prezzi non raggiungibili in questo momento"
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(type, here != null) { if (here != null) load() }

    Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar("Distributori più economici", "Prezzi comunicati dai gestori al Ministero, aggiornati ogni mattina")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Benzina", "Gasolio", "GPL", "Metano").forEach { f ->
                Chip(if (f == "Benzina" || f == "Gasolio") "$f self" else f, type == f) {
                    type = f
                    scope.launch { Prefs.edit(context) { it[Keys.fuelType] = f } }
                }
            }
        }
        if (loading) Muted("Scarico i prezzi di oggi…", 14.sp)
        error?.let { Muted(it, 14.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(list) { i, st -> FuelRow(st, i == 0) }
            if (list.isNotEmpty()) item { Muted("Di' «Ehi Tachi, portami al primo»") }
        }
    }
}

@Composable
private fun FuelRow(st: FuelStation, best: Boolean) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp)).background(p.surf)
            .clickable(role = Role.Button) {
                Hub.send(UiAction.NavigateCoords(st.lat, st.lng, st.name, false))
                Hub.screen.value = Screen.DASHBOARD
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(Ic.fuel, if (best) p.acc else p.surf2, if (best) p.onAcc else p.text, 40.dp, 20.dp, 12.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(st.name, 14.sp, FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (best) Pill(bg = p.acc) { T("Più economico", 11.sp, FontWeight.ExtraBold, p.onAcc) }
            }
            Muted(String.format(Locale.ITALIAN, "%.1f km · %s", st.distanceKm, st.address), 12.sp, maxLines = 1)
        }
        T(String.format(Locale.ITALIAN, "%.3f €", st.price), 17.sp, FontWeight.ExtraBold)
    }
}

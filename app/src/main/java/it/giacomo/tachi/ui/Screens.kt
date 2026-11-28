package it.giacomo.tachi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giacomo.tachi.R
import it.giacomo.tachi.car.DeviceKind
import it.giacomo.tachi.car.Paired
import it.giacomo.tachi.data.Car

/** Contenitore comune: fondo scuro, margini, spazio per il ritaglio della fotocamera. */
@Composable
fun ScreenBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Col.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) { content() }
}

// ---------------- 1. Permessi ----------------

@Composable
fun PermissionsScreen(hasBt: Boolean, hasNotif: Boolean, onBt: () -> Unit, onNotif: () -> Unit, onNext: () -> Unit) {
    ScreenBox {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(290.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LogoRow()
                    Text("Benvenuto. Due permessi e sei pronto.", style = Type.title)
                    Text("Servono solo per riconoscere la tua auto e per tenere l'app attiva mentre guidi. Nient'altro.", style = Type.body)
                }
                StepDots(1, 2)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PermissionCard(R.drawable.ic_bluetooth, "Dispositivi nelle vicinanze", "Per vedere quando il telefono si collega all'auto.", hasBt, onBt)
                PermissionCard(R.drawable.ic_bell, "Notifiche", "Per la notifica «Tachimetro attivo» con il pulsante Chiudi.", hasNotif, onNotif)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!hasBt) Text("Serve almeno il primo permesso.", style = Type.itemSub, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    PrimaryButton("Avanti", enabled = hasBt, onClick = onNext)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(icon: Int, title: String, sub: String, granted: Boolean, onAsk: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Ico(icon, Col.blue, 30.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.itemTitle.copy(fontSize = 17.sp))
                Text(sub, style = Type.itemSub.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 3.dp))
            }
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.ic_check, Col.green, 20.dp)
                    Text("Concesso", style = Type.button.copy(color = Col.green, fontSize = 15.sp))
                }
            } else {
                OutlineButton("Consenti", minHeight = 48.dp, onClick = onAsk)
            }
        }
    }
}

// ---------------- 2. Scelta dell'auto ----------------

@Composable
fun CarPickerScreen(
    devices: List<Paired>,
    already: Set<String>,
    selected: String?,
    onboarding: Boolean,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenBluetooth: () -> Unit,
) {
    ScreenBox {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LogoRow()
                    Text(if (onboarding) "Qual è la tua auto?" else "Aggiungi un'auto", style = Type.title)
                    Text(
                        if (onboarding) "Scegli il Bluetooth dell'auto tra i dispositivi già associati al telefono. Potrai aggiungere altre auto dalle Impostazioni."
                        else "Scegli il Bluetooth dell'auto tra i dispositivi già associati al telefono.",
                        style = Type.body,
                    )
                }
                if (onboarding) StepDots(2, 2)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DISPOSITIVI ASSOCIATI", style = Type.label)
                if (devices.isEmpty()) {
                    Panel(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Nessun dispositivo associato", style = Type.itemTitle)
                            Text("Associa prima il telefono all'auto dalle impostazioni Bluetooth, poi torna qui.", style = Type.body)
                            OutlineButton("Apri impostazioni Bluetooth", onClick = onOpenBluetooth)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(devices, key = { it.address }) { d ->
                            DeviceRow(d, selected == d.address, d.address in already) { onSelect(d.address) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Indietro", style = Type.button.copy(color = Col.muted)) }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(if (onboarding) "Salva e continua" else "Aggiungi", enabled = selected != null, onClick = onSave)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: Paired, selected: Boolean, already: Boolean, onClick: () -> Unit) {
    val icon = when (d.kind) {
        DeviceKind.CAR -> R.drawable.ic_car
        DeviceKind.HEADPHONES -> R.drawable.ic_headphones
        DeviceKind.OTHER -> R.drawable.ic_device
    }
    val sub = when {
        already -> "Già tra le tue auto"
        d.kind == DeviceKind.CAR -> "Autoradio, vivavoce"
        d.kind == DeviceKind.HEADPHONES -> "Cuffie"
        else -> "Altro dispositivo"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Col.amberSoft else Col.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Col.amber else Col.border), RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Ico(icon, if (d.kind == DeviceKind.CAR) Col.blue else Color(0xFF7C8796), 26.dp)
        Column(Modifier.weight(1f)) {
            Text(d.name, style = Type.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = Type.itemSub)
        }
        if (selected) Ico(R.drawable.ic_check_circle, Col.amber, 24.dp)
        else Box(Modifier.size(22.dp).border(2.dp, Col.faint, CircleShape))
    }
}

// ---------------- 3. In attesa dell'auto ----------------

@Composable
fun WaitingScreen(
    btOn: Boolean,
    carNames: List<String>,
    onEnableBt: () -> Unit,
    onAddCar: () -> Unit,
    onTest: () -> Unit,
    onSettings: () -> Unit,
    onStatus: () -> Unit,
    onClose: () -> Unit,
) {
    ScreenBox {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LogoRow()
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Box(
                    Modifier.size(150.dp).clip(CircleShape).background(Color(0xFF121A26)).border(2.dp, Color(0xFF23344D), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Ico(R.drawable.ic_bluetooth, if (btOn) Col.blue else Col.faint, 70.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        !btOn -> {
                            Text("Il Bluetooth è spento", style = Type.title.copy(fontSize = 32.sp))
                            Text("Accendilo per far riconoscere la tua auto.", style = Type.body.copy(fontSize = 16.sp))
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PrimaryButton("Accendi il Bluetooth", onClick = onEnableBt)
                                OutlineButton("Prova senza auto", onClick = onTest)
                            }
                        }
                        carNames.isEmpty() -> {
                            Text("Nessuna auto scelta", style = Type.title.copy(fontSize = 32.sp))
                            Text("Scegli il Bluetooth della tua auto per far attivare Tachimetro da solo.", style = Type.body.copy(fontSize = 16.sp))
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PrimaryButton("Scegli l'auto", onClick = onAddCar)
                                OutlineButton("Prova senza auto", onClick = onTest)
                            }
                        }
                        else -> {
                            Text("In attesa della tua auto", style = Type.title.copy(fontSize = 32.sp))
                            Text(
                                "Accendi l'auto: Tachimetro si attiva da solo appena il telefono si collega a ${carNames.joinToString(" o ")}.",
                                style = Type.body.copy(fontSize = 16.sp),
                            )
                            Row(Modifier.padding(top = 6.dp)) { OutlineButton("Prova senza auto", onClick = onTest) }
                        }
                    }
                }
            }
            BottomButtons(onSettings, onStatus, onClose)
        }
    }
}

@Composable
private fun BottomButtons(onSettings: () -> Unit, onStatus: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TileButton("Impostazioni", R.drawable.ic_gear, Modifier.weight(1f), onClick = onSettings)
        TileButton("Stato", R.drawable.ic_pulse, Modifier.weight(1f), onClick = onStatus)
        TileButton("Chiudi", R.drawable.ic_power, Modifier.weight(1f), iconTint = Col.red, onClick = onClose)
    }
}

// ---------------- 4. Auto collegata ----------------

@Composable
fun ActiveScreen(carName: String?, test: Boolean, onSettings: () -> Unit, onStatus: () -> Unit, onClose: () -> Unit) {
    ScreenBox {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LogoRow()
                Spacer(Modifier.weight(1f))
                Text("Modalità guidatore", style = Type.itemSub.copy(fontSize = 14.sp))
                Spacer(Modifier.width(16.dp))
                Row(
                    Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (test) Col.amberSoft else Col.greenBg)
                        .border(1.dp, if (test) Col.warnBorder else Col.greenBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (test) Col.amber else Col.green))
                    Text(
                        if (test) "Prova senza auto" else "Collegato a ${carName.orEmpty()}",
                        style = Type.button.copy(color = if (test) Col.warnText else Col.greenText, fontSize = 15.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = Color(0xFF2C333E),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                        Ico(R.drawable.ic_dashboard, Col.faint, 44.dp)
                        Text("Qui arriverà il cruscotto", style = Type.titleSmall.copy(fontSize = 20.sp, color = Color(0xFFD5DAE1)))
                        Text(
                            "Modulo 2: mappa, velocità, musica e messaggi. In questo modulo l'app è già attiva e pronta.",
                            style = Type.itemSub.copy(fontSize = 14.sp), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp),
                        )
                    }
                }
                Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileButton("Impostazioni", R.drawable.ic_gear, Modifier.fillMaxWidth(), minHeight = 64.dp, center = false, onClick = onSettings)
                    TileButton("Stato", R.drawable.ic_pulse, Modifier.fillMaxWidth(), minHeight = 64.dp, center = false, onClick = onStatus)
                    TileButton("Chiudi", R.drawable.ic_power, Modifier.fillMaxWidth(), iconTint = Col.red, minHeight = 64.dp, center = false, onClick = onClose)
                }
            }
        }
    }
}

// ---------------- 5. Auto scollegata ----------------

@Composable
fun LeavingScreen(seconds: Int, onStay: () -> Unit, onCloseNow: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF07090C)).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 20.dp)) {
        LogoRow()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Col.surface)
                    .border(1.dp, Color(0xFF3A2F18), RoundedCornerShape(22.dp))
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 10.dp.toPx()
                        val inset = w / 2
                        val arcSize = Size(size.width - w, size.height - w)
                        drawArc(Color(0xFF2C333E), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                        drawArc(Col.amber, -90f, 360f * seconds / 60f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$seconds", style = Type.title.copy(fontSize = 44.sp, lineHeight = 46.sp))
                        Text("secondi", style = Type.itemSub)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Auto scollegata", style = Type.title.copy(fontSize = 28.sp))
                    Text("Tachimetro si chiude tra $seconds secondi. Se l'auto si ricollega prima, il conto si annulla da solo.", style = Type.body)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Resto qui", Modifier.weight(1f), onClick = onStay)
                        OutlineButton("Chiudi ora", Modifier.weight(1f), color = Col.text, borderColor = Col.borderStrong, minHeight = 56.dp, onClick = onCloseNow)
                    }
                }
            }
        }
    }
}

// ---------------- 6. Stato ----------------

data class StatusRow(val title: String, val detail: String, val ok: Boolean)

@Composable
fun StatusScreen(rows: List<StatusRow>, summary: String, onBack: () -> Unit) {
    ScreenBox {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header("Stato", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { r -> StatusCell(r, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text(summary, style = Type.body.copy(fontSize = 14.sp))
            }
        }
    }
}

@Composable
private fun StatusCell(r: StatusRow, modifier: Modifier) {
    Panel(modifier, borderColor = if (r.ok) Col.border else Col.warnBorder) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(if (r.ok) R.drawable.ic_check_circle else R.drawable.ic_alert_circle, if (r.ok) Col.green else Col.amber, 22.dp)
            Column {
                Text(r.title, style = Type.itemTitle.copy(fontSize = 15.sp))
                Text(r.detail, style = Type.itemSub.copy(color = if (r.ok) Col.muted else Col.warnText), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LogoRow()
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFF2C333E)))
        Spacer(Modifier.width(18.dp))
        Text(title, style = Type.titleSmall)
        Spacer(Modifier.weight(1f))
        OutlineButton("Indietro", color = Col.text, borderColor = Col.borderStrong, minHeight = 44.dp, onClick = onBack)
    }
}

// ---------------- 7. Impostazioni ----------------

@Composable
fun SettingsScreen(cars: List<Car>, version: String, onRemove: (Car) -> Unit, onAdd: () -> Unit, onOpenSystem: () -> Unit, onBack: () -> Unit) {
    ScreenBox {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header("Impostazioni", onBack)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(Modifier.weight(1.3f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("LE TUE AUTO", style = Type.label)
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (cars.isEmpty()) Text("Nessuna auto scelta.", style = Type.body)
                            cars.forEach { car ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Col.surfaceLow).padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Ico(R.drawable.ic_car, Col.blue, 24.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(car.name, style = Type.itemTitle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Si attiva quando il telefono si collega", style = Type.itemSub)
                                    }
                                    OutlineButton("Rimuovi", color = Color(0xFFFF9A92), borderColor = Col.redBorder, minHeight = 44.dp) { onRemove(car) }
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .drawDashedBorder()
                                .clickable(role = Role.Button, onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) { Text("Aggiungi un'auto", style = Type.button.copy(color = Col.amber, fontSize = 15.sp)) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("PERMESSI", style = Type.label)
                            Text("Bluetooth e notifiche si gestiscono dalle impostazioni del telefono.", style = Type.body.copy(fontSize = 14.sp))
                            OutlineButton("Apri impostazioni del telefono", Modifier.fillMaxWidth(), color = Col.text, borderColor = Col.borderStrong, minHeight = 46.dp, onClick = onOpenSystem)
                        }
                    }
                    Panel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("INFORMAZIONI", style = Type.label)
                            Text("Tachimetro, versione $version · modulo Base", style = Type.body.copy(fontSize = 14.sp))
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.drawDashedBorder(): Modifier = this.drawBehind {
    drawRoundRect(
        color = Col.amber,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
    )
}

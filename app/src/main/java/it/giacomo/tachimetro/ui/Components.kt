package it.giacomo.tachimetro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.VoicePhase
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPal.current.text,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier = modifier, fontSize = size, fontWeight = weight, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = size * 1.3f)
}

@Composable
fun Muted(text: String, size: TextUnit = 12.sp, maxLines: Int = Int.MAX_VALUE) = T(text, size, color = LocalPal.current.muted, maxLines = maxLines)

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    pad: Dp = 12.dp,
    gap: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPal.current
    var m = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(p.surf)
        .border(BorderStroke(1.dp, p.line), RoundedCornerShape(20.dp))
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(m.padding(pad), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

@Composable
fun IconBox(icon: ImageVector, bg: Color, fg: Color, size: Dp = 44.dp, iconSize: Dp = 22.dp, radius: Dp = 14.dp, desc: String? = null) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = fg, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Mono(label: String, key: String, size: Dp = 44.dp, fontSize: TextUnit = 15.sp, radius: Dp = 14.dp) {
    val p = LocalPal.current
    val (bg, fg) = p.tints[key] ?: (p.surf2 to p.text)
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        T(label, fontSize, FontWeight.ExtraBold, fg)
    }
}

@Composable
fun Btn(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val p = LocalPal.current
    val bg = if (primary) p.acc else p.surf2
    val fg = if (primary) p.onAcc else p.text
    Row(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        T(text, 14.sp, FontWeight.Bold, fg)
    }
}

@Composable
fun RoundBtn(icon: ImageVector, desc: String, size: Dp = 48.dp, strong: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (strong) p.strong else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (strong) p.onStrong else p.text, modifier = Modifier.size(if (size > 56.dp) 28.dp else 24.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) p.acc else p.surf2)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 13.sp, FontWeight.Bold, if (selected) p.onAcc else p.text)
    }
}

@Composable
fun Option(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surf2)
            .border(2.dp, if (selected) p.acc else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        T(text, 13.sp, FontWeight.Bold)
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        T(label, 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (checked) p.acc else p.surf2).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (checked) p.onAcc else p.muted))
        }
    }
}

@Composable
fun Pill(modifier: Modifier = Modifier, bg: Color = LocalPal.current.surf2, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    var m = modifier.clip(RoundedCornerShape(999.dp)).background(bg)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Row(m.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
fun VoicePill(phase: VoicePhase, onTap: () -> Unit, fontSize: TextUnit = 12.sp) {
    val p = LocalPal.current
    val active = phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING
    val text = when (phase) {
        VoicePhase.OFF -> "Voce spenta"
        VoicePhase.LOADING -> "Preparo la voce…"
        VoicePhase.WAITING_WAKE -> "In ascolto: «Ehi Tachi»"
        VoicePhase.LISTENING -> "Ti ascolto"
        VoicePhase.THINKING -> "Un attimo…"
        VoicePhase.SPEAKING -> "Sto parlando"
    }
    Pill(bg = if (active) p.voice else p.surf2, onClick = onTap) {
        if (active) {
            Icon(Ic.mic, null, tint = p.onVoice, modifier = Modifier.size(14.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (phase == VoicePhase.OFF) p.muted else p.voice))
        }
        T(text, fontSize, FontWeight.SemiBold, if (active) p.onVoice else p.text, maxLines = 1)
    }
}

@Composable
fun Clock(fontSize: TextUnit = 17.sp) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(10_000)
        }
    }
    T(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize, FontWeight.ExtraBold)
}

@Composable
fun ThemePill(night: Boolean, auto: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Pill(onClick = onClick) {
        Icon(if (night) Ic.moon else Ic.sun, null, tint = if (night) p.acc else p.voice, modifier = Modifier.size(14.dp))
        T(if (auto) "Auto" else if (night) "Notte" else "Giorno", 12.sp, FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SpeedSign(limit: Int, size: Dp = 46.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White).border(size / 9, Color(0xFFD7262E), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        T(limit.toString(), (size.value * 0.36f).sp, FontWeight.ExtraBold, Color(0xFF111111))
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CardBox(modifier, pad = 16.dp, gap = 10.dp) {
        T(title, 15.sp, FontWeight.ExtraBold)
        content()
    }
}

val SmallPad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

fun voicePhaseIsBusy(): Boolean = Hub.voice.value.phase in setOf(VoicePhase.LISTENING, VoicePhase.THINKING)

package it.giacomo.tachi.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.giacomo.tachi.R

/** Logo in alto a sinistra: simbolo sportivo e scritta inclinata. */
@Composable
fun LogoRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.logo_mark), contentDescription = "Tachimetro", modifier = Modifier.size(36.dp))
        Text(
            buildAnnotatedString {
                append("TACHI")
                withStyle(SpanStyle(color = Col.amber)) { append("METRO") }
            },
            style = TextStyle(
                fontFamily = Grotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.06.em,
                color = Col.text,
                textGeometricTransform = TextGeometricTransform(skewX = -0.18f),
            ),
        )
    }
}

@Composable
fun Ico(@DrawableRes id: Int, tint: Color, size: Dp = 24.dp) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** Pulsante principale ambra. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Col.amber else Col.borderStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.button.copy(color = if (enabled) Col.onAmber else Col.muted, fontWeight = FontWeight.Bold, fontSize = 17.sp))
    }
}

/** Pulsante con solo il bordo. */
@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Col.amber,
    borderColor: Color = color,
    minHeight: Dp = 52.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.button.copy(color = color))
    }
}

/** Pulsante a riquadro con icona, come Impostazioni, Stato e Chiudi. */
@Composable
fun TileButton(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    iconTint: Color = Col.muted,
    minHeight: Dp = 56.dp,
    center: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Col.surface)
            .border(1.dp, Col.border, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(12.dp),
    ) {
        Ico(icon, iconTint, 20.dp)
        Text(text, style = Type.button)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, borderColor: Color = Col.border, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Col.surface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
    ) { content() }
}

/** Indicatore dei passi della configurazione guidata. */
@Composable
fun StepDots(step: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(Modifier.width(24.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < step) Col.amber else Color(0xFF2C333E)))
        }
        Spacer(Modifier.width(4.dp))
        Text("Passo $step di $total", style = Type.itemSub)
    }
}

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

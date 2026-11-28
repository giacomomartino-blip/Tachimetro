package it.giacomo.tachi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.giacomo.tachi.R

/** Colori del mockup approvato: fondo scuro per non abbagliare alla guida, ambra per le azioni. */
object Col {
    val bg = Color(0xFF0E1116)
    val surface = Color(0xFF171B22)
    val surfaceLow = Color(0xFF11151B)
    val border = Color(0xFF262C36)
    val borderStrong = Color(0xFF3A414D)
    val text = Color(0xFFF2F4F7)
    val muted = Color(0xFFA9B2BF)
    val faint = Color(0xFF5B6472)
    val amber = Color(0xFFF5A524)
    val onAmber = Color(0xFF111418)
    val amberSoft = Color(0xFF1E1A10)
    val blue = Color(0xFF7CB7FF)
    val green = Color(0xFF3DD68C)
    val greenBg = Color(0xFF10241A)
    val greenBorder = Color(0xFF1F4D33)
    val greenText = Color(0xFFCFF5E0)
    val warnBorder = Color(0xFF4A3A16)
    val warnText = Color(0xFFF7C66B)
    val red = Color(0xFFFF8A80)
    val redBorder = Color(0xFF5A2B2B)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))
val Grotesk = FontFamily(grotesk(500), grotesk(700))

object Type {
    val title = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, color = Col.text)
    val titleSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = Col.text)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = Col.muted)
    val itemTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, color = Col.text)
    val itemSub = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = Col.muted)
    val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Col.text)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp, color = Col.muted)
}

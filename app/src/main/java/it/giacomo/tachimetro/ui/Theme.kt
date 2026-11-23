package it.giacomo.tachimetro.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

data class Pal(
    val night: Boolean,
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val acc: Color,
    val onAcc: Color,
    val voice: Color,
    val onVoice: Color,
    val strong: Color,
    val onStrong: Color,
    val route: Color,
    val tints: Map<String, Pair<Color, Color>>,
)

private fun c(hex: Long) = Color(hex)

val NIGHT = Pal(
    night = true,
    bg = c(0xFF0D1220), surf = c(0xFF172033), surf2 = c(0xFF1F2A40), line = c(0x12FFFFFF),
    text = c(0xFFEEF2F8), muted = c(0xFFA6B0C3), acc = c(0xFF4FD1C5), onAcc = c(0xFF0D1220),
    voice = c(0xFFFF9F6B), onVoice = c(0xFF0D1220), strong = c(0xFFEEF2F8), onStrong = c(0xFF0D1220),
    route = c(0xFF5AA2FF),
    tints = mapOf(
        "sp" to (c(0xFF1D3A2C) to c(0xFF7FE0A8)),
        "wa" to (c(0xFF173A35) to c(0xFF7DDCC8)),
        "tm" to (c(0xFF2C2848) to c(0xFFBDB6F7)),
        "wz" to (c(0xFF1C3447) to c(0xFF8CCBF2)),
        "yt" to (c(0xFF3E2229) to c(0xFFF5A9B3)),
        "gm" to (c(0xFF25304F) to c(0xFFA9C1FF)),
        "am" to (c(0xFF1E3640) to c(0xFF8FD6E8)),
        "cl" to (c(0xFF3A3120) to c(0xFFF2C978)),
    ),
)

val DAY = Pal(
    night = false,
    bg = c(0xFFEEF2F7), surf = c(0xFFFFFFFF), surf2 = c(0xFFE7EDF4), line = c(0xFFDCE3EC),
    text = c(0xFF142033), muted = c(0xFF536176), acc = c(0xFF0B7C72), onAcc = c(0xFFFFFFFF),
    voice = c(0xFFB4501C), onVoice = c(0xFFFFFFFF), strong = c(0xFF142033), onStrong = c(0xFFFFFFFF),
    route = c(0xFF2F6FEB),
    tints = mapOf(
        "sp" to (c(0xFFDBF2E4) to c(0xFF145C36)),
        "wa" to (c(0xFFD8F1EC) to c(0xFF0F5A4E)),
        "tm" to (c(0xFFE6E3FA) to c(0xFF3F3794)),
        "wz" to (c(0xFFDCEBF7) to c(0xFF14507A)),
        "yt" to (c(0xFFFBE2E5) to c(0xFF8A2333)),
        "gm" to (c(0xFFE1E8FB) to c(0xFF2C4A9A)),
        "am" to (c(0xFFDAEEF3) to c(0xFF165466)),
        "cl" to (c(0xFFF8EDD5) to c(0xFF7A5410)),
    ),
)

val LocalPal = staticCompositionLocalOf { NIGHT }

/** Icone a tratto, le stesse del mockup. */
object Ic {
    private fun v(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val home by lazy { v("home", "M4 11l8-7 8 7v9h-5v-6H9v6H4z") }
    val phone by lazy { v("phone", "M5 4h4l2 5-3 2a11 11 0 0 0 5 5l2-3 5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2") }
    val msg by lazy { v("msg", "M4 5h16v11H9l-5 4z") }
    val radio by lazy { v("radio", "M5 9h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 9l10-5", "M6 14.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M14 13h4M14 16h4") }
    val music by lazy { v("music", "M9 18V5l11-2v13", "M4 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0", "M15 16a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0") }
    val grid by lazy { v("grid", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z") }
    val mic by lazy { v("mic", "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z", "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val play by lazy { v("play", "M8 5v14l11-7z") }
    val pause by lazy { v("pause", "M9 5v14M15 5v14") }
    val prev by lazy { v("prev", "M6 5v14", "M19 5L9 12l10 7z") }
    val next by lazy { v("next", "M18 5v14", "M5 5l10 7-10 7z") }
    val sun by lazy { v("sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5") }
    val moon by lazy { v("moon", "M20 14A8 8 0 1 1 10 4a6.5 6.5 0 0 0 10 10z") }
    val nav by lazy { v("nav", "M3 11l18-8-8 18-2-8z") }
    val head by lazy { v("head", "M4 15v-3a8 8 0 0 1 16 0v3", "M4.5 14h1a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-1A1.5 1.5 0 0 1 3 18.5v-3A1.5 1.5 0 0 1 4.5 14z", "M18.5 14h1a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-1a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z") }
    val speak by lazy { v("speak", "M4 9v6h4l5 4V5L8 9z", "M16 9a4 4 0 0 1 0 6") }
    val volDown by lazy { v("voldown", "M4 9v6h4l5 4V5L8 9z", "M17 12h4") }
    val volUp by lazy { v("volup", "M4 9v6h4l5 4V5L8 9z", "M17 12h4M19 10v4") }
    val plus by lazy { v("plus", "M12 5v14M5 12h14") }
    val x by lazy { v("x", "M6 6l12 12M18 6L6 18") }
    val up by lazy { v("up", "M6 15l6-6 6 6") }
    val down by lazy { v("down", "M6 9l6 6 6-6") }
    val cloud by lazy { v("cloud", "M7 18h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.1 11.1 3.5 3.5 0 0 0 7 18z") }
    val camera by lazy { v("camera", "M5 7h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0", "M8 7l1.5-3h5L16 7") }
    val battery by lazy { v("battery", "M4 7h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M22 11v2", "M12 9l-2 3h3l-2 3") }
    val thermo by lazy { v("thermo", "M10 14V5a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0z") }
    val pin by lazy { v("pin", "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z", "M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0") }
    val fuel by lazy { v("fuel", "M6 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M4 10h10M14 8l4 3v7a1.5 1.5 0 0 0 3 0v-8l-3-3") }
    val clock by lazy { v("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M12 7v5l3 2") }
    val lock by lazy { v("lock", "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V8a4 4 0 0 1 8 0v3") }
    val gear by lazy { v("gear", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4") }
    val stop by lazy { v("stop", "M6 6h12v12H6z") }
    val car by lazy { v("car", "M5 16l1.5-5a2 2 0 0 1 2-1.5h7a2 2 0 0 1 2 1.5L19 16", "M4 16h16v3H4z", "M7 19v1.5M17 19v1.5") }
}

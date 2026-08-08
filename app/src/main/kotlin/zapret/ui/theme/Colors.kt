package zapret.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Palette {
    val background = Color(0xFF0C1217)
    val backgroundDeep = Color(0xFF070A0E)
    val surface = Color(0xFF1B242E)
    val surfaceRaised = Color(0xFF24303B)
    val outline = Color(0xFF33414D)
    val accent = Color(0xFF2DE2C5)
    val accentDeep = Color(0xFF0F7F6D)
    val text = Color(0xFFF3F7F9)
    val textMuted = Color(0xFF93A0AC)
    val danger = Color(0xFFFF6B6B)

    /** Shared interaction language for rows, cards, tabs and buttons. */
    val hover = Color(0x14FFFFFF)
    val pressed = Color(0x1FFFFFFF)
    val focusRing = Color(0x662DE2C5)
    val fieldIdle = Color(0xFF1E2832)
    val fieldFocus = Color(0xFF253240)
    val divider = Color(0xFF2A3642)
}

/** Atmosphere behind the whole window: a glow where the power control sits, dark at the edges. */
fun appBackground(): Brush = Brush.linearGradient(
    0f to Color(0xFF122029),
    0.45f to Palette.background,
    1f to Palette.backgroundDeep,
)

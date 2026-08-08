package zapret.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Palette {
    val background = Color(0xFF0E1419)
    val backgroundDeep = Color(0xFF080C10)
    val surface = Color(0xFF1A222B)
    val surfaceRaised = Color(0xFF212B35)
    val outline = Color(0xFF2C3843)
    val accent = Color(0xFF2DE2C5)
    val accentDeep = Color(0xFF0F7F6D)
    val text = Color(0xFFF1F6F8)
    val textMuted = Color(0xFF8A97A3)
    val danger = Color(0xFFFF6B6B)
}

/** Atmosphere behind the whole window: a glow where the power control sits, dark at the edges. */
fun appBackground(): Brush = Brush.linearGradient(
    0f to Color(0xFF122029),
    0.45f to Palette.background,
    1f to Palette.backgroundDeep,
)

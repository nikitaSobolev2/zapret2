package zapret.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The app needs three glyphs, so they are drawn here instead of pulling in an icon set. */
@Composable
private fun Glyph(modifier: Modifier, box: Dp, draw: DrawScope.(Float) -> Unit) =
    Canvas(modifier.size(box)) { draw(size.width) }

@Composable
fun ChevronRight(tint: Color, modifier: Modifier = Modifier, box: Dp = 18.dp) = Glyph(modifier, box) { w ->
    val path = Path().apply {
        moveTo(w * 0.38f, w * 0.28f)
        lineTo(w * 0.64f, w * 0.5f)
        lineTo(w * 0.38f, w * 0.72f)
    }
    drawPath(path, tint, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round))
}

@Composable
fun HomeGlyph(tint: Color, modifier: Modifier = Modifier, box: Dp = 18.dp) = Glyph(modifier, box) { w ->
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
    val roof = Path().apply {
        moveTo(w * 0.16f, w * 0.48f)
        lineTo(w * 0.5f, w * 0.2f)
        lineTo(w * 0.84f, w * 0.48f)
    }
    val body = Path().apply {
        moveTo(w * 0.26f, w * 0.45f)
        lineTo(w * 0.26f, w * 0.8f)
        lineTo(w * 0.74f, w * 0.8f)
        lineTo(w * 0.74f, w * 0.45f)
    }
    drawPath(roof, tint, style = stroke)
    drawPath(body, tint, style = stroke)
}

@Composable
fun SettingsGlyph(tint: Color, modifier: Modifier = Modifier, box: Dp = 18.dp) = Glyph(modifier, box) { w ->
    val strokeWidth = w * 0.11f
    val center = Offset(w / 2, w / 2)
    drawCircle(tint, radius = w * 0.2f, center = center, style = Stroke(width = strokeWidth))
    repeat(GEAR_TEETH) { index ->
        val angle = index * 2.0 * PI / GEAR_TEETH
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color = tint,
            start = Offset(center.x + dx * w * 0.28f, center.y + dy * w * 0.28f),
            end = Offset(center.x + dx * w * 0.42f, center.y + dy * w * 0.42f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private const val GEAR_TEETH = 6

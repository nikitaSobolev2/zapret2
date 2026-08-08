package zapret.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import zapret.ui.theme.Palette

/** The single control of the app: colour and glow carry the state, the slow pulse says it is alive. */
@Composable
fun PowerButton(
    running: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring by animateColorAsState(if (running) Palette.accent else Palette.outline, tween(500))
    val glyph by animateColorAsState(if (running) Palette.accent else Palette.textMuted, tween(500))
    val glow by animateFloatAsState(if (running) 1f else 0f, tween(700))
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.93f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
    )

    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .clip(CircleShape)
            .clickable(enabled = !busy, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            if (glow > 0f) {
                val halo = radius * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.accent.copy(alpha = 0.28f * glow), Palette.accent.copy(alpha = 0f)),
                        center = center,
                        radius = halo,
                    ),
                    radius = halo,
                    center = center,
                )
            }

            val dial = radius * 0.72f
            drawCircle(color = Palette.surface, radius = dial, center = center)
            drawCircle(color = ring, radius = dial, center = center, style = Stroke(width = radius * 0.045f))

            if (busy) return@Canvas

            val glyphRadius = radius * 0.3f
            val shift = radius * 0.06f
            val stroke = Stroke(width = radius * 0.06f, cap = StrokeCap.Round)
            drawArc(
                color = glyph,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - glyphRadius, center.y - glyphRadius + shift),
                size = Size(glyphRadius * 2, glyphRadius * 2),
                style = stroke,
            )
            drawLine(
                color = glyph,
                start = Offset(center.x, center.y - glyphRadius * 1.6f + shift),
                end = Offset(center.x, center.y - glyphRadius * 0.15f + shift),
                strokeWidth = radius * 0.06f,
                cap = StrokeCap.Round,
            )
        }

        if (busy) CircularProgressIndicator(color = Palette.accent, modifier = Modifier.size(30.dp))
    }
}

private val BUTTON_SIZE = 190.dp

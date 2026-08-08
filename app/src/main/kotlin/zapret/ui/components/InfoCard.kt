package zapret.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import zapret.ui.theme.Dimens
import zapret.ui.theme.Palette

/** A tappable row that shows one config value and leads to the matching settings section. */
@Composable
fun InfoCard(title: String, value: String, onClick: () -> Unit, mod: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val color = when {
        pressed -> Palette.surfaceRaised
        hovered -> Palette.surfaceRaised.copy(alpha = 0.85f)
        else -> Palette.surface
    }

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(Dimens.radiusCard),
        color = color,
        modifier = mod.fillMaxWidth().hoverable(interaction),
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.lg + Dimens.xs, vertical = Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChevronRight(if (hovered) Palette.accent else Palette.textMuted)
        }
    }
}

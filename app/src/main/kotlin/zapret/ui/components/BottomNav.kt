package zapret.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zapret.ui.Screen
import zapret.ui.theme.Dimens
import zapret.ui.theme.Palette

@Composable
fun BottomNav(current: Screen, onSelect: (Screen) -> Unit, mod: Modifier = Modifier) {
    val tabs = listOf(Screen.HOME to "Главная", Screen.SETTINGS to "Настройки")

    Surface(
        shape = RoundedCornerShape(Dimens.radiusNav),
        color = Palette.surface,
        modifier = mod.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.padding(Dimens.sm - Dimens.xs / 2).height(Dimens.navHeight)) {
            val tabWidth = maxWidth / tabs.size
            val selected = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0)
            val indicator by animateDpAsState(
                targetValue = tabWidth * selected,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            )

            Box(
                Modifier
                    .offset(x = indicator)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .background(Palette.accent.copy(alpha = 0.16f), RoundedCornerShape(20.dp)),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                tabs.forEach { (screen, label) ->
                    Tab(
                        label = label,
                        screen = screen,
                        active = screen == current,
                        onClick = { onSelect(screen) },
                        mod = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tab(
    label: String,
    screen: Screen,
    active: Boolean,
    onClick: () -> Unit,
    mod: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint by animateColorAsState(
        when {
            active -> Palette.accent
            hovered -> Palette.text
            else -> Palette.textMuted
        },
    )
    Row(
        modifier = mod
            .clip(RoundedCornerShape(20.dp))
            .hoverable(interaction)
            .background(if (!active && hovered) Palette.hover else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (screen) {
            Screen.HOME -> HomeGlyph(tint)
            Screen.SETTINGS -> SettingsGlyph(tint)
        }
        Spacer(Modifier.width(Dimens.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

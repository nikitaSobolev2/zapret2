package zapret.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun ZapretTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Palette.backgroundDeep,
            secondary = Palette.accentDeep,
            background = Palette.background,
            onBackground = Palette.text,
            surface = Palette.surface,
            onSurface = Palette.text,
            surfaceVariant = Palette.surfaceRaised,
            onSurfaceVariant = Palette.textMuted,
            outline = Palette.outline,
            error = Palette.danger,
        ),
        typography = ZapretTypography,
        content = content,
    )
}

package zapret.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import zapret.ui.AppViewModel
import zapret.ui.Screen
import zapret.ui.theme.Palette

/** Menu bar presence: the app lives here, the window is only a detail view. */
@Composable
fun ApplicationScope.TrayMenu(model: AppViewModel) {
    val state = model.state
    val icon = remember(state.running) { TrayIcon(state.running) }

    Tray(
        icon = icon,
        tooltip = "Zapret — ${state.trayStatus()}",
        onAction = { model.openWindow(Screen.HOME) },
        menu = {
            Item(text = state.trayStatus(), enabled = false, onClick = {})
            Separator()
            Item(text = "Открыть окно", onClick = { model.openWindow(Screen.HOME) })
            if (state.installed) {
                Item(
                    text = if (state.running) "Остановить" else "Запустить",
                    enabled = state.busy == null,
                    onClick = model::toggle,
                )
            } else {
                Item(text = "Установить zapret2", enabled = state.busy == null, onClick = model::install)
            }
            Item(text = "Настройки", onClick = { model.openWindow(Screen.SETTINGS) })
            Separator()
            Item(text = "Закрыть полностью", onClick = ::exitApplication)
        },
    )
}

private fun zapret.ui.UiState.trayStatus(): String = when {
    busy != null -> busy
    !installed -> "Не установлен"
    running -> "Работает"
    else -> "Остановлено"
}

/** A ring that fills with the accent colour while the daemon runs. */
private class TrayIcon(private val active: Boolean) : Painter() {

    override val intrinsicSize = Size(ICON, ICON)

    override fun DrawScope.onDraw() {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.36f
        val color = if (active) Palette.accent else Color(0xFFB9C3CC)

        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = size.minDimension * 0.11f))
        if (active) drawCircle(color = color, radius = radius * 0.42f, center = center)
    }

    private companion object {
        const val ICON = 44f
    }
}

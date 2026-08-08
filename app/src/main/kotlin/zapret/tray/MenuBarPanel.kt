package zapret.tray

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import kotlinx.coroutines.delay
import zapret.ui.AppViewModel
import zapret.ui.Screen
import zapret.ui.asTimer
import zapret.ui.home.PowerButton
import zapret.ui.theme.Dimens
import zapret.ui.theme.Palette
import zapret.ui.theme.ZapretTheme
import zapret.ui.theme.appBackground

/** Compact always-on-top panel opened from the menu-bar icon. */
@Composable
fun MenuBarPanel(model: AppViewModel) {
    if (!model.panelVisible) return

    val position = remember { panelPosition() }
    val state = rememberWindowState(
        position = position,
        size = DpSize(Dimens.panelWidth, Dimens.panelHeight),
    )

    Window(
        onCloseRequest = model::hidePanel,
        visible = true,
        title = "Zapret",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        state = state,
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                model.hidePanel()
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            delay(350)
            while (model.panelVisible) {
                if (!window.isActive) {
                    model.hidePanel()
                    break
                }
                delay(200)
            }
        }

        ZapretTheme {
            PanelContent(model)
        }
    }
}

@Composable
private fun PanelContent(model: AppViewModel) {
    val ui = model.state
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val timer = remember(tick, ui.status) { ui.uptime().asTimer() }

    val shape = RoundedCornerShape(Dimens.radiusPanel)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(appBackground())
            .border(1.dp, Palette.outline, shape)
            .padding(Dimens.lg)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    model.hidePanel()
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Zapret", style = MaterialTheme.typography.titleMedium, color = Palette.text)
                Text(ui.panelStatus(), style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
            }
            if (ui.running) {
                Text(timer, style = MaterialTheme.typography.titleMedium, color = Palette.accent)
            }
        }

        Spacer(Modifier.height(Dimens.md))
        PowerButton(
            running = ui.running,
            busy = ui.busy != null,
            onClick = model::toggle,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(Dimens.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!ui.installed) {
                TextButton(onClick = model::install, enabled = ui.busy == null) {
                    Text("Установить", color = Palette.accent)
                }
            } else if (ui.running) {
                TextButton(onClick = model::toggle, enabled = ui.busy == null) {
                    Text("Выключить", color = Palette.danger)
                }
            } else {
                TextButton(onClick = model::toggle, enabled = ui.busy == null) {
                    Text("Включить", color = Palette.accent)
                }
            }
        }

        if (ui.tgConfig.enabled) {
            TextButton(
                onClick = model::restartTgProxy,
                enabled = ui.busy == null,
            ) {
                Text("Перезапустить TG proxy", color = Palette.accent)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { model.openWindow(Screen.HOME) }) {
                Text("Открыть окно", color = Palette.accent)
            }
            TextButton(onClick = { model.openWindow(Screen.SETTINGS) }) {
                Text("Настройки", color = Palette.text)
            }
        }
    }
}

private fun zapret.ui.UiState.panelStatus(): String = when {
    busy != null -> busy
    !installed -> "Не установлен"
    running && tgConfig.enabled && tgRunning -> "Zapret + TG"
    running && tgConfig.enabled -> "Zapret · TG стоп"
    running -> "Работает"
    else -> "Остановлено"
}

/** Place the panel near the top-right of the screen that contains the mouse (menu-bar side). */
private fun panelPosition(): WindowPosition {
    val mouse = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val bounds = device.defaultConfiguration.bounds
    val insets = runCatching {
        java.awt.Toolkit.getDefaultToolkit().getScreenInsets(device.defaultConfiguration)
    }.getOrNull()
    val top = bounds.y + (insets?.top ?: 28) + 8
    val widthPx = Dimens.panelWidth.value
    val x = if (mouse != null) {
        (mouse.x - widthPx / 2).toInt().coerceIn(bounds.x + 12, bounds.x + bounds.width - widthPx.toInt() - 12)
    } else {
        bounds.x + bounds.width - widthPx.toInt() - 24
    }
    return WindowPosition(x.dp, top.dp)
}

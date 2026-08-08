package zapret

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import zapret.tray.TrayMenu
import zapret.ui.App
import zapret.ui.AppViewModel

fun main() = application {
    val scope = rememberCoroutineScope()
    val model = remember { AppViewModel(scope) }

    TrayMenu(model)

    Window(
        onCloseRequest = model::hideWindow,
        visible = model.windowVisible,
        title = "Zapret",
        state = rememberWindowState(width = 440.dp, height = 820.dp),
    ) {
        App(model)
    }
}

package zapret

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import zapret.tray.MenuBarPanel
import zapret.tray.TrayMenu
import zapret.ui.App
import zapret.ui.AppViewModel
import zapret.ui.theme.Dimens

fun main() = application {
    val scope = rememberCoroutineScope()
    val model = remember { AppViewModel(scope) }
    val appIcon = painterResource("zapret-icon.png")

    TrayMenu(model)
    MenuBarPanel(model)

    Window(
        onCloseRequest = model::hideWindow,
        visible = model.windowVisible,
        title = "Zapret",
        icon = appIcon,
        state = rememberWindowState(
            size = DpSize(Dimens.windowWidth, Dimens.windowHeight),
        ),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(Dimens.windowMinWidth, Dimens.windowMinHeight)
            window.maximumSize = Dimension(Dimens.windowMaxWidth, Dimens.windowMaxHeight)
        }
        App(model)
    }
}

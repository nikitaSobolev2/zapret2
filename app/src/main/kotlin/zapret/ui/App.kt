package zapret.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import zapret.ui.components.BottomNav
import zapret.ui.components.NoticeBar
import zapret.ui.home.HomeScreen
import zapret.ui.settings.SettingsScreen
import zapret.ui.theme.Dimens
import zapret.ui.theme.ZapretTheme
import zapret.ui.theme.appBackground

@Composable
fun App(model: AppViewModel) = ZapretTheme {
    val state = model.state

    Box(Modifier.fillMaxSize().background(appBackground())) {
        Column(Modifier.fillMaxSize().padding(horizontal = Dimens.xl)) {
            Box(Modifier.weight(1f)) {
                when (state.screen) {
                    Screen.HOME -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(Dimens.xxl))
                        HomeScreen(
                            state = state,
                            onToggle = model::toggle,
                            onOpen = model::show,
                            onInstallCompiler = model::installCompilerTools,
                        )
                        Spacer(Modifier.height(Dimens.xl))
                    }

                    Screen.SETTINGS -> SettingsScreen(
                        state = state,
                        onApply = model::applyConfig,
                        onInstall = model::install,
                        onUninstall = model::uninstall,
                        onPasswordless = model::setPasswordless,
                        onAutoUpdate = model::setAutoUpdate,
                        onCheckUpdates = model::checkForUpdates,
                        onUpdateNow = { model.startUpdate() },
                        onProbeStrategies = model::probeStrategies,
                    )
                }
            }

            state.notice?.let {
                NoticeBar(it, model::dismissNotice)
                Spacer(Modifier.height(Dimens.sm + Dimens.xs / 2))
            }
            BottomNav(state.screen, model::show)
            Spacer(Modifier.height(Dimens.lg))
        }

        UpdateDialog(state = state, onCancel = model::cancelUpdate)
    }
}

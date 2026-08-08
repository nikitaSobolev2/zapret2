package zapret.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import zapret.ui.Screen
import zapret.ui.UiState
import zapret.ui.asTimer
import zapret.ui.components.InfoCard
import zapret.ui.theme.BrandStyle
import zapret.ui.theme.Dimens
import zapret.ui.theme.Palette
import zapret.ui.theme.TimerStyle

@Composable
fun HomeScreen(
    state: UiState,
    onToggle: () -> Unit,
    onOpen: (Screen) -> Unit,
    mod: Modifier = Modifier,
) {
    Column(
        mod.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Brand()

        Spacer(Modifier.height(Dimens.xxl))
        Uptime(state)
        Spacer(Modifier.height(Dimens.md))

        PowerButton(running = state.running, busy = state.busy != null, onClick = onToggle)

        Spacer(Modifier.height(Dimens.lg))
        Text(state.headline(), style = MaterialTheme.typography.headlineSmall, color = Palette.text)
        Spacer(Modifier.height(Dimens.sm - Dimens.xs / 2))
        Text(
            text = state.subline(),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.textMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Dimens.section))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.sm + Dimens.xs / 2)) {
            val open = { onOpen(Screen.SETTINGS) }
            InfoCard("Режим фильтра", state.config.filterMode.label, onClick = open)
            InfoCard("Порты", "${state.config.tpwsPorts} → ${state.config.tpwsPort}", onClick = open)
            InfoCard("Стратегия", state.strategyPreview(), onClick = open)
        }
    }
}

@Composable
private fun Brand() {
    Text("ZAPRET", style = BrandStyle, color = Palette.text)
    Spacer(Modifier.height(Dimens.xs))
    Text(
        text = "обход блокировок · macOS",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.textMuted,
    )
}

@Composable
private fun Uptime(state: UiState) {
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    val timer = remember(tick, state.status) { state.uptime().asTimer() }

    AnimatedVisibility(
        visible = state.running,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
    ) {
        Text(timer, style = TimerStyle, color = Palette.accent)
    }
    if (!state.running) Text("--:--:--", style = TimerStyle, color = Palette.outline)
}

private fun UiState.headline(): String = when {
    busy != null -> busy
    !installed -> "Не установлен"
    running -> "Работает"
    else -> "Остановлено"
}

private fun UiState.subline(): String = when {
    !installed -> "Нажмите кнопку, чтобы собрать и установить zapret2"
    running -> "tpws на порту ${config.tpwsPort} · фильтр: ${config.filterMode.label}"
    else -> "Нажмите кнопку, чтобы включить обход"
}

private fun UiState.strategyPreview(): String =
    config.tpwsOpt.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "не задана"

package zapret.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zapret.domain.FilterMode
import zapret.domain.UninstallScope
import zapret.domain.ZapretConfig
import zapret.ui.UiState
import zapret.ui.components.AccentButton
import zapret.ui.components.ChoiceRow
import zapret.ui.components.DangerButton
import zapret.ui.components.Section
import zapret.ui.components.SwitchRow
import zapret.ui.components.ValueField
import zapret.ui.theme.MonoStyle
import zapret.ui.theme.Palette

@Composable
fun SettingsScreen(
    state: UiState,
    onApply: (ZapretConfig) -> Unit,
    onInstall: () -> Unit,
    onUninstall: (UninstallScope) -> Unit,
    onPasswordless: (Boolean) -> Unit,
    mod: Modifier = Modifier,
) {
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var askUninstall by remember { mutableStateOf(false) }
    val editable = state.busy == null

    Column(mod.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, color = Palette.text)

        Section("Прозрачный режим") {
            SwitchRow("Включён", draft.tpwsEnable) { draft = draft.copy(tpwsEnable = it) }
            ValueField("Порт tpws", draft.tpwsPort, onChange = { draft = draft.copy(tpwsPort = it) })
            ValueField("Перенаправляемые порты", draft.tpwsPorts, onChange = { draft = draft.copy(tpwsPorts = it) })
            ValueField(
                label = "Стратегия",
                value = draft.tpwsOpt,
                onChange = { draft = draft.copy(tpwsOpt = it) },
                singleLine = false,
                textStyle = MonoStyle,
                minHeight = 132.dp,
            )
        }

        Section("SOCKS-прокси") {
            SwitchRow("Включён", draft.socksEnable) { draft = draft.copy(socksEnable = it) }
            ValueField("Порт SOCKS", draft.socksPort, onChange = { draft = draft.copy(socksPort = it) })
            ValueField(
                label = "Стратегия SOCKS",
                value = draft.socksOpt,
                onChange = { draft = draft.copy(socksOpt = it) },
                singleLine = false,
                textStyle = MonoStyle,
                minHeight = 110.dp,
            )
        }

        Section("Фильтрация трафика") {
            FilterMode.entries.forEach { mode ->
                ChoiceRow(mode.label, draft.filterMode == mode) { draft = draft.copy(filterMode = mode) }
            }
        }

        Section("Сеть и брандмауэр") {
            SwitchRow("Не работать с IPv6", draft.disableIpv6) { draft = draft.copy(disableIpv6 = it) }
            SwitchRow("Применять правила PF", draft.applyFirewall) { draft = draft.copy(applyFirewall = it) }
            ValueField(
                label = "Интерфейс WAN",
                value = draft.ifaceWan,
                onChange = { draft = draft.copy(ifaceWan = it) },
            )
            Text(
                text = "Физический интерфейс для обхода (обычно en0). " +
                    "Пусто = авто. Нужен, чтобы корпоративный VPN (L2TP/utun) работал вместе с zapret.",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textMuted,
            )
        }

        Section("Права") {
            SwitchRow("Вкл/выкл без пароля", state.passwordless) { onPasswordless(it) }
            Text(
                text = "Разрешает запуск/остановку zapret2 через sudo без запроса пароля. " +
                    "Настройка требует однократного ввода пароля администратора.",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textMuted,
            )
        }

        if (state.installed) {
            AccentButton(
                text = "Применить и перезапустить",
                enabled = editable,
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AccentButton(
                text = "Установить zapret2",
                enabled = editable,
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DangerButton(
            text = "Удалить…",
            enabled = editable,
            onClick = { askUninstall = true },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }

    if (askUninstall) {
        UninstallDialog(
            onChoose = {
                askUninstall = false
                onUninstall(it)
            },
            onDismiss = { askUninstall = false },
        )
    }
}

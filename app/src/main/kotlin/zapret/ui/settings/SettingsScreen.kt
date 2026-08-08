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
import zapret.ui.theme.Dimens
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

    Column(mod.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xl)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, color = Palette.text)

        Section(
            title = "Прозрачный режим",
            description = "Системный обход DPI: PF перенаправляет выбранные порты в tpws.",
        ) {
            SwitchRow(
                label = "Включён",
                checked = draft.tpwsEnable,
                onChange = { draft = draft.copy(tpwsEnable = it) },
                description = "Прозрачный обход DPI для портов ниже",
            )
            ValueField("Порт tpws", draft.tpwsPort, onChange = { draft = draft.copy(tpwsPort = it) })
            ValueField(
                label = "Перенаправляемые порты",
                value = draft.tpwsPorts,
                onChange = { draft = draft.copy(tpwsPorts = it) },
            )
            ValueField(
                label = "Стратегия",
                value = draft.tpwsOpt,
                onChange = { draft = draft.copy(tpwsOpt = it) },
                singleLine = false,
                textStyle = MonoStyle,
                minHeight = 132.dp,
            )
        }

        Section(
            title = "SOCKS-прокси",
            description = "Локальный прокси для приложений, которые умеют SOCKS сами.",
        ) {
            SwitchRow(
                label = "Включён",
                checked = draft.socksEnable,
                onChange = { draft = draft.copy(socksEnable = it) },
                description = "Локальный SOCKS для приложений вручную",
            )
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

        Section(
            title = "Фильтрация трафика",
            description = "Какой трафик обрабатывать: всё, списки IP/доменов или автосписок.",
        ) {
            ChoiceRow(
                label = FilterMode.NONE.label,
                selected = draft.filterMode == FilterMode.NONE,
                onSelect = { draft = draft.copy(filterMode = FilterMode.NONE) },
                description = "Обрабатывать весь подходящий трафик",
            )
            ChoiceRow(
                label = FilterMode.IPSET.label,
                selected = draft.filterMode == FilterMode.IPSET,
                onSelect = { draft = draft.copy(filterMode = FilterMode.IPSET) },
                description = "Только адреса из ipset-списков",
            )
            ChoiceRow(
                label = FilterMode.HOSTLIST.label,
                selected = draft.filterMode == FilterMode.HOSTLIST,
                onSelect = { draft = draft.copy(filterMode = FilterMode.HOSTLIST) },
                description = "Только домены из списков хостов",
            )
            ChoiceRow(
                label = FilterMode.AUTOHOSTLIST.label,
                selected = draft.filterMode == FilterMode.AUTOHOSTLIST,
                onSelect = { draft = draft.copy(filterMode = FilterMode.AUTOHOSTLIST) },
                description = "Автоматически пополнять список доменов",
            )
        }

        Section(
            title = "Сеть и брандмауэр",
            description = "Интерфейс WAN ограничивает PF физическим линком — VPN (L2TP/utun) остаётся в стороне.",
        ) {
            SwitchRow(
                label = "Не работать с IPv6",
                checked = draft.disableIpv6,
                onChange = { draft = draft.copy(disableIpv6 = it) },
                description = "Не трогать IPv6-трафик",
            )
            SwitchRow(
                label = "Применять правила PF",
                checked = draft.applyFirewall,
                onChange = { draft = draft.copy(applyFirewall = it) },
                description = "Правила PF для перенаправления портов",
            )
            ValueField(
                label = "Интерфейс WAN",
                value = draft.ifaceWan,
                onChange = { draft = draft.copy(ifaceWan = it) },
                description = "Обычно en0. Пусто = авто. Нужен для совместной работы с корпоративным VPN.",
            )
        }

        Section(title = "Права") {
            SwitchRow(
                label = "Вкл/выкл без пароля",
                checked = state.passwordless,
                onChange = onPasswordless,
                description = "sudo без пароля только для start/stop/restart. Включение спросит пароль один раз.",
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
        Spacer(Modifier.height(Dimens.xs))
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

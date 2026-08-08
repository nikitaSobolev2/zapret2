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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import zapret.domain.EngineListsStore
import zapret.domain.IpsetMode
import zapret.domain.StrategyEntry
import zapret.domain.TgWsProxyConfig
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
    onApply: (ZapretConfig, TgWsProxyConfig, Map<String, String>) -> Unit,
    onInstall: () -> Unit,
    onUninstall: (UninstallScope) -> Unit,
    onPasswordless: (Boolean) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
    mod: Modifier = Modifier,
) {
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var tgDraft by remember(state.tgConfig) { mutableStateOf(state.tgConfig) }
    var lists by remember(state.listContents) { mutableStateOf(state.listContents) }
    var selectedList by remember { mutableStateOf(EngineListsStore.LIST_FILES.first()) }
    var askUninstall by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }
    var linkOpened by remember { mutableStateOf(false) }
    val editable = state.busy == null
    val strategies = state.strategies.ifEmpty {
        listOf(StrategyEntry(ZapretConfig.DEFAULT_STRATEGY, ZapretConfig.DEFAULT_STRATEGY))
    }

    Column(mod.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xl)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, color = Palette.text)

        Section(
            title = "Анти-DPI (utunws)",
            creditLabel = "стратегии · Flowseal / bol-van",
            onCreditClick = { openUrl("https://github.com/Flowseal/zapret-discord-youtube") },
            description = "Пакетный обход DPI (fake/QUIC/Discord UDP) через utun + BPF. " +
                "Нужен физический WAN и ARP к шлюзу. Корпоративный VPN — только split-tunnel.",
        ) {
            Text("Стратегия", style = MaterialTheme.typography.labelLarge, color = Palette.textMuted)
            strategies.forEach { entry ->
                ChoiceRow(
                    label = entry.title,
                    selected = draft.strategyId == entry.id,
                    onSelect = { draft = draft.copy(strategyId = entry.id) },
                    description = entry.id,
                )
            }
            Spacer(Modifier.height(Dimens.sm))
            Text("IP-список", style = MaterialTheme.typography.labelLarge, color = Palette.textMuted)
            IpsetMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.label,
                    selected = draft.ipsetMode == mode,
                    onSelect = { draft = draft.copy(ipsetMode = mode) },
                )
            }
        }

        Section(
            title = "Списки доменов и IP",
            description = "Файлы в ~/Library/Application Support/Zapret/lists. " +
                "Редактируйте и нажмите «Применить». Reset вернёт значения из пакета.",
        ) {
            EngineListsStore.LIST_FILES.forEach { name ->
                ChoiceRow(
                    label = EngineListsStore.LIST_LABELS[name] ?: name,
                    selected = selectedList == name,
                    onSelect = { selectedList = name },
                )
            }
            ValueField(
                label = EngineListsStore.LIST_LABELS[selectedList] ?: selectedList,
                value = lists[selectedList].orEmpty(),
                onChange = { lists = lists + (selectedList to it) },
                singleLine = false,
                textStyle = MonoStyle,
                minHeight = 160.dp,
            )
            AccentButton(
                text = "Сбросить выбранный список",
                enabled = editable,
                onClick = {
                    val defaults = state.defaultListContents[selectedList]
                    if (defaults != null) {
                        lists = lists + (selectedList to defaults)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AccentButton(
                text = "Сбросить все списки к пакету",
                enabled = editable,
                onClick = { lists = state.defaultListContents },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section(
            title = "Telegram MTProto proxy",
            creditLabel = "tg-ws-proxy · автор Flowseal",
            onCreditClick = { openUrl("https://github.com/Flowseal/tg-ws-proxy") },
            description = "Локальный мост для Telegram Desktop (MTProto → WSS). " +
                "Запускается вместе с Zapret. Не открывает web.telegram.org при IP-блокировке.",
        ) {
            SwitchRow(
                label = "Включён с Zapret",
                checked = tgDraft.enabled,
                onChange = { tgDraft = tgDraft.copy(enabled = it) },
                description = "Старт/стоп вместе с кнопкой питания",
            )
            ValueField("Host", tgDraft.host, onChange = { tgDraft = tgDraft.copy(host = it) })
            ValueField("Порт", tgDraft.port, onChange = { tgDraft = tgDraft.copy(port = it) })
            ValueField(
                label = "Secret (32 hex)",
                value = tgDraft.secret,
                onChange = { tgDraft = tgDraft.copy(secret = it) },
                textStyle = MonoStyle,
            )
            AccentButton(
                text = "Сгенерировать secret",
                enabled = editable,
                onClick = { tgDraft = tgDraft.copy(secret = TgWsProxyConfig.newSecret()) },
                modifier = Modifier.fillMaxWidth(),
            )
            ValueField(
                label = "DC → IP (по строке, DC:IP)",
                value = tgDraft.dcIp.joinToString("\n"),
                onChange = {
                    tgDraft = tgDraft.copy(
                        dcIp = it.lineSequence().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }.toList(),
                    )
                },
                singleLine = false,
                textStyle = MonoStyle,
                minHeight = 72.dp,
            )
            SwitchRow(
                label = "Cloudflare fallback",
                checked = tgDraft.cfproxy,
                onChange = { tgDraft = tgDraft.copy(cfproxy = it) },
            )
            AccentButton(
                text = if (linkCopied) "Ссылка скопирована" else "Копировать tg:// proxy",
                enabled = editable,
                onClick = {
                    copyToClipboard(tgDraft.telegramProxyLink())
                    linkCopied = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AccentButton(
                text = if (linkOpened) "Открыто в Telegram" else "Открыть в Telegram",
                enabled = editable,
                onClick = {
                    openInTelegram(tgDraft.telegramProxyLink())
                    linkOpened = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section(title = "Права") {
            SwitchRow(
                label = "Вкл/выкл без пароля",
                checked = state.passwordless,
                onChange = onPasswordless,
                description = "sudo без пароля для stop/start (restart.sh). Первая установка движка всё ещё спросит пароль.",
            )
        }

        Section(
            title = "Обновления",
            description = "Текущая версия: ${state.appVersion}. DMG с GitHub Releases.",
        ) {
            SwitchRow(
                label = "Автообновление",
                checked = state.autoUpdate,
                onChange = onAutoUpdate,
            )
            AccentButton(
                text = "Проверить обновления",
                enabled = editable && !state.showUpdateModal,
                onClick = onCheckUpdates,
                modifier = Modifier.fillMaxWidth(),
            )
            state.updateAvailable?.let { info ->
                Text("Доступна версия ${info.version}", color = Palette.accent)
                AccentButton(
                    text = "Обновить сейчас",
                    enabled = editable && !state.showUpdateModal,
                    onClick = onUpdateNow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AccentButton(
            text = if (state.installed) "Применить и перезапустить" else "Сохранить настройки",
            enabled = editable,
            onClick = { onApply(draft, tgDraft, lists) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.installed) {
            AccentButton(
                text = "Установить движок",
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

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private fun openInTelegram(url: String) {
    if (!openUrl(url)) copyToClipboard(url)
}

private fun openUrl(url: String): Boolean =
    runCatching {
        ProcessBuilder("/usr/bin/open", url).redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

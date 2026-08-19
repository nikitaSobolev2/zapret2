package zapret.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import zapret.domain.DesktopOpen
import zapret.domain.EngineListsStore
import zapret.domain.IpsetMode
import zapret.domain.StrategyEntry
import zapret.domain.StrategyProbeReport
import zapret.domain.TgWsProxyConfig
import zapret.domain.UninstallScope
import zapret.domain.ZapretConfig
import zapret.ui.UiState
import zapret.ui.components.AccentButton
import zapret.ui.components.ChoiceRow
import zapret.ui.components.DropdownField
import zapret.ui.components.GhostButton
import zapret.ui.components.Section
import zapret.ui.components.SwitchRow
import zapret.ui.components.TextAction
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
    onTgEnabled: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
    onProbeStrategies: () -> Unit,
    onOpenList: (String) -> Unit,
    mod: Modifier = Modifier,
) {
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var tgDraft by remember(state.tgConfig.copy(enabled = false)) { mutableStateOf(state.tgConfig) }
    var lists by remember(state.listContents) { mutableStateOf(state.listContents) }
    var selectedList by remember { mutableStateOf(EngineListsStore.USER_HOST_LIST) }
    var replacedOversized by remember(state.listContents) { mutableStateOf(setOf<String>()) }
    val oversizedNames = state.oversizedLists.keys
    var tgAdvanced by remember { mutableStateOf(false) }
    var systemAdvanced by remember { mutableStateOf(false) }
    var askUninstall by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }
    var linkOpened by remember { mutableStateOf(false) }
    val editable = state.busy == null && state.probePhase == null
    val strategies = state.strategies.ifEmpty {
        listOf(StrategyEntry(ZapretConfig.DEFAULT_STRATEGY, ZapretConfig.DEFAULT_STRATEGY))
    }
    val strategyTitle = strategies.firstOrNull { it.id == draft.strategyId }?.title ?: draft.strategyId
    val listLabel = EngineListsStore.LIST_LABELS[selectedList] ?: selectedList

    Column(mod.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.lg),
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall, color = Palette.text)

            Section(
                title = "Анти-DPI",
                creditLabel = "Flowseal / bol-van",
                onCreditClick = { openUrl("https://github.com/Flowseal/zapret-discord-youtube") },
                description = "WAN (en0) + ARP. Corp VPN — только split-tunnel.",
            ) {
                DropdownField(
                    label = "Стратегия",
                    value = strategyTitle,
                    options = strategies.map { it.id to it.title },
                    onSelect = { id -> draft = draft.copy(strategyId = id) },
                    enabled = editable,
                )
                GhostButton(
                    text = state.probePhase ?: "Подобрать стратегию",
                    enabled = editable && state.installed,
                    onClick = onProbeStrategies,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.probeReport?.let { report ->
                    ProbeSummary(report)
                    if (report.winnerId != null) {
                        TextAction(
                            text = "Применить ${report.winnerId}",
                            enabled = editable,
                            onClick = { draft = draft.copy(strategyId = report.winnerId) },
                        )
                    }
                }
                Text("IP-список", style = MaterialTheme.typography.labelLarge, color = Palette.textMuted)
                IpsetMode.entries.forEach { mode ->
                    ChoiceRow(
                        label = mode.label,
                        selected = draft.ipsetMode == mode,
                        onSelect = { draft = draft.copy(ipsetMode = mode) },
                    )
                }
                SwitchRow(
                    label = "Discord UDP порты",
                    checked = draft.discordUdp,
                    onChange = { draft = draft.copy(discordUdp = it) },
                    description = "Голос Discord через PF (чуть выше нагрузка)",
                )
                SwitchRow(
                    label = "Блокировать QUIC (HTTP/3)",
                    checked = draft.blockQuic,
                    onChange = { draft = draft.copy(blockQuic = it) },
                    description = "YouTube в браузере: UDP/443 → TCP (рекомендуется)",
                )
            }

            Section(
                title = "Списки",
                description = "Свой сайт — «Домены пользователя». Путь: ~/Library/Application Support/Zapret/lists",
            ) {
                DropdownField(
                    label = "Файл",
                    value = listLabel,
                    options = EngineListsStore.LIST_FILES.map { it to (EngineListsStore.LIST_LABELS[it] ?: it) },
                    onSelect = { selectedList = it },
                    enabled = editable,
                )
                val editInApp = selectedList !in oversizedNames || selectedList in replacedOversized
                if (editInApp) {
                    ValueField(
                        label = listLabel,
                        value = lists[selectedList].orEmpty(),
                        onChange = { lists = lists + (selectedList to it) },
                        singleLine = false,
                        textStyle = MonoStyle,
                        minHeight = 140.dp,
                        maxHeight = 220.dp,
                        enabled = editable,
                        description = "Один хост на строку, без https:// и без *. Поддомены совпадают сами. Нужен «Применить и перезапустить».",
                    )
                } else {
                    val sizeKb = ((state.oversizedLists[selectedList] ?: 0L) + 1023) / 1024
                    Text(
                        text = "Файл слишком большой для экрана настроек ($sizeKb КБ). Встроенный редактор его не рисует.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.text,
                    )
                    Text(
                        text = "Откройте во внешнем редакторе. «Применить» этот файл не перезапишет. Пустая замена тоже не сотрёт файл на диске.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.textMuted,
                    )
                    GhostButton(
                        text = "Открыть в редакторе",
                        enabled = editable,
                        onClick = { onOpenList(selectedList) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextAction(
                        text = "Заменить содержимым здесь",
                        enabled = editable,
                        onClick = {
                            replacedOversized = replacedOversized + selectedList
                            lists = lists + (selectedList to "")
                        },
                    )
                }
                TextAction(
                    text = "Сбросить выбранный",
                    enabled = editable && (selectedList !in oversizedNames || selectedList in replacedOversized),
                    onClick = {
                        val defaults = state.defaultListContents[selectedList]
                        if (defaults != null) lists = lists + (selectedList to defaults)
                    },
                )
                TextAction(
                    text = "Сбросить все к пакету",
                    enabled = editable,
                    onClick = {
                        lists = state.defaultListContents.filterKeys { it !in oversizedNames }
                        replacedOversized = emptySet()
                    },
                )
            }

            Section(
                title = "Telegram MTProto",
                creditLabel = "tg-ws-proxy · Flowseal",
                onCreditClick = { openUrl("https://github.com/Flowseal/tg-ws-proxy") },
            ) {
                SwitchRow(
                    label = "Включён с Zapret",
                    checked = state.tgConfig.enabled,
                    enabled = editable,
                    onChange = onTgEnabled,
                    description = "Только Telegram Desktop. Включается сразу, без «Применить».",
                )
                GhostButton(
                    text = if (linkCopied) "Ссылка скопирована" else "Копировать tg://",
                    enabled = editable,
                    onClick = {
                        copyToClipboard(tgDraft.telegramProxyLink())
                        linkCopied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostButton(
                    text = if (linkOpened) "Открыто" else "Открыть в Telegram",
                    enabled = editable,
                    onClick = {
                        openInTelegram(tgDraft.telegramProxyLink())
                        linkOpened = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextAction(
                    text = if (tgAdvanced) "Скрыть параметры" else "Параметры proxy",
                    enabled = true,
                    onClick = { tgAdvanced = !tgAdvanced },
                )
                if (tgAdvanced) {
                    ValueField("Host", tgDraft.host, onChange = { tgDraft = tgDraft.copy(host = it) })
                    ValueField("Порт", tgDraft.port, onChange = { tgDraft = tgDraft.copy(port = it) })
                    ValueField(
                        label = "Secret (32 hex)",
                        value = tgDraft.secret,
                        onChange = { tgDraft = tgDraft.copy(secret = it) },
                        textStyle = MonoStyle,
                    )
                    GhostButton(
                        text = "Сгенерировать secret",
                        enabled = editable,
                        onClick = { tgDraft = tgDraft.copy(secret = TgWsProxyConfig.newSecret()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ValueField(
                        label = "DC → IP",
                        value = tgDraft.dcIp.joinToString("\n"),
                        onChange = {
                            tgDraft = tgDraft.copy(
                                dcIp = it.lineSequence().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }.toList(),
                            )
                        },
                        singleLine = false,
                        textStyle = MonoStyle,
                        minHeight = 64.dp,
                    )
                    SwitchRow(
                        label = "Cloudflare fallback",
                        checked = tgDraft.cfproxy,
                        onChange = { tgDraft = tgDraft.copy(cfproxy = it) },
                    )
                }
            }

            Section(title = "Система") {
                SwitchRow(
                    label = "Вкл/выкл без пароля",
                    checked = state.passwordless,
                    onChange = onPasswordless,
                    description = "sudo для stop/restart",
                )
                TextAction(
                    text = if (systemAdvanced) "Скрыть обновления" else "Обновления · v${state.appVersion}",
                    enabled = true,
                    onClick = { systemAdvanced = !systemAdvanced },
                )
                if (systemAdvanced) {
                    SwitchRow(
                        label = "Автообновление",
                        checked = state.autoUpdate,
                        onChange = onAutoUpdate,
                    )
                    GhostButton(
                        text = "Проверить обновления",
                        enabled = editable && !state.showUpdateModal,
                        onClick = onCheckUpdates,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.updateAvailable?.let { info ->
                        Text("Доступна ${info.version}", color = Palette.accent)
                        GhostButton(
                            text = "Обновить сейчас",
                            enabled = editable && !state.showUpdateModal,
                            onClick = onUpdateNow,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextAction(
                    text = "Удалить…",
                    enabled = editable,
                    onClick = { askUninstall = true },
                    danger = true,
                )
            }
            Spacer(Modifier.height(Dimens.sm))
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            AccentButton(
                text = when {
                    !state.installed -> "Сохранить настройки"
                    else -> "Применить и перезапустить"
                },
                enabled = editable,
                onClick = {
                    onApply(
                        draft,
                        tgDraft.withLiveEnabled(state.tgConfig.enabled),
                        EngineListsStore.applyListDrafts(lists, oversizedNames, replacedOversized),
                    )
                },
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
        }
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

@Composable
private fun ProbeSummary(report: StrategyProbeReport) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        Text(
            text = report.winnerId?.let { "Лучшая: $it" } ?: "Подходящая стратегия не найдена",
            style = MaterialTheme.typography.bodyMedium,
            color = if (report.winnerId != null) Palette.accent else Palette.danger,
        )
        report.results.forEach { row ->
            val stab = row.targetStabilityPermille / 10
            val ping = row.avgTargetLatencyMs?.let { "${it}ms" } ?: "—"
            Text(
                text = "${row.strategyId}: ${row.score} · ${stab}% · $ping" +
                    " (D ${row.discord.successes}/${row.discord.attempts}" +
                    " Y ${row.youtube.successes}/${row.youtube.attempts}" +
                    " V ${row.googlevideo.successes}/${row.googlevideo.attempts}" +
                    " C ${row.control.successes}/${row.control.attempts})",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textMuted,
            )
        }
    }
}

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private fun openInTelegram(url: String) {
    if (!openUrl(url)) copyToClipboard(url)
}

private fun openUrl(url: String): Boolean = DesktopOpen.url(url)

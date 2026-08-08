package zapret.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import zapret.domain.Prerequisites
import zapret.ui.theme.Dimens
import zapret.ui.theme.Palette

@Composable
fun PrerequisitesCard(
    prerequisites: Prerequisites,
    onInstallCompiler: () -> Unit,
    mod: Modifier = Modifier,
) {
    if (prerequisites.isReady) return

    Surface(
        shape = RoundedCornerShape(Dimens.radiusCard),
        color = Palette.surface,
        modifier = mod.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            Text("Готовность", style = MaterialTheme.typography.titleMedium, color = Palette.text)
            CheckLine("Инструменты сборки (Xcode CLT)", prerequisites.hasCompiler)
            CheckLine("Исходники zapret2", prerequisites.hasSources)
            CheckLine(
                label = "Интерфейс WAN",
                ok = prerequisites.wanInterface != null,
                detail = prerequisites.wanInterface ?: "не найден",
            )
            CheckLine(
                label = "zapret2 установлен",
                ok = prerequisites.zapretInstalled,
                detail = if (prerequisites.zapretInstalled) "/opt/zapret2" else "нажмите кнопку питания",
            )
            CheckLine(
                label = "Вкл/выкл без пароля",
                ok = prerequisites.passwordlessControl,
                detail = if (prerequisites.passwordlessControl) "sudoers" else "опционально · Настройки",
            )

            if (!prerequisites.hasCompiler) {
                TextButton(onClick = onInstallCompiler) {
                    Text("Установить инструменты", color = Palette.accent)
                }
            }
            if (!prerequisites.hasSources) {
                Text(
                    text = "Переустановите приложение из DMG — внутри должны быть исходники.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.danger,
                )
            }
            if (!prerequisites.passwordlessControl) {
                Text(
                    text = "Права администратора спросят при установке и каждом start/stop, пока не включите «без пароля».",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.textMuted,
                )
            }
        }
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        Text(
            text = if (ok) "✓" else "!",
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) Palette.accent else Palette.danger,
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.text)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
            }
        }
    }
}

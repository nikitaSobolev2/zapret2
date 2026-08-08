package zapret.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zapret.domain.UpdatePhase
import zapret.ui.theme.Palette

@Composable
fun UpdateDialog(
    state: UiState,
    onCancel: () -> Unit,
) {
    if (!state.showUpdateModal) return

    val title = when (state.updatePhase) {
        UpdatePhase.Checking -> "Проверка обновлений"
        UpdatePhase.Downloading -> "Загрузка обновления"
        UpdatePhase.Applying -> "Установка обновления"
        else -> "Обновление Zapret"
    }
    val body = state.updateProgressLabel.ifBlank {
        when (state.updatePhase) {
            UpdatePhase.Checking -> "Ищем новую версию…"
            UpdatePhase.Downloading -> "Скачиваем Zapret…"
            UpdatePhase.Applying -> "Заменяем приложение и перезапускаем…"
            else -> "Обновление…"
        }
    }

    AlertDialog(
        onDismissRequest = { /* only Cancel */ },
        containerColor = Palette.surface,
        title = { Text(title, color = Palette.text) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.updateAvailable?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.appVersion} → ${it.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.textMuted,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = state.updatePhase != UpdatePhase.Applying,
            ) {
                Text("Отмена", color = Palette.textMuted)
            }
        },
    )
}

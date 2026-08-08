package zapret.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zapret.domain.UninstallScope
import zapret.ui.components.DangerButton
import zapret.ui.theme.Palette

@Composable
fun UninstallDialog(onChoose: (UninstallScope) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text("Удаление", color = Palette.text) },
        text = {
            Text(
                text = "«Только приложение» оставит zapret2 установленным и работающим. " +
                    "«Приложение и zapret2» снимет правила PF, уберёт автозапуск и удалит /opt/zapret2.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.textMuted,
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton(
                    text = UninstallScope.APP_AND_ZAPRET.label,
                    enabled = true,
                    onClick = { onChoose(UninstallScope.APP_AND_ZAPRET) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onChoose(UninstallScope.APP_ONLY) }, modifier = Modifier.fillMaxWidth()) {
                    Text(UninstallScope.APP_ONLY.label, color = Palette.text)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Palette.textMuted) }
        },
    )
}

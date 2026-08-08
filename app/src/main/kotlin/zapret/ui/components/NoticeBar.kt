package zapret.ui.components

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
import androidx.compose.ui.unit.dp
import zapret.ui.Notice
import zapret.ui.theme.Palette

@Composable
fun NoticeBar(notice: Notice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = if (notice.isError) Palette.danger else Palette.accent
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice.text,
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            TextButton(onClick = onDismiss) { Text("Скрыть", color = accent) }
        }
    }
}

package zapret.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zapret.ui.theme.Palette

/** A tappable row that shows one config value and leads to the matching settings section. */
@Composable
fun InfoCard(title: String, value: String, onClick: () -> Unit, mod: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Palette.surface,
        modifier = mod.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChevronRight(Palette.textMuted)
        }
    }
}

package zapret.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zapret.ui.theme.Palette

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(16.dp), color = Palette.surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.backgroundDeep,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textMuted,
                uncheckedTrackColor = Palette.surfaceRaised,
                uncheckedBorderColor = Palette.outline,
            ),
        )
    }
}

@Composable
fun ValueField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    minHeight: Dp = Dp.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Palette.textMuted) },
        singleLine = singleLine,
        textStyle = textStyle,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Palette.surfaceRaised,
            unfocusedContainerColor = Palette.surfaceRaised,
            focusedTextColor = Palette.text,
            unfocusedTextColor = Palette.text,
            cursorColor = Palette.accent,
            focusedIndicatorColor = Palette.accent,
            unfocusedIndicatorColor = Palette.outline,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
    )
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = Palette.accent, unselectedColor = Palette.textMuted),
        )
        Spacer(Modifier.padding(start = 4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.text)
    }
}

@Composable
fun AccentButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.accent,
            contentColor = Palette.backgroundDeep,
            disabledContainerColor = Palette.surfaceRaised,
            disabledContentColor = Palette.textMuted,
        ),
        modifier = modifier.height(46.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DangerButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.danger.copy(alpha = 0.16f),
            contentColor = Palette.danger,
            disabledContainerColor = Palette.surfaceRaised,
            disabledContentColor = Palette.textMuted,
        ),
        modifier = modifier.height(46.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

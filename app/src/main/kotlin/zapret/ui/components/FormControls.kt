package zapret.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zapret.ui.theme.Dimens
import zapret.ui.theme.Palette

@Composable
fun Section(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.textMuted,
            modifier = Modifier.padding(start = Dimens.xs, bottom = Dimens.xs),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textMuted.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = Dimens.xs, bottom = Dimens.sm),
            )
        } else {
            Spacer(Modifier.height(Dimens.xs))
        }
        Surface(
            shape = RoundedCornerShape(Dimens.radiusCard),
            color = Palette.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
                content = content,
            )
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val tint = rowTint(hovered = hovered, pressed = pressed, selected = false)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusField))
            .background(tint)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) }
            .padding(horizontal = Dimens.sm, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Dimens.md)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.text)
            if (description != null) {
                Spacer(Modifier.height(Dimens.xs))
                Text(description, style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
            }
        }
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
    description: String? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, color = Palette.textMuted) },
            singleLine = singleLine,
            textStyle = textStyle,
            shape = RoundedCornerShape(Dimens.radiusField),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.fieldFocus,
                unfocusedContainerColor = Palette.fieldIdle,
                focusedTextColor = Palette.text,
                unfocusedTextColor = Palette.text,
                cursorColor = Palette.accent,
                focusedIndicatorColor = Palette.accent,
                unfocusedIndicatorColor = Palette.outline,
                focusedLabelColor = Palette.accent,
                unfocusedLabelColor = Palette.textMuted,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textMuted,
                modifier = Modifier.padding(horizontal = Dimens.xs),
            )
        }
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, description: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val tint = rowTint(hovered = hovered, pressed = pressed, selected = selected)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusField))
            .background(tint)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Palette.accent.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(Dimens.radiusField),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(horizontal = Dimens.xs, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = Palette.accent, unselectedColor = Palette.textMuted),
        )
        Column(Modifier.padding(start = Dimens.xs)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.text)
            if (description != null) {
                Spacer(Modifier.height(Dimens.xs))
                Text(description, style = MaterialTheme.typography.labelSmall, color = Palette.textMuted)
            }
        }
    }
}

@Composable
fun AccentButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val container = when {
        !enabled -> Palette.surfaceRaised
        pressed -> Palette.accentDeep
        hovered -> Palette.accent.copy(alpha = 0.92f)
        else -> Palette.accent
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(Dimens.radiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Palette.backgroundDeep,
            disabledContainerColor = Palette.surfaceRaised,
            disabledContentColor = Palette.textMuted,
        ),
        modifier = modifier.height(Dimens.buttonHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DangerButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val container = when {
        !enabled -> Palette.surfaceRaised
        pressed -> Palette.danger.copy(alpha = 0.28f)
        hovered -> Palette.danger.copy(alpha = 0.22f)
        else -> Palette.danger.copy(alpha = 0.16f)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(Dimens.radiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Palette.danger,
            disabledContainerColor = Palette.surfaceRaised,
            disabledContentColor = Palette.textMuted,
        ),
        modifier = modifier.height(Dimens.buttonHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private fun rowTint(hovered: Boolean, pressed: Boolean, selected: Boolean): Color = when {
    pressed -> Palette.pressed
    hovered -> Palette.hover
    selected -> Palette.accent.copy(alpha = 0.10f)
    else -> Color.Transparent
}

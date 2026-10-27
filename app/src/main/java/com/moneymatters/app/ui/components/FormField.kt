package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * A labeled single-line input matching DESIGN.md's form-input look (52dp, outlined, `md` radius).
 * [error], when set, replaces the label and border color and shows beneath the field.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    error: String? = null,
    testTag: String? = null,
    focusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
) {
    val c = MoneyTheme.colors
    val textStyle: TextStyle = MoneyTheme.type.bodyLg.copy(color = c.ink)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MoneyTheme.type.labelRegular, color = if (error != null) c.negative else c.muted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MoneyTheme.shapes.md)
                .background(c.surfaceLowest)
                .border(1.dp, if (error != null) c.negative else c.outline, MoneyTheme.shapes.md)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = textStyle, color = c.muted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(c.anchor),
                singleLine = true,
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            )
        }
        if (error != null) {
            Text(error, style = MoneyTheme.type.bodySm, color = c.negative, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

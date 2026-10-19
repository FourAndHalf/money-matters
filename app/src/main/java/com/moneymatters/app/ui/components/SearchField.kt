package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * A search input matching DESIGN.md's form-input look (52dp, white fill, 1px outline,
 * `md` radius), with a leading search glyph and a clear button once there's text.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    val textStyle: TextStyle = MoneyTheme.type.bodyLg.copy(color = c.ink)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MoneyTheme.shapes.md)
            .background(c.surfaceLowest)
            .border(1.dp, c.outline, MoneyTheme.shapes.md)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MoneyIcons.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = c.muted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(c.anchor),
                singleLine = true,
                modifier = Modifier.testTag("search-field"),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                MoneyIcons.Close,
                "Clear search",
                tint = c.muted,
                modifier = Modifier
                    .size(18.dp)
                    .clip(MoneyTheme.shapes.full)
                    .clickable(role = Role.Button, onClickLabel = "Clear search") { onValueChange("") },
            )
        }
    }
}

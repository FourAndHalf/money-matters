package com.moneymatters.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * Top bar for a secondary (pushed) page: back button, title over an optional muted subtitle,
 * and one optional trailing action. Used by account ledgers, holding and transaction detail,
 * and any other page reached by drilling into the dashboard.
 */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MoneyTheme.spacing.margin, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(MoneyIcons.ArrowLeft, "Back", onBack, size = 40.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MoneyTheme.type.headlineMd, color = MoneyTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        action?.invoke()
    }
}

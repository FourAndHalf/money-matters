package com.moneymatters.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneymatters.app.feature.dashboard.DashboardScreen
import com.moneymatters.app.feature.dashboard.DashboardState
import com.moneymatters.app.ui.components.MoneyBottomBar
import com.moneymatters.app.ui.components.NavItem
import com.moneymatters.app.ui.components.QuickAddFab
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

private val Tabs = listOf(
    NavItem("Home", MoneyIcons.Home),
    NavItem("Txns", MoneyIcons.Transactions),
    NavItem("Accounts", MoneyIcons.Accounts),
    NavItem("Invest", MoneyIcons.Invest),
    NavItem("More", MoneyIcons.More),
)

/** App shell: canvas, current tab, Quick Add and the floating bottom bar. */
@Composable
fun MoneyMattersApp(dashboard: DashboardState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val c = MoneyTheme.colors
    val canvas = if (c.isDark) Brush.verticalGradient(listOf(c.canvas, c.canvas)) else Brush.verticalGradient(0f to Color(0xFFF0F0F2), 0.45f to c.canvas)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .background(canvas),
    ) {
        when (tab) {
            0 -> DashboardScreen(
                dashboard,
                onNavigate = {},
                // Clears the floating bar and Quick Add.
                contentPadding = PaddingValues(bottom = 132.dp),
            )
            else -> Text(
                "${Tabs[tab].label} is not built yet",
                style = MoneyTheme.type.bodyMd,
                color = c.muted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 14.dp),
        ) {
            MoneyBottomBar(Tabs, tab, onSelect = { tab = it })
        }
        QuickAddFab(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 98.dp),
        )
    }
}

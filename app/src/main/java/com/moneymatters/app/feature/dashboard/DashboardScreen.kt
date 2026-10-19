package com.moneymatters.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.components.CircleIconButton
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme

/** Navigation targets the dashboard can ask for. Pages behind them are not built yet. */
enum class DashboardDestination {
    Insights, Notifications, Profile, Leeway, Accounts, Account, Runway, Budgets, Spending,
    Calendar, Goals, Portfolio, Insight, CloseMonth, Drafts, Uncategorized, InTransit, WeeklyReview, Tax,
}

/**
 * Home (P03). Order follows design/mockups/dashboard.html: leeway first, then the numbers
 * that explain it, then the books' housekeeping at the end.
 */
@Composable
fun DashboardScreen(
    state: DashboardState,
    onNavigate: (DashboardDestination) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenAccounts: () -> Unit = { onNavigate(DashboardDestination.Accounts) },
    onOpenAccount: (accountId: String) -> Unit = { onNavigate(DashboardDestination.Account) },
    onOpenTransactions: () -> Unit = { onNavigate(DashboardDestination.Drafts) },
) {
    val margin = MoneyTheme.spacing.margin
    val gutter = MoneyTheme.spacing.gutter
    val status = WindowInsets.statusBars.asPaddingValues()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = status.calculateTopPadding() + contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(gutter),
    ) {
        padded(margin, "header") { Header(state, onNavigate) }
        padded(margin, "leeway") { LeewayHero(state.leeway, onOpen = { onNavigate(DashboardDestination.Leeway) }) }
        item("kpis") { KpiStrip(state.kpis) }
        padded(margin, "accounts") {
            AccountsSection(
                state.accounts,
                onViewAll = onOpenAccounts,
                onAction = { account -> onOpenAccount(account.id) },
            )
        }
        padded(margin, "runway") { RunwayModule(state.runway) { onNavigate(DashboardDestination.Runway) } }
        padded(margin, "budgets") { BudgetsModule(state.budgets) { onNavigate(DashboardDestination.Budgets) } }
        padded(margin, "spending") { SpendingModule(state.spending) { onNavigate(DashboardDestination.Spending) } }
        padded(margin, "upcoming") { UpcomingModule(state.upcoming) { onNavigate(DashboardDestination.Calendar) } }
        padded(margin, "goals") { GoalsModule(state.goals) { onNavigate(DashboardDestination.Goals) } }
        padded(margin, "portfolio") { PortfolioModule(state.portfolio) { onNavigate(DashboardDestination.Portfolio) } }
        padded(margin, "insights") {
            InsightsModule(
                state.insights,
                onAll = { onNavigate(DashboardDestination.Insights) },
                onAction = { onNavigate(DashboardDestination.Insight) },
                onDismiss = {},
            )
        }
        padded(margin, "attention") {
            AttentionModule(state.attention) { item ->
                when (item.kind) {
                    // Drafts and uncategorized entries both live in the Transactions list.
                    AttentionKind.Drafts, AttentionKind.Uncategorized -> onOpenTransactions()
                    AttentionKind.CloseMonth -> onNavigate(DashboardDestination.CloseMonth)
                    AttentionKind.InTransit -> onNavigate(DashboardDestination.InTransit)
                    AttentionKind.WeeklyReview -> onNavigate(DashboardDestination.WeeklyReview)
                    AttentionKind.AdvanceTax -> onNavigate(DashboardDestination.Tax)
                }
            }
        }
    }
}

private fun LazyListScope.padded(margin: androidx.compose.ui.unit.Dp, key: String, content: @Composable () -> Unit) {
    item(key) { Box(Modifier.padding(horizontal = margin)) { content() } }
}

@Composable
private fun Header(state: DashboardState, onNavigate: (DashboardDestination) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.greeting, style = MoneyTheme.type.headlineXlMobile, color = MoneyTheme.colors.ink)
            Text(state.dateLabel, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 3.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(MoneyIcons.Sparkle, "Insights", { onNavigate(DashboardDestination.Insights) })
            CircleIconButton(MoneyIcons.Bell, "Notifications", { onNavigate(DashboardDestination.Notifications) }, showDot = state.hasUnreadAlerts)
            Avatar(state.profileInitial)
        }
    }
}

@Composable
private fun Avatar(initial: String) {
    Box(
        Modifier
            .size(44.dp)
            .clip(MoneyTheme.shapes.full)
            .background(Brush.linearGradient(listOf(Color(0xFFE2D8C2), Color(0xFFA8997A))))
            .semantics { contentDescription = "Profile" },
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MoneyTheme.type.bodyLg.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

@Preview(widthDp = 392, heightDp = 2400, showBackground = true, backgroundColor = 0xFFE8E8EA)
@Composable
private fun DashboardPreview() {
    MoneyMattersTheme(darkTheme = false) { DashboardScreen(SampleDashboard.state, onNavigate = {}) }
}

package com.moneymatters.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.moneymatters.app.R

private val Wide = DpSize(110.dp, 40.dp)
private val Square = DpSize(110.dp, 110.dp)

/** Two buttons: add a transaction (opens Quick Add) and view the Accounts tab. Side by side at 2×1, stacked at 2×2. */
class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Wide, Square))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickActionsContent() }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

/** The same widget offered as its own 2×2 entry in the picker. */
class QuickActionsStackedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

@Composable
private fun QuickActionsContent() {
    val context = LocalContext.current
    val add = GlanceModifier.clickable(actionStartActivity(launchIntent(context, ACTION_ADD_ENTRY)))
    val accounts = GlanceModifier.clickable(actionStartActivity(launchIntent(context, ACTION_OPEN_ACCOUNTS)))
    // One rounded card behind both buttons, a step darker than the unfilled button so each still reads against it.
    Box(GlanceModifier.fillMaxSize().background(widgetColor { it.surfaceLow }).cornerRadius(24.dp).padding(6.dp)) {
        if (LocalSize.current.height >= Square.height) StackedButtons(add, accounts) else SideBySideButtons(add, accounts)
    }
}

@Composable
private fun StackedButtons(add: GlanceModifier, accounts: GlanceModifier) {
    Column(GlanceModifier.fillMaxSize()) {
        ActionButton(R.drawable.ic_widget_add, "Add a transaction", filled = true, add.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(6.dp))
        ActionButton(R.drawable.ic_widget_accounts, "Accounts", filled = false, accounts.fillMaxWidth().defaultWeight())
    }
}

@Composable
private fun SideBySideButtons(add: GlanceModifier, accounts: GlanceModifier) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        ActionButton(R.drawable.ic_widget_add, "Add a transaction", filled = true, add.defaultWeight().fillMaxHeight())
        Spacer(GlanceModifier.width(6.dp))
        ActionButton(R.drawable.ic_widget_accounts, "Accounts", filled = false, accounts.defaultWeight().fillMaxHeight())
    }
}

@Composable
private fun ActionButton(icon: Int, description: String, filled: Boolean, modifier: GlanceModifier) {
    Box(
        modifier
            .background(widgetColor { if (filled) it.anchor else it.surfaceLowest })
            .cornerRadius(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(widgetColor { if (filled) it.onAnchor else it.ink }),
            modifier = GlanceModifier.size(26.dp),
        )
    }
}

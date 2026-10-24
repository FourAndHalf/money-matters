package com.moneymatters.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/** Two buttons: add a transaction (opens Quick Add) and view the Accounts tab. */
class QuickActionsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickActionsContent() }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

@Composable
private fun QuickActionsContent() {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton("+ Add", filled = true, GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent(context, ACTION_ADD_ENTRY))))
        Spacer(GlanceModifier.width(6.dp))
        ActionButton("Accounts", filled = false, GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent(context, ACTION_OPEN_ACCOUNTS))))
    }
}

@Composable
private fun ActionButton(text: String, filled: Boolean, modifier: GlanceModifier) {
    Box(
        modifier
            .fillMaxHeight()
            .background(widgetColor { if (filled) it.anchor else it.surfaceLowest })
            .cornerRadius(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(
                color = widgetColor { if (filled) it.onAnchor else it.ink },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 1,
        )
    }
}

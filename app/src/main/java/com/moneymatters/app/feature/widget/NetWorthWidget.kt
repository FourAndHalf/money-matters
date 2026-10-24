package com.moneymatters.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.money.formatInrCompact
import com.moneymatters.app.feature.settings.NumberFormat
import com.moneymatters.app.feature.settings.SettingsPrefs
import com.moneymatters.app.ui.theme.DarkMoneyColors
import com.moneymatters.app.ui.theme.LightMoneyColors
import com.moneymatters.app.ui.theme.MoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OneRow = DpSize(250.dp, 40.dp)
private val TwoRows = DpSize(250.dp, 110.dp)

/** The widgets can't use Compose theming, so they take the app's light and dark palettes directly. */
internal fun widgetColor(pick: (MoneyColors) -> Color) = ColorProvider(day = pick(LightMoneyColors), night = pick(DarkMoneyColors))

/**
 * Net worth, as on Home. One row tall (4×1) it is the figure alone; two rows tall (4×2) it adds assets and liabilities.
 * Tapping opens the app, which asks for the passcode as usual.
 */
class NetWorthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(OneRow, TwoRows))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, format) = withContext(Dispatchers.IO) { WidgetStore.encrypted(context).read() to SettingsPrefs(context).numberFormat }
        provideContent { NetWorthContent(snapshot, format) }
    }
}

class NetWorthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetWorthWidget()
}

private fun Paise.widgetText(format: NumberFormat) = if (format == NumberFormat.Compact) formatInrCompact() else formatInr()

@Composable
private fun NetWorthContent(snapshot: WidgetSnapshot?, format: NumberFormat) {
    val context = LocalContext.current
    val tall = LocalSize.current.height >= TwoRows.height
    val label = TextStyle(color = widgetColor { it.muted }, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(widgetColor { it.surfaceLowest })
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(actionStartActivity(launchIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (snapshot == null) {
            Text("Net worth", style = label)
            Text("Open Money Matters to start", style = TextStyle(color = widgetColor { it.ink }, fontSize = 14.sp, fontFamily = FontFamily.Monospace))
            return@Column
        }
        Text("Net worth", style = label)
        Text(
            snapshot.netWorth.widgetText(format),
            style = TextStyle(color = widgetColor { it.ink }, fontSize = if (tall) 30.sp else 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            maxLines = 1,
        )
        if (tall) {
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("Assets", style = label)
                    Text(snapshot.assets.widgetText(format), style = TextStyle(color = widgetColor { it.ink }, fontSize = 14.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
                }
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                    Text("Liabilities", style = label)
                    Text(snapshot.liabilities.widgetText(format), style = TextStyle(color = widgetColor { it.negative }, fontSize = 14.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
                }
            }
        }
    }
}

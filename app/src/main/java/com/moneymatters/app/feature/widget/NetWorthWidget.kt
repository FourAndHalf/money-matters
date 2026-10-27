package com.moneymatters.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.color.ColorProvider
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.ui.theme.DarkMoneyColors
import com.moneymatters.app.ui.theme.LightMoneyColors
import com.moneymatters.app.ui.theme.MoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The widgets can't use Compose theming, so they take the app's light and dark palettes directly. */
internal fun widgetColor(pick: (MoneyColors) -> Color) = ColorProvider(day = pick(LightMoneyColors), night = pick(DarkMoneyColors))

/**
 * Net worth, as on Home: the same card, drawn by [NetWorthCardRenderer]. One row tall (4×1) it is the figure and the split bar;
 * two rows tall (4×2) it adds assets and liabilities. Tapping opens the app, which asks for the passcode as usual.
 */
class NetWorthWidget : GlanceAppWidget() {
    // Exact, so the card is drawn at the size it is shown at rather than stretched from a nearby one.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, lock) = withContext(Dispatchers.IO) {
            val passcodes = PasscodeStore.encrypted(context)
            WidgetStore.encrypted(context).read() to LockState(passcodes.isSet(), passcodes.isSet() && passcodes.isLockEnabled())
        }
        provideContent { NetWorthContent(snapshot, lock) }
    }
}

class NetWorthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetWorthWidget()
}

internal const val HIDDEN_AMOUNT = "₹ ••••••"

/** [hidden] is the lock being on (and so the figures masked); [hasPasscode] says whether it can be switched on at all. */
private data class LockState(val hasPasscode: Boolean, val hidden: Boolean)

/** Switches the app lock on from the widget, which masks the figures too. Switching it off goes through the app and its passcode. */
class EnableLockCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) { PasscodeStore.encrypted(context).setLockEnabled(true) }
        NetWorthWidget().updateAll(context)
    }
}

/** With the app lock on, a home screen shouldn't read out the books: every figure becomes [HIDDEN_AMOUNT]. */
internal fun Paise.widgetText(hidden: Boolean = false) = if (hidden) HIDDEN_AMOUNT else formatInr()

@Composable
private fun NetWorthContent(snapshot: WidgetSnapshot?, lock: LockState) {
    val hidden = lock.hidden
    val context = LocalContext.current
    val size = LocalSize.current
    val card = remember(size, snapshot, hidden) { NetWorthCardRenderer.render(context, size, snapshot, hidden) }
    val description = when {
        snapshot == null -> "Net worth"
        hidden -> "Net worth, hidden while the app lock is on"
        else -> "Net worth ${snapshot.netWorth.widgetText()}, assets ${snapshot.assets.widgetText()}, liabilities ${snapshot.liabilities.widgetText()}"
    }
    // On: off needs the passcode, so it opens the app. Off: switch the lock on in place. No passcode yet: just open the app.
    val toggleAction = when {
        lock.hidden -> actionStartActivity(launchIntent(context, ACTION_DISABLE_LOCK))
        lock.hasPasscode -> actionRunCallback<EnableLockCallback>()
        else -> actionStartActivity(launchIntent(context))
    }
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Image(
            ImageProvider(card),
            contentDescription = description,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context))),
        )
        Box(GlanceModifier.size(width = 72.dp, height = 44.dp).clickable(toggleAction)) {}
    }
}

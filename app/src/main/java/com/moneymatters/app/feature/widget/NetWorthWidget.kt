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
import androidx.glance.layout.Spacer
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
        val (snapshot, mask) = withContext(Dispatchers.IO) {
            val passcodes = PasscodeStore.encrypted(context)
            val appLockOn = passcodes.isSet() && passcodes.isLockEnabled()
            WidgetStore.encrypted(context).read() to MaskState(appLockOn, userMasked = WidgetMask.of(context).isMasked())
        }
        provideContent { NetWorthContent(snapshot, mask) }
    }
}

class NetWorthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetWorthWidget()
}

internal const val HIDDEN_AMOUNT = "₹ ••••••"

/**
 * Why the figures are masked, if they are. With the app's encryption lock on they always are, and the toggle is stuck on until
 * the lock is switched off in the app. With the lock off, the toggle on the card decides, and flipping it never touches the lock.
 */
private data class MaskState(val appLockOn: Boolean, val userMasked: Boolean) {
    val hidden: Boolean get() = appLockOn || userMasked
}

/** Flips the card's own mask toggle. The app lock is not involved, so this is one small write and a redraw. */
class ToggleMaskCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            withContext(Dispatchers.IO) { WidgetMask.of(context).let { it.setMasked(!it.isMasked()) } }
        } finally {
            NetWorthWidget().updateAll(context)
        }
    }
}

/** Masked (by the app lock or the card's toggle), a home screen doesn't read out the books: every figure becomes [HIDDEN_AMOUNT]. */
internal fun Paise.widgetText(hidden: Boolean = false) = if (hidden) HIDDEN_AMOUNT else formatInr()

@Composable
private fun NetWorthContent(snapshot: WidgetSnapshot?, mask: MaskState) {
    val hidden = mask.hidden
    val context = LocalContext.current
    val size = LocalSize.current
    val card = remember(size, snapshot, hidden) { NetWorthCardRenderer.render(context, size, snapshot, hidden) }
    val description = when {
        snapshot == null -> "Net worth"
        mask.appLockOn -> "Net worth, hidden while the app lock is on"
        hidden -> "Net worth, hidden. Tap the switch to show it"
        else -> "Net worth ${snapshot.netWorth.widgetText()}, assets ${snapshot.assets.widgetText()}, liabilities ${snapshot.liabilities.widgetText()}"
    }
    // App lock on: the switch is stuck on, so a tap on it just opens the app like the rest of the card. Otherwise it flips the mask.
    val toggleAction = if (mask.appLockOn) actionStartActivity(launchIntent(context)) else actionRunCallback<ToggleMaskCallback>()
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Image(
            ImageProvider(card),
            contentDescription = description,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(launchIntent(context))),
        )
        // Invisible, over the switch drawn in the card. Generous, since a thumb misses a 30×18dp switch; the Spacer gives the view content.
        Box(GlanceModifier.size(width = 96.dp, height = 48.dp).clickable(toggleAction)) { Spacer(GlanceModifier.fillMaxSize()) }
    }
}

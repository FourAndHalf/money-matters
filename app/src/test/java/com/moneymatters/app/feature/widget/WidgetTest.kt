package com.moneymatters.app.feature.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.feature.settings.NumberFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun store() = WidgetStore(context.getSharedPreferences("widget_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() })

    @Test fun `nothing is shown until the app has saved totals`() {
        assertNull(store().read())
    }

    @Test fun `saved totals read back exactly, negative net worth included, and a later save replaces them`() {
        val store = store()
        store.save(WidgetSnapshot(Paise(-1_234_56), Paise(9_000_00), Paise(10_234_56), 1_000))
        assertEquals(WidgetSnapshot(Paise(-1_234_56), Paise(9_000_00), Paise(10_234_56), 1_000), store.read())
        store.save(WidgetSnapshot(Paise(5_00), Paise(5_00), Paise.Zero, 2_000))
        assertEquals(Paise(5_00), store.read()!!.netWorth)
        assertEquals(2_000L, store.read()!!.updatedAtMillis)
    }

    @Test fun `figures show normally and become dots when the app lock is on`() {
        val amount = Paise(2_40_000_00)
        assertEquals("₹2,40,000", amount.widgetText(NumberFormat.Full))
        assertEquals("₹2.4L", amount.widgetText(NumberFormat.Compact))
        assertEquals(HIDDEN_AMOUNT, amount.widgetText(NumberFormat.Full, hidden = true))
        assertEquals(HIDDEN_AMOUNT, Paise(-5_00).widgetText(NumberFormat.Compact, hidden = true))
    }

    @Test fun `each widget button's intent maps to its page, and anything else to none`() {
        assertEquals(LaunchTarget.AddEntry, LaunchTarget.from(launchIntent(context, ACTION_ADD_ENTRY)))
        assertEquals(LaunchTarget.Accounts, LaunchTarget.from(launchIntent(context, ACTION_OPEN_ACCOUNTS)))
        assertNull(LaunchTarget.from(launchIntent(context)))
        assertNull(LaunchTarget.from(Intent(Intent.ACTION_VIEW)))
        assertNull(LaunchTarget.from(null))
    }

    @Test fun `the launch intent reuses the running app instead of stacking another`() {
        val flags = launchIntent(context, ACTION_ADD_ENTRY).flags
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP, flags and Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

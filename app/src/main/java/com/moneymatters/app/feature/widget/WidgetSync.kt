package com.moneymatters.app.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.moneymatters.app.feature.dashboard.HomeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves Home's totals for the widget and redraws every placed copy of it. */
suspend fun syncNetWorthWidget(context: Context, store: WidgetStore, home: HomeState, nowMillis: Long = System.currentTimeMillis()) {
    withContext(Dispatchers.IO) { store.save(WidgetSnapshot(home.netWorth, home.assetsTotal, home.liabilitiesTotal, nowMillis)) }
    NetWorthWidget().updateAll(context)
}

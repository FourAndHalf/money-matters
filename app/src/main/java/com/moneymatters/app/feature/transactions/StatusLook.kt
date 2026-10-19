package com.moneymatters.app.feature.transactions

import androidx.compose.ui.graphics.vector.ImageVector
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.icons.MoneyIcons

/** How a [TxnStatus] renders as a [com.moneymatters.app.ui.components.StatusBadge]. Null means no badge (Posted is the quiet default). */
internal data class StatusLook(val text: String, val tone: Tone, val icon: ImageVector?)

internal fun TxnStatus.look(): StatusLook? = when (this) {
    TxnStatus.Draft -> StatusLook("Draft", Tone.Info, null)
    TxnStatus.Posted -> null
    TxnStatus.Reconciled -> StatusLook("Reconciled", Tone.Positive, null)
    TxnStatus.Locked -> StatusLook("Locked", Tone.Neutral, MoneyIcons.Lock)
    TxnStatus.PriorPeriodAdjustment -> StatusLook("Prior-period", Tone.Warning, null)
}

package com.moneymatters.app.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.components.NumberKeypad
import com.moneymatters.app.ui.components.PasscodeDots
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme
import kotlinx.coroutines.delay

/**
 * Lock screen (P02): passcode keypad, plus biometrics when [onRequestBiometric] is given. The
 * system prompt is offered once each time the app locks; the fingerprint key re-offers it.
 */
@Composable
fun LockScreen(lock: AppLock, modifier: Modifier = Modifier, onRequestBiometric: (() -> Unit)? = null) {
    val c = MoneyTheme.colors
    LaunchedEffect(lock.lockCount) {
        if (lock.lockCount > 0) onRequestBiometric?.invoke()
    }

    // Re-read the remaining lockout once a second while it runs.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lock.lockedOutUntil) {
        while (lock.lockoutRemainingMillis() > 0) {
            delay(1_000)
            tick++
        }
        tick++
    }
    val lockoutSeconds = tick.let { (lock.lockoutRemainingMillis() + 999) / 1000 }
    val lockedOut = lockoutSeconds > 0

    Column(
        modifier
            .fillMaxSize()
            .background(c.canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(MoneyTheme.spacing.xl))
        Box(
            Modifier.size(56.dp).clip(MoneyTheme.shapes.full).background(c.surfaceLowest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MoneyIcons.Lock, null, tint = c.ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        Text("Enter passcode", style = MoneyTheme.type.headlineLg, color = c.ink)
        Spacer(Modifier.height(MoneyTheme.spacing.sm))
        val (status, statusColor) = when {
            lockedOut -> "Too many tries. Try again in ${formatWait(lockoutSeconds)}" to c.negative
            lock.isChecking -> "Checking…" to c.muted
            lock.lastAttemptWrong && lock.attemptsBeforeLockout > 0 ->
                "Wrong passcode · ${lock.attemptsBeforeLockout} ${if (lock.attemptsBeforeLockout == 1) "try" else "tries"} left" to c.negative
            lock.lastAttemptWrong -> "Wrong passcode" to c.negative
            else -> "Money Matters is locked" to c.muted
        }
        Text(
            status,
            style = MoneyTheme.type.bodyMd,
            color = statusColor,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(MoneyTheme.spacing.lg))
        PasscodeDots(lock.passcodeLength, lock.entered.length, isError = lock.lastAttemptWrong && lock.entered.isEmpty())
        Spacer(Modifier.height(MoneyTheme.spacing.xl))
        NumberKeypad(
            onDigit = lock::onDigit,
            onBackspace = lock::onBackspace,
            enabled = !lockedOut && !lock.isChecking,
            onBiometric = onRequestBiometric,
        )
        Spacer(Modifier.height(MoneyTheme.spacing.xl))
    }
}

/** 28 → "28s", 125 → "2:05". */
internal fun formatWait(seconds: Long): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

package com.moneymatters.app.feature.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.security.FREE_ATTEMPTS
import com.moneymatters.app.core.security.PasscodeStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whether the app is locked, and the lock screen's keypad state (plan.html → P02). Locked from a
 * cold start whenever a passcode exists, and again once the app has been in the background for
 * at least [PasscodeStore.lockTimeout]. A [ViewModel] so a rotation doesn't unlock the app.
 */
class AppLock(
    private val store: PasscodeStore,
    startLocked: Boolean = store.isSet(),
    private val now: () -> Long = System::currentTimeMillis,
    private val hashing: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    var isLocked by mutableStateOf(startLocked)
        private set
    var passcodeLength by mutableIntStateOf(store.passcodeLength())
        private set
    var entered by mutableStateOf("")
        private set
    var isChecking by mutableStateOf(false)
        private set
    var lastAttemptWrong by mutableStateOf(false)
        private set
    var failedAttempts by mutableIntStateOf(store.failedAttempts())
        private set
    var lockedOutUntil by mutableLongStateOf(store.lockedOutUntil())
        private set

    /** Bumped on every lock, so the screen offers the biometric prompt once per lock. */
    var lockCount by mutableIntStateOf(if (isLocked) 1 else 0)
        private set

    private var backgroundedAt: Long? = null

    fun lockoutRemainingMillis(): Long = (lockedOutUntil - now()).coerceAtLeast(0)

    /** Tries left before the backoff starts; only worth showing after a miss. */
    val attemptsBeforeLockout get() = (FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0)

    fun onAppBackgrounded() {
        if (!isLocked) backgroundedAt = now()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (store.isSet() && now() - since >= store.lockTimeout().millis) lock()
    }

    fun lock() {
        isLocked = true
        passcodeLength = store.passcodeLength()
        entered = ""
        lastAttemptWrong = false
        lockCount++
    }

    fun onDigit(digit: Char) {
        if (!isLocked || isChecking || lockoutRemainingMillis() > 0 || entered.length >= passcodeLength) return
        entered += digit
        lastAttemptWrong = false
        if (entered.length == passcodeLength) check(entered)
    }

    fun onBackspace() {
        if (!isChecking) entered = entered.dropLast(1)
    }

    fun onBiometricSuccess() {
        if (!isLocked) return
        viewModelScope.launch {
            withContext(hashing) { store.clearFailedAttempts() }
            unlock()
        }
    }

    private fun check(attempt: String) {
        isChecking = true
        viewModelScope.launch {
            val ok = withContext(hashing) {
                store.verify(attempt).also { ok -> if (ok) store.clearFailedAttempts() else store.recordFailedAttempt(now()) }
            }
            isChecking = false
            if (ok) {
                unlock()
            } else {
                failedAttempts = store.failedAttempts()
                lockedOutUntil = store.lockedOutUntil()
                lastAttemptWrong = true
                entered = ""
            }
        }
    }

    private fun unlock() {
        isLocked = false
        entered = ""
        lastAttemptWrong = false
        failedAttempts = 0
        lockedOutUntil = 0
    }
}

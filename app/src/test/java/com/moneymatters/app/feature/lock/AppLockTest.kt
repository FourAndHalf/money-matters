package com.moneymatters.app.feature.lock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.security.LockTimeout
import com.moneymatters.app.core.security.PasscodeStore
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLockTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("lock_test", Context.MODE_PRIVATE)
    private val store = PasscodeStore(prefs)
    private var clock = 1_000_000L

    // Unconfined hashing + Main.immediate on Robolectric's main thread: each check finishes inline.
    private fun newLock(startLocked: Boolean = store.isSet()) = AppLock(store, startLocked, now = { clock }, hashing = Dispatchers.Unconfined)

    private fun AppLock.type(code: String) = code.forEach(::onDigit)

    @Before fun setUp() {
        store.save("4829", biometricEnabled = true, timeout = LockTimeout.OneMinute)
    }

    @Test fun `starts locked once a passcode exists, and not before`() {
        assertTrue(newLock().isLocked)
        prefs.edit().clear().commit()
        assertFalse(newLock().isLocked)
    }

    @Test fun `the right passcode unlocks`() {
        val lock = newLock()
        lock.type("4829")
        assertFalse(lock.isLocked)
        assertEquals("", lock.entered)
    }

    @Test fun `a wrong passcode clears the dots and counts down the free tries`() {
        val lock = newLock()
        lock.type("1111")
        assertTrue(lock.isLocked)
        assertTrue(lock.lastAttemptWrong)
        assertEquals("", lock.entered)
        assertEquals(4, lock.attemptsBeforeLockout)
    }

    @Test fun `typing stops at the passcode length`() {
        val lock = newLock()
        lock.type("12")
        lock.onBackspace()
        assertEquals("1", lock.entered)
    }

    @Test fun `five misses lock the keypad until the backoff passes`() {
        val lock = newLock()
        repeat(5) { lock.type("0000") }
        assertEquals(30_000, lock.lockoutRemainingMillis())
        lock.type("4829")
        assertTrue("ignored while locked out", lock.isLocked)
        assertEquals("", lock.entered)
        clock += 30_000
        lock.type("4829")
        assertFalse(lock.isLocked)
        assertEquals(0, store.failedAttempts())
    }

    @Test fun `killing the app doesn't reset the backoff`() {
        repeat(5) { newLock().type("0000") }
        val relaunched = newLock()
        assertEquals(30_000, relaunched.lockoutRemainingMillis())
        assertEquals(0, relaunched.attemptsBeforeLockout)
    }

    @Test fun `locks again only after the timeout in the background`() {
        val lock = newLock(startLocked = false)
        lock.onAppBackgrounded()
        clock += 59_999
        lock.onAppForegrounded()
        assertFalse(lock.isLocked)

        lock.onAppBackgrounded()
        clock += 60_000
        lock.onAppForegrounded()
        assertTrue(lock.isLocked)
    }

    @Test fun `Immediately locks on every return`() {
        store.setLockTimeout(LockTimeout.Immediately)
        val lock = newLock(startLocked = false)
        lock.onAppBackgrounded()
        lock.onAppForegrounded()
        assertTrue(lock.isLocked)
    }

    @Test fun `each lock bumps the count that re-offers biometrics`() {
        val lock = newLock()
        assertEquals(1, lock.lockCount)
        lock.type("4829")
        lock.lock()
        assertEquals(2, lock.lockCount)
    }

    @Test fun `biometric success unlocks and clears misses`() {
        val lock = newLock()
        lock.type("0000")
        lock.onBiometricSuccess()
        assertFalse(lock.isLocked)
        assertEquals(0, store.failedAttempts())
    }

    @Test fun `formats the wait`() {
        assertEquals("28s", formatWait(28))
        assertEquals("1:00", formatWait(60))
        assertEquals("2:05", formatWait(125))
    }
}

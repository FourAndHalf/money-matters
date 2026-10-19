package com.moneymatters.app.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Plain prefs stand in for the encrypted file: Robolectric has no Android Keystore.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PasscodeStoreTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("passcode_test", Context.MODE_PRIVATE)
    private val store = PasscodeStore(prefs)

    @Test fun `nothing is set until saved`() {
        assertFalse(store.isSet())
        assertFalse(store.verify("1234"))
    }

    @Test fun `verifies the right passcode and rejects others`() {
        store.save("482910", biometricEnabled = true)
        assertTrue(store.isSet())
        assertTrue(store.verify("482910"))
        assertFalse(store.verify("482911"))
        assertFalse(store.verify(""))
        assertTrue(store.isBiometricEnabled())
    }

    @Test fun `never stores the passcode in the clear`() {
        store.save("482910", biometricEnabled = false)
        prefs.all.values.forEach { assertFalse(it.toString().contains("482910")) }
        assertFalse(store.isBiometricEnabled())
    }

    @Test fun `each save uses a fresh salt, so the same passcode hashes differently`() {
        store.save("1234", biometricEnabled = false)
        val first = prefs.getString("passcode_hash", null)
        store.save("1234", biometricEnabled = false)
        assertNotEquals(first, prefs.getString("passcode_hash", null))
        assertTrue(store.verify("1234"))
    }
}

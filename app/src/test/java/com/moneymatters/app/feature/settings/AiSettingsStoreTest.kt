package com.moneymatters.app.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiSettingsStoreTest {

    private val store = AiSettingsStore(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ai_settings_test", Context.MODE_PRIVATE),
    )

    @Test fun `defaults are off, no key, and preview on`() {
        assertFalse(store.enabled)
        assertNull(store.apiKey)
        assertTrue(store.payloadPreview)
    }

    @Test fun `every field round-trips, and a blank key clears it`() {
        store.enabled = true
        store.apiKey = "sk-test-123"
        store.payloadPreview = false
        assertTrue(store.enabled)
        assertEquals("sk-test-123", store.apiKey)
        assertFalse(store.payloadPreview)

        store.apiKey = ""
        assertNull(store.apiKey)
    }
}

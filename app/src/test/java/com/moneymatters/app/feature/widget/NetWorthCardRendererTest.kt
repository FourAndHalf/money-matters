package com.moneymatters.app.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.Paise
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h852dp-night-xxhdpi")
class NetWorthCardRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val snapshot = WidgetSnapshot(Paise(1_66_005_00), Paise(2_37_787_00), Paise(71_782_00), 0)
    private val twoRows = DpSize(340.dp, 150.dp)
    private val oneRow = DpSize(340.dp, 70.dp)

    private fun render(size: DpSize, hidden: Boolean = false, snap: WidgetSnapshot? = snapshot) =
        NetWorthCardRenderer.render(context, size, snap, hidden)

    private fun Bitmap.save(name: String) {
        val dir = File("build/outputs/widget").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun Bitmap.sameAs(other: Bitmap) = width == other.width && height == other.height && sameAsPixels(other)
    private fun Bitmap.sameAsPixels(other: Bitmap): Boolean {
        val a = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        val b = IntArray(other.width * other.height).also { other.getPixels(it, 0, other.width, 0, 0, other.width, other.height) }
        return a.contentEquals(b)
    }

    @Test fun `the bitmap is exactly the widget's size in pixels`() {
        val bitmap = render(twoRows)
        assertEquals((340 * 3), bitmap.width)
        assertEquals((150 * 3), bitmap.height)
    }

    @Test fun `corners stay transparent so the rounded card shows the wallpaper`() {
        val bitmap = render(twoRows)
        assertEquals(0, bitmap.getPixel(2, 2) ushr 24)
        assertTrue(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) ushr 24 > 0)
    }

    @Test fun `hiding the amounts changes the picture, as do the sizes and an empty snapshot`() {
        val shown = render(twoRows)
        assertFalse(shown.sameAs(render(twoRows, hidden = true)))
        assertFalse(shown.sameAs(render(twoRows, snap = null)))
        assertFalse(render(oneRow).sameAs(render(oneRow, hidden = true)))
    }

    @Test fun `different totals draw different bars`() {
        val skewed = snapshot.copy(assets = Paise(10_000_00), liabilities = Paise(90_000_00))
        assertFalse(render(twoRows).sameAs(render(twoRows, snap = skewed)))
    }

    @Test fun `a huge figure still renders within the card`() {
        val big = snapshot.copy(netWorth = Paise(123_45_67_890_00), assets = Paise(123_45_67_890_00))
        assertEquals(340 * 3, render(twoRows, snap = big).width)
    }

    @Test fun `writes the cards out for looking at`() {
        render(twoRows).save("net-worth-4x2-dark")
        render(oneRow).save("net-worth-4x1-dark")
        render(twoRows, hidden = true).save("net-worth-4x2-hidden")
        render(oneRow, hidden = true).save("net-worth-4x1-hidden")
        render(DpSize(340.dp, 120.dp), snap = snapshot.copy(netWorth = Paise(-12_345_00), assets = Paise(5_000_00), liabilities = Paise(17_345_00))).save("net-worth-4x2-negative")
        render(twoRows, snap = null).save("net-worth-empty")
    }
}

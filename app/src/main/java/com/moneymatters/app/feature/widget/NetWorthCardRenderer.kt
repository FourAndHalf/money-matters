package com.moneymatters.app.feature.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.core.content.res.ResourcesCompat
import com.moneymatters.app.R
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.theme.DarkMoneyColors
import com.moneymatters.app.ui.theme.LightMoneyColors
import com.moneymatters.app.ui.theme.MoneyColors
import kotlin.math.min
import kotlin.math.roundToInt

/** Below this height the widget is one row tall and drops the Assets / Liabilities row. */
internal const val FULL_CARD_MIN_HEIGHT_DP = 110f

// Home's NetWorthCard, in dp: 16 padding; 11 label; 34 hero over a 10 bar; 11 / 13 for the Assets and Liabilities row.
private const val FULL_NATURAL_HEIGHT = 160f
private const val COMPACT_NATURAL_HEIGHT = 89f
private const val CORNER = 28f
private const val TOGGLE_WIDTH = 30f
private const val TOGGLE_HEIGHT = 18f

/**
 * Paints Home's net worth card (same font, sizes, colours, split bar) into a bitmap. Widgets are RemoteViews, which can't load
 * the bundled JetBrains Mono as text, so the whole card is drawn here and shown as one image. The layout is Home's, scaled
 * down uniformly when the widget is shorter than the card.
 */
internal object NetWorthCardRenderer {
    fun render(context: Context, size: DpSize, snapshot: WidgetSnapshot?, hidden: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(
            (size.width.value * density).roundToInt().coerceAtLeast(1),
            (size.height.value * density).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val full = size.height.value >= FULL_CARD_MIN_HEIGHT_DP
        val natural = if (full) FULL_NATURAL_HEIGHT else COMPACT_NATURAL_HEIGHT
        val scale = min(1f, size.height.value / natural)
        val canvas = Canvas(bitmap)
        canvas.scale(density * scale, density * scale)
        Painter(context, canvas, size.width.value / scale, size.height.value / scale, if (dark) DarkMoneyColors else LightMoneyColors, dark)
            .draw(snapshot, hidden, full, natural)
        return bitmap
    }

    private class Painter(
        context: Context,
        val canvas: Canvas,
        val width: Float,
        val height: Float,
        val colors: MoneyColors,
        val dark: Boolean,
    ) {
        private val regular = font(context, R.font.jetbrains_mono_regular)
        private val medium = font(context, R.font.jetbrains_mono_medium)
        private val pad = if (height < FULL_CARD_MIN_HEIGHT_DP) 12f else 16f

        fun draw(snapshot: WidgetSnapshot?, hidden: Boolean, full: Boolean, natural: Float) {
            card()
            // Content is laid out from the top of its own box; any spare height is split above and below.
            val top = (height - natural) / 2 + pad
            val contentWidth = width - 2 * pad
            text("Net worth", pad, top, 15f, 11f, regular, colors.muted)
            lockToggle(top, on = hidden)
            if (snapshot == null) {
                text("Open Money Matters to start", pad, top + 21f, 40f, 13f, regular, colors.ink, fitWidth = contentWidth)
                return
            }
            val heroText = if (hidden) HIDDEN_AMOUNT else snapshot.netWorth.formatInr()
            val heroTop = top + if (full) 21f else 15f
            val heroSize = if (full) 34f else 28f
            val heroLine = if (full) 40f else 34f
            text(heroText, pad, heroTop, heroLine, heroSize, medium, colors.ink, spacing = -0.045f, fitWidth = contentWidth)
            val barTop = heroTop + heroLine + if (full) 14f else 8f
            bar(pad, barTop, contentWidth, if (full) 10f else 8f, snapshot, hidden)
            if (!full) return
            val rowTop = barTop + 10f + 10f
            text("Assets", pad, rowTop, 15f, 11f, regular, colors.muted)
            text(amount(snapshot.assets, hidden), pad, rowTop + 15f, 18f, 13f, medium, colors.ink, spacing = -0.01f)
            text("Liabilities", width - pad, rowTop, 15f, 11f, regular, colors.muted, alignEnd = true)
            text(amount(snapshot.liabilities, hidden), width - pad, rowTop + 15f, 18f, 13f, medium, colors.negative, spacing = -0.01f, alignEnd = true)
        }

        /** The app-lock switch at the top right, on the label row. On means the lock is on and the figures are hidden. */
        private fun lockToggle(labelTop: Float, on: Boolean) {
            val left = width - pad - TOGGLE_WIDTH
            val top = labelTop + (15f - TOGGLE_HEIGHT) / 2
            val track = RectF(left, top, left + TOGGLE_WIDTH, top + TOGGLE_HEIGHT)
            canvas.drawRoundRect(track, TOGGLE_HEIGHT / 2, TOGGLE_HEIGHT / 2, fill(if (on) colors.anchor else colors.outline))
            val knobRadius = TOGGLE_HEIGHT / 2 - 2f
            val knobX = if (on) track.right - 2f - knobRadius else track.left + 2f + knobRadius
            canvas.drawCircle(knobX, track.centerY(), knobRadius, fill(Color.White))
        }

        private fun amount(value: Paise, hidden: Boolean) = if (hidden) HIDDEN_AMOUNT else value.formatInr()

        /** Module's look: surfaceLow tile, 28dp corners, a faint 1dp highlight edge. */
        private fun card() {
            val rect = RectF(0.5f, 0.5f, width - 0.5f, height - 0.5f)
            canvas.drawRoundRect(rect, CORNER, CORNER, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surfaceLow.toArgb() })
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.White.copy(alpha = if (dark) 0.04f else 0.75f).toArgb()
            }
            canvas.drawRoundRect(rect, CORNER, CORNER, edge)
        }

        /** Assets over liabilities, as on Home; with the amounts hidden the split would give them away, so only the track shows. */
        private fun bar(x: Float, y: Float, w: Float, h: Float, snapshot: WidgetSnapshot, hidden: Boolean) {
            val clip = Path().apply { addRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawRect(x, y, x + w, y + h, fill(colors.outline))
            if (!hidden) {
                val total = (snapshot.assets.value + snapshot.liabilities.value).coerceAtLeast(1)
                val assetsWidth = w * snapshot.assets.value.toFloat() / total
                canvas.drawRect(x, y, x + assetsWidth, y + h, fill(colors.anchor))
                canvas.drawRect(x + assetsWidth, y, x + w * (snapshot.assets.value + snapshot.liabilities.value).toFloat() / total, y + h, fill(colors.negative))
            }
            canvas.restore()
        }

        private fun fill(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }

        /** One text line centred in a [lineHeight] box whose top is [top]. [fitWidth] shrinks the size until it fits. */
        private fun text(
            value: String,
            x: Float,
            top: Float,
            lineHeight: Float,
            size: Float,
            face: Typeface,
            color: Color,
            spacing: Float = 0f,
            alignEnd: Boolean = false,
            fitWidth: Float? = null,
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                this.color = color.toArgb()
                letterSpacing = spacing
                fontFeatureSettings = "tnum"
                textAlign = if (alignEnd) Paint.Align.RIGHT else Paint.Align.LEFT
                textSize = size
            }
            if (fitWidth != null) {
                while (paint.measureText(value) > fitWidth && paint.textSize > 8f) paint.textSize -= 0.5f
            }
            val metrics = paint.fontMetrics
            val baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
            canvas.drawText(value, x, baseline, paint)
        }

        private fun font(context: Context, id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.MONOSPACE
    }
}

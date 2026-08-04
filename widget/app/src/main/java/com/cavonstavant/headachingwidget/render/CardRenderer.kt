package com.cavonstavant.headachingwidget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.cavonstavant.headachingwidget.R
import com.cavonstavant.headachingwidget.data.Quote

/**
 * Draws the shareable card — the tap payoff Fortunes has.
 *
 * Same language as the widget itself: black ground, tracked-out label, the quote
 * in Departure Mono, and the full credit string the site prints rather than the
 * widget's abbreviated one.
 */
object CardRenderer {

    const val SIZE = 1080

    private const val PADDING = 96f
    private const val GAP = 48f
    private const val LABEL_SIZE = 26f
    private const val CREDIT_SIZE = 24f
    private const val QUOTE_MAX_SIZE = 64f
    private const val QUOTE_MIN_SIZE = 26f
    private const val QUOTE_SIZE_STEP = 2f
    private const val LINE_SPACING = 1.25f
    private const val LABEL_TRACKING = 0.18f
    private const val CREDIT_TRACKING = 0.1f

    private const val BACKGROUND = Color.BLACK
    private const val FOREGROUND = Color.WHITE
    private val MUTED = Color.rgb(0x8A, 0x8A, 0x8A)

    fun render(context: Context, quote: Quote): Bitmap {
        val typeface = ResourcesCompat.getFont(context, R.font.departure_mono) ?: Typeface.MONOSPACE
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap).apply { drawColor(BACKGROUND) }

        val contentWidth = (SIZE - PADDING * 2).toInt()

        val label = measure(
            text = context.getString(R.string.widget_title).uppercase(),
            paint = paintFor(typeface, LABEL_SIZE, MUTED, LABEL_TRACKING),
            width = contentWidth,
        )
        val credit = measure(
            text = quote.credit.uppercase(),
            paint = paintFor(typeface, CREDIT_SIZE, MUTED, CREDIT_TRACKING),
            width = contentWidth,
        )

        // Whatever vertical room the label and credit leave belongs to the quote.
        val available = (SIZE - PADDING * 2 - label.height - credit.height - GAP * 2)
            .coerceAtLeast(0f)
        val body = shrinkToFit(
            text = quote.display,
            paint = paintFor(typeface, QUOTE_MAX_SIZE, FOREGROUND, 0f),
            width = contentWidth,
            maxHeight = available.toInt(),
        )

        canvas.place(label, PADDING, PADDING)
        canvas.place(body, PADDING, PADDING + label.height + GAP + (available - body.height) / 2f)
        canvas.place(credit, PADDING, SIZE - PADDING - credit.height)

        return bitmap
    }

    /** Steps the type size down until the quote fits the room it has. */
    private fun shrinkToFit(text: CharSequence, paint: TextPaint, width: Int, maxHeight: Int): StaticLayout {
        var size = QUOTE_MAX_SIZE
        while (true) {
            paint.textSize = size
            val layout = measure(text, paint, width)
            if (layout.height <= maxHeight || size <= QUOTE_MIN_SIZE) return layout
            size -= QUOTE_SIZE_STEP
        }
    }

    private fun measure(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()

    private fun paintFor(typeface: Typeface, size: Float, color: Int, tracking: Float) =
        TextPaint().apply {
            isAntiAlias = true
            this.typeface = typeface
            this.textSize = size
            this.color = color
            this.letterSpacing = tracking
        }

    private fun Canvas.place(layout: StaticLayout, x: Float, y: Float) =
        withTranslation(x, y) { layout.draw(this) }
}

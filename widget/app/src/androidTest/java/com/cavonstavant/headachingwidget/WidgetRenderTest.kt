package com.cavonstavant.headachingwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cavonstavant.headachingwidget.data.Quote
import com.cavonstavant.headachingwidget.data.QuoteRepository
import com.cavonstavant.headachingwidget.render.CardRenderer
import com.cavonstavant.headachingwidget.widget.QuoteWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Renders the widget layouts and the share card on a real device, because the
 * risky parts of this widget are all things only inflation can settle: whether
 * `res/font` resolves inside RemoteViews, whether `autoSizeTextType` actually
 * shrinks text there, and whether the card renderer lays out sanely.
 *
 * Screenshots land in the app's external files dir so they can be pulled and
 * eyeballed:
 *   adb shell run-as com.cavonstavant.headachingwidget \
 *       cat files/render/widget-4x2.png > widget-4x2.png
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val longQuote = Quote(
        text = "And I will be in good spirits / Buckle up, you may want to survive this one",
        song = "Dodge This!",
        artist = "Headache (PLZ)",
        album = "The Head Hurts but the Heart Knows the Truth",
    )

    @Test
    fun departureMonoResolvesInsideRemoteViews() {
        val view = inflateWidget(R.layout.widget_quote, widthDp = 320, heightDp = 160)
        val quote = view.findViewById<TextView>(R.id.widget_quote)

        assertNotNull("quote view missing from the wide layout", quote)
        assertNotNull("quote text view has no typeface at all", quote.typeface)

        // Comparing instances is unreliable across inflation paths, so compare
        // metrics instead: a pixel monospace measures differently from Roboto.
        val sample = "MMMMiiii"
        val actual = quote.paint.measureText(sample)
        val fallback = TextPaint().apply {
            typeface = Typeface.DEFAULT
            textSize = quote.paint.textSize
            letterSpacing = quote.paint.letterSpacing
        }.measureText(sample)

        assertTrue(
            "quote text measures identically to Roboto ($actual px) — res/font did not " +
                "resolve inside RemoteViews and the widget is not using Departure Mono",
            abs(actual - fallback) > 1f,
        )
    }

    @Test
    fun wideLayoutShowsQuoteAndCredit() {
        val view = inflateWidget(R.layout.widget_quote, widthDp = 320, heightDp = 160)

        val quote = view.findViewById<TextView>(R.id.widget_quote)
        val credit = view.findViewById<TextView>(R.id.widget_credit)

        assertEquals(longQuote.display, quote.text.toString())
        assertEquals(longQuote.song, credit.text.toString())
        assertTrue("quote laid out with no height", quote.height > 0)
        screenshot(view, "widget-4x2.png")
    }

    @Test
    fun compactLayoutDropsTheCreditLine() {
        val view = inflateWidget(R.layout.widget_quote_small, widthDp = 160, heightDp = 160)

        assertEquals(longQuote.display, view.findViewById<TextView>(R.id.widget_quote).text.toString())
        assertNull(
            "compact layout must not carry a credit view — the provider skips setting it",
            view.findViewById<TextView?>(R.id.widget_credit),
        )
        screenshot(view, "widget-2x2.png")
    }

    /**
     * The whole point of autosize here: the same quote must be set smaller in a
     * 2x2 than in a 4x2, otherwise long quotes just get clipped.
     */
    @Test
    fun autoSizeShrinksTextInTheSmallerWidget() {
        val wide = inflateWidget(R.layout.widget_quote, widthDp = 320, heightDp = 160)
            .findViewById<TextView>(R.id.widget_quote)
        val compact = inflateWidget(R.layout.widget_quote_small, widthDp = 160, heightDp = 160)
            .findViewById<TextView>(R.id.widget_quote)

        assertTrue(
            "autosize did not shrink text for the compact widget " +
                "(wide=${wide.textSize}px, compact=${compact.textSize}px)",
            compact.textSize < wide.textSize,
        )
    }

    @Test
    fun quoteFitsWithoutBeingClipped() {
        val view = inflateWidget(R.layout.widget_quote, widthDp = 320, heightDp = 160)
        val quote = view.findViewById<TextView>(R.id.widget_quote)

        val lines = quote.layout.lineCount
        val visibleLines = quote.height / quote.lineHeight
        assertTrue(
            "quote needs $lines lines but only $visibleLines fit — it will be ellipsized",
            lines <= visibleLines,
        )
    }

    @Test
    fun shareCardRendersSquareAndNotBlank() {
        val card = CardRenderer.render(context, longQuote)

        assertEquals(CardRenderer.SIZE, card.width)
        assertEquals(CardRenderer.SIZE, card.height)
        assertEquals("card ground should be black", Color.BLACK, card.getPixel(4, 4))
        assertTrue("card has no light pixels — nothing was drawn", hasLightPixels(card))

        write(card, "share-card.png")
        card.recycle()
    }

    /**
     * Exercises the provider's own RemoteViews, not a hand-built copy: the
     * multi-size map construction, the shared click intent, and the fact that
     * the compact variant must not try to set the credit view it doesn't have.
     * A launcher is the only thing that can actually place a widget, so this is
     * as close to the real path as a test can get.
     */
    @Test
    fun providerBuildsRemoteViewsThatInflate() {
        val remote = QuoteWidgetProvider.buildViews(context, longQuote)

        val host = FrameLayout(context)
        val view = remote.apply(context, host)
        host.addView(view)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(dpToPx(320), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dpToPx(160), View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, dpToPx(320), dpToPx(160))

        val quote = view.findViewById<TextView>(R.id.widget_quote)
        assertNotNull("provider's views have no quote text view", quote)
        assertEquals(longQuote.display, quote.text.toString())
        assertTrue("provider's views laid out with no height", view.height > 0)
    }

    /** A missing quote must degrade to a message, not crash the launcher. */
    @Test
    fun providerHandlesAMissingQuote() {
        val remote = QuoteWidgetProvider.buildViews(context, null)

        val host = FrameLayout(context)
        val view = remote.apply(context, host)

        assertEquals(
            context.getString(R.string.no_quotes),
            view.findViewById<TextView>(R.id.widget_quote).text.toString(),
        )
    }

    /** The bundled asset alone has to be enough to show a quote offline. */
    @Test
    fun bundledAssetYieldsTodaysQuote() {
        val quotes = QuoteRepository.get(context).quotes()
        assertTrue("bundled asset produced no quotes", quotes.size > 1000)

        val today = QuoteRepository.get(context).today()
        assertNotNull("no quote for today", today)
        assertTrue("today's quote has no text", today!!.text.isNotEmpty())
    }

    // ── helpers ────────────────────────────────────────────────

    /** Applies the RemoteViews the widget really ships, then measures it at a given size. */
    private fun inflateWidget(layoutId: Int, widthDp: Int, heightDp: Int): View {
        val remote = RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_quote, longQuote.display)
            if (layoutId == R.layout.widget_quote) {
                setTextViewText(R.id.widget_credit, longQuote.song)
            }
        }

        val host = FrameLayout(context)
        val view = remote.apply(context, host)
        host.addView(view)

        val width = dpToPx(widthDp)
        val height = dpToPx(heightDp)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        return view
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    private fun screenshot(view: View, name: String) {
        val parent = view.parent as ViewGroup
        val bitmap = Bitmap.createBitmap(parent.width, parent.height, Bitmap.Config.ARGB_8888)
        parent.draw(Canvas(bitmap))
        write(bitmap, name)
        bitmap.recycle()
    }

    private fun write(bitmap: Bitmap, name: String) {
        val directory = File(context.filesDir, "render").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun hasLightPixels(bitmap: Bitmap): Boolean {
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                if (Color.red(bitmap.getPixel(x, y)) > 0x60) return true
            }
        }
        return false
    }
}

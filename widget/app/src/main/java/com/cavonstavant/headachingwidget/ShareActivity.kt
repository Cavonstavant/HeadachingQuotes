package com.cavonstavant.headachingwidget

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cavonstavant.headachingwidget.data.Quote
import com.cavonstavant.headachingwidget.data.QuoteRepository
import com.cavonstavant.headachingwidget.render.CardRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * The widget's tap payoff: renders the day's quote as a card and hands it to the
 * share sheet, the way Fortunes does. Has no UI of its own.
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val quote = QuoteRepository.get(this).today()
        if (quote == null) {
            bailOut(R.string.no_quotes)
            return
        }

        val card = runCatching { writeCard(quote) }.getOrNull()
        if (card == null) {
            bailOut(R.string.share_failed)
            return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, card)
            putExtra(Intent.EXTRA_TEXT, "${quote.display}\n\n${quote.credit}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        finish()
    }

    private fun bailOut(messageId: Int) {
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun writeCard(quote: Quote): Uri {
        val directory = File(cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, CARD_NAME)

        val bitmap = CardRenderer.render(this, quote)
        try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }

        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private companion object {
        /** Must match the cache-path in res/xml/file_paths.xml. */
        const val SHARE_DIRECTORY = "shared"
        const val CARD_NAME = "headaching-quote.png"
    }
}

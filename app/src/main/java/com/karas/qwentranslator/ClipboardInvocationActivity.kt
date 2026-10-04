package com.karas.qwentranslator

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import com.karas.qwentranslator.core.TranslationRequest

/** Android 10+ allows clipboard reads only after this foreground window obtains focus. */
class ClipboardInvocationActivity : Activity() {
    private var consumed = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || consumed) return
        consumed = true
        val text = try {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        } catch (_: RuntimeException) {
            null
        }
        when {
            text.isNullOrBlank() -> Toast.makeText(this, R.string.copy_text_first, Toast.LENGTH_LONG).show()
            text.length > TranslationRequest.MAX_TEXT_LENGTH ->
                Toast.makeText(this, R.string.clipboard_text_too_long, Toast.LENGTH_LONG).show()
            else -> startActivity(
                Intent(this, ProcessTextActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text),
            )
        }
        finish()
    }
}

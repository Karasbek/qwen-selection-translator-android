package com.karas.qwentranslator.anki

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SaveReceiptStoreTest {
    @Test fun durableJournalSurvivesRecreationAndContainsOnlyHashesAndIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "receipt-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
        try {
            val first = FileSaveReceiptStore(isolated)
            val intent = SaveReceipt("operation", "front-hash", "word-hash", null, false)
            first.put(intent)
            assertEquals(listOf(intent), FileSaveReceiptStore(isolated).all())
            first.put(intent.copy(noteId = 123, confirmed = true))
            assertEquals(listOf(intent.copy(noteId = 123, confirmed = true)), FileSaveReceiptStore(isolated).all())
            val data = directory.resolve("anki-save-receipts.json").readText()
            assertFalse(data.contains("definition")); assertFalse(data.contains("example")); assertFalse(data.contains("apiKey"))
        } finally { directory.deleteRecursively() }
    }
    @Test fun corruptJournalFailsClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "receipt-corrupt-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
        try {
            directory.resolve("anki-save-receipts.json").writeText("broken")
            try { FileSaveReceiptStore(isolated).all(); fail("Corrupt journal must not look empty") }
            catch (_: org.json.JSONException) { /* no write can proceed with an unreadable journal */ }
        } finally { directory.deleteRecursively() }
    }
}

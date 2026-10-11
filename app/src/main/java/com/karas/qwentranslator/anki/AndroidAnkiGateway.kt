package com.karas.qwentranslator.anki

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import androidx.core.net.toUri
import androidx.core.content.ContextCompat

/** Official FlashCardsContract from AnkiDroid v2.25.1, with minimal explicit projections. */
class AndroidAnkiGateway(context: Context) : AnkiGateway {
    private val context = context.applicationContext
    override fun availability(): AnkiAvailability {
        val version = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            return AnkiAvailability(AnkiStatus.NOT_INSTALLED)
        }
        @Suppress("DEPRECATION")
        val provider = context.packageManager.resolveContentProvider(AUTHORITY, 0)
        if (provider == null || !provider.enabled || !provider.exported || provider.packageName != PACKAGE)
            return AnkiAvailability(AnkiStatus.API_UNAVAILABLE, version)
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED)
            return AnkiAvailability(AnkiStatus.PERMISSION_REQUIRED, version)
        return AnkiAvailability(AnkiStatus.CONNECTED, version)
    }

    override fun decks(): List<AnkiDeck> = query("decks", arrayOf("deck_id", "deck_name")) {
        AnkiDeck(it.getLong(0), it.getString(1))
    }

    override fun models(): List<AnkiModel> = query("models", arrayOf("_id", "name", "field_names", "type")) {
        AnkiModel(it.getLong(0), it.getString(1), it.getString(2).split('\u001f'), it.getInt(3))
    }

    private fun <T> query(path: String, projection: Array<String>, read: (Cursor) -> T): List<T> {
        check(availability().status == AnkiStatus.CONNECTED) { "AnkiDroid unavailable" }
        return context.contentResolver.query("content://$AUTHORITY/$path".toUri(), projection, null, null, null)
            ?.use { cursor -> buildList { while (cursor.moveToNext()) add(read(cursor)) } }
            ?: error("AnkiDroid metadata unavailable")
    }

    companion object {
        const val PACKAGE = "com.ichi2.anki"
        const val AUTHORITY = "com.ichi2.anki.flashcards"
        const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }
}

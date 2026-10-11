package com.karas.qwentranslator.anki

import android.content.ContentValues
import android.content.Context
import android.text.Html
import java.security.MessageDigest
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

    override fun decks(): List<AnkiDeck> = query("decks", arrayOf("deck_id", "deck_name", "deck_dyn")) {
        AnkiDeck(it.getLong(0), it.getString(1), it.getInt(2) != 0)
    }

    override fun models(): List<AnkiModel> = query("models", arrayOf("_id", "name", "field_names", "type")) {
        AnkiModel(it.getLong(0), it.getString(1), it.getString(2).split('\u001f'), it.getInt(3))
    }

    override fun hasExactDuplicate(modelId: Long, front: String): Boolean {
        check(availability().status == AnkiStatus.CONNECTED)
        // Official v2 checksum lookup, then compare full Front to reject checksum collisions.
        // Only candidate fields from this model/checksum are read, never the full collection.
        val cursor = context.contentResolver.query("content://$AUTHORITY/notes_v2".toUri(),
            arrayOf("flds"), "mid=? AND csum=?", arrayOf(modelId.toString(), checksum(front).toString()), null)
            ?: error("Duplicate lookup unavailable")
        return cursor.use {
            var found = false
            while (it.moveToNext()) if (it.getString(0).substringBefore('\u001f') == front) found = true
            found
        }
    }

    override fun addNote(draft: AnkiCardDraft, onInserted: (Long) -> Unit): Long? {
        check(availability().status == AnkiStatus.CONNECTED)
        require(draft.validation.valid && draft.fields.size == 5)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put("mid", requireNotNull(draft.modelId))
            put("flds", draft.fields.joinToString("\u001f"))
        }
        val uri = resolver.insert("content://$AUTHORITY/notes".toUri(), values) ?: return null
        val id = uri.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        onInserted(id) // Durable receipt BEFORE querying/moving the newly created card.
        val cards = resolver.query("$uri/cards".toUri(), arrayOf("ord"), null, null, null)
            ?: error("New card unavailable")
        val ordinals = cards.use { buildList { while (it.moveToNext()) add(it.getInt(0)) } }
        require(ordinals == listOf(0)) // Exactly one c1 card; never modify any pre-existing card.
        val target = ContentValues().apply { put("deck_id", requireNotNull(draft.deckId)) }
        check(resolver.update("$uri/cards/0".toUri(), target, null, null) == 1)
        return id
    }

    override fun verifyNote(noteId: Long, draft: AnkiCardDraft): NoteVerification {
        val notes = query("notes/$noteId", arrayOf("mid", "flds")) {
            it.getLong(0) to it.getString(1).split('\u001f')
        }
        if (notes.singleOrNull() != (draft.modelId to draft.fields))
            return NoteVerification(false, "Не удалось подтвердить тип заметки и пять полей. Проверьте AnkiDroid.")
        val cards = query("notes/$noteId/cards", arrayOf("ord", "deck_id")) { it.getInt(0) to it.getLong(1) }
        if (cards != listOf(0 to draft.deckId))
            return NoteVerification(false, "Не удалось подтвердить одну карточку c1 в выбранной колоде. Проверьте колоду в AnkiDroid; Qwen не перемещает её повторно.")
        return NoteVerification(true)
    }

    // Same strip-tags/entity-decode/SHA1-first-32-bits algorithm as the v2.25.1 API.
    // Builder output has only <br> tags and encoded plain text (no images/scripts/styles).
    @Suppress("DEPRECATION")
    private fun checksum(front: String): Long {
        val plain = Regex("&#?\\w+;").replace(front.replace(Regex("<.*?>"), "")) {
            Html.fromHtml(it.value.replace("&nbsp;", " ")).toString()
        }
        val bytes = MessageDigest.getInstance("SHA-1").digest(plain.toByteArray(Charsets.UTF_8))
        return bytes.take(4).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
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

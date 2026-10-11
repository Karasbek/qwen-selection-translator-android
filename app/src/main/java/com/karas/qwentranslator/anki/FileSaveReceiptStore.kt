package com.karas.qwentranslator.anki

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** noBackupFilesDir: device-local protection, excluded from cloud backup and device transfer. */
class FileSaveReceiptStore(context: Context) : SaveReceiptStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "anki-save-receipts.json"))
    @Synchronized override fun all(): List<SaveReceipt> {
        if (!file.baseFile.exists()) return emptyList()
        val array = JSONArray(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SaveReceipt(item.getString("key"), item.getString("fingerprint"), item.getString("word"),
                if (item.isNull("noteId")) null else item.getLong("noteId"), item.getBoolean("confirmed"))
        }
    }
    @Synchronized override fun put(receipt: SaveReceipt) {
        val records = all().associateBy { it.key }.toMutableMap().apply { put(receipt.key, receipt) }
        val array = JSONArray()
        records.values.forEach { record -> array.put(JSONObject().put("key", record.key)
            .put("fingerprint", record.fingerprint).put("word", record.word)
            .put("noteId", record.noteId ?: JSONObject.NULL).put("confirmed", record.confirmed)) }
        val stream = file.startWrite()
        try { stream.write(array.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}

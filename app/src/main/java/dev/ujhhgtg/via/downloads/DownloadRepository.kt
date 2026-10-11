package dev.ujhhgtg.via.downloads

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.database.sqlite.transaction
import org.json.JSONObject

/** Persistent downloader database. This mirrors Via's downloader v3 schema. */
class DownloadRepository(context: Context) {
    private val helper = Helper(context.applicationContext)
    private val db get() = helper.writableDatabase
    private val chunkCache = HashMap<Long, List<DownloadChunk>>() // f5.a keeps live range offsets between pause/resume.

    fun list(): List<DownloadRecord> = db.query("tasks", null, null, null, null, null, "id DESC").use { c -> buildList { while (c.moveToNext()) add(read(c)) } }
    fun list(states: IntArray): List<DownloadRecord> {
        if (states.isEmpty()) return emptyList()
        val where = states.joinToString(",", "state IN (", ")")
        return db.query("tasks", null, where, null, null, null, "created_at ASC").use { c -> buildList { while (c.moveToNext()) add(read(c)) } }
    }
    fun get(id: Long): DownloadRecord? = db.query("tasks", null, "id = ?", arrayOf(id.toString()), null, null, null, "1").use { c -> if (c.moveToFirst()) read(c) else null }
    fun insert(record: DownloadRecord): Long = db.insert("tasks", null, values(record, includeId = false))
    fun update(record: DownloadRecord): Boolean = db.update("tasks", values(record, includeId = false).apply { put("updated_at", now()) }, "id = ?", arrayOf(record.id.toString())) > 0
    fun delete(id: Long): Boolean = db.delete("tasks", "id = ?", arrayOf(id.toString())) > 0
    internal fun savePackageClassification(id: Long, flags: Int): Boolean = db.update("tasks", ContentValues().apply {
        put("flags", flags)
    }, "id = ? AND flags != ?", arrayOf(id.toString(), flags.toString())) > 0
    fun clearChunks(id: Long): Boolean = (db.delete("chunks", "task_id = ?", arrayOf(id.toString())) >= 0).also { if (it) chunkCache.remove(id) }
    fun resetChunks(id: Long): Boolean = (db.update("chunks", ContentValues().apply { put("downloaded", 0) }, "task_id = ?", arrayOf(id.toString())) >= 0).also {
        if (it) chunkCache[id]?.forEach { chunk -> chunk.downloaded = 0 }
    }
    internal fun chunks(taskId: Long): List<DownloadChunk> = chunkCache.getOrPut(taskId) { db.query("chunks", null, "task_id = ?", arrayOf(taskId.toString()), null, null, "id ASC").use { cursor ->
        buildList { while (cursor.moveToNext()) add(DownloadChunk(cursor.getLong(cursor.getColumnIndexOrThrow("id")), taskId,
            cursor.getLong(cursor.getColumnIndexOrThrow("start")), cursor.getLong(cursor.getColumnIndexOrThrow("length")),
            cursor.getLong(cursor.getColumnIndexOrThrow("downloaded")))) }
    } }
    internal fun createChunks(chunks: List<DownloadChunk>): List<DownloadChunk> {
        chunks.forEach { chunkCache.remove(it.taskId) }
        db.transaction {
            chunks.forEach { chunk -> chunk.id = insert("chunks", null, ContentValues().apply {
                put("task_id", chunk.taskId); put("start", chunk.start); put("length", chunk.length); put("downloaded", chunk.downloaded); put("flags", 0)
            }) }
        }
        return chunks.firstOrNull()?.let { this.chunks(it.taskId) } ?: emptyList()
    }
    internal fun saveChunk(chunk: DownloadChunk) = db.update("chunks", ContentValues().apply {
        put("downloaded", chunk.downloaded)
    }, "id = ?", arrayOf(chunk.id.toString())) > 0
    fun close() { chunkCache.clear(); helper.close() }

    private fun values(r: DownloadRecord, includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId && r.id != 0L) put("id", r.id)
        put("name", r.name); put("downloaded_size", r.downloadedSize); put("total_size", r.totalSize)
        put("state", r.state); put("error_message", r.errorMessage); put("url", r.url)
        put("header", if (r.headers.isEmpty()) null else JSONObject(r.headers).toString())
        put("chunks", r.chunks); put("path", r.path); put("file_uri", r.fileUri?.toString())
        put("mimetype", r.mimeType); put("priority", r.priority); put("flags", r.flags)
        put("created_at", if (r.createdAt == 0L) now() else r.createdAt); put("updated_at", if (r.updatedAt == 0L) now() else r.updatedAt)
    }
    private fun read(c: android.database.Cursor): DownloadRecord {
        val header = c.getString(c.getColumnIndexOrThrow("header"))
        val headers = mutableMapOf<String, String>(); if (!header.isNullOrEmpty()) runCatching { val o = JSONObject(header); o.keys().forEach { key -> if (!o.isNull(key)) headers[key] = o.optString(key) } }
        val uri = c.getString(c.getColumnIndexOrThrow("file_uri"))
        return DownloadRecord(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("url")), headers, c.getString(c.getColumnIndexOrThrow("mimetype")), c.getString(c.getColumnIndexOrThrow("path")), uri?.let(Uri::parse), c.getLong(c.getColumnIndexOrThrow("downloaded_size")), c.getLong(c.getColumnIndexOrThrow("total_size")), c.getInt(c.getColumnIndexOrThrow("chunks")), c.getInt(c.getColumnIndexOrThrow("priority")), c.getInt(c.getColumnIndexOrThrow("state")), c.getString(c.getColumnIndexOrThrow("error_message")), c.getInt(c.getColumnIndexOrThrow("flags")), c.getLong(c.getColumnIndexOrThrow("updated_at")), c.getLong(c.getColumnIndexOrThrow("created_at")))
    }
    private fun now() = System.currentTimeMillis() / 1000

    private class Helper(context: Context) : SQLiteOpenHelper(context, "downloader", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, url TEXT, header TEXT, mimetype TEXT, path TEXT, file_uri TEXT, downloaded_size INTEGER, total_size INTEGER, chunks INTEGER, priority INTEGER, state INTEGER, error_message TEXT, flags INTEGER, updated_at INTEGER DEFAULT 0, created_at INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER, start INTEGER, length INTEGER, downloaded INTEGER, flags INTEGER)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 1) { db.execSQL("DELETE FROM tasks"); return }
            if (oldVersion == 2) { db.execSQL("ALTER TABLE tasks ADD COLUMN error_message Text;"); db.execSQL("CREATE TABLE IF NOT EXISTS chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER, start INTEGER, length INTEGER, downloaded INTEGER, flags INTEGER)") }
        }
    }
}

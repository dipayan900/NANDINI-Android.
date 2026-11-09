package com.dipayan.nandini

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class StoredChatSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long
)

/**
 * Durable chat storage. SQLite avoids SharedPreferences' small Binder-backed
 * value limit and removes arbitrary message/chat count caps.
 */
class ChatStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "arushi_conversations.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE chats (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id TEXT NOT NULL,
                role TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(chat_id) REFERENCES chats(id) ON DELETE CASCADE
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_chat_id ON messages(chat_id, id)")
        db.execSQL("CREATE INDEX idx_messages_body ON messages(body)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun migrateLegacy(chatsJson: String?, historyJson: String?, currentChatId: String) {
        val db = writableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM chats", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        if (count > 0L) return

        val imported = mutableSetOf<String>()
        db.beginTransaction()
        try {
            val chats = JSONArray(chatsJson ?: "[]")
            for (i in 0 until chats.length()) {
                val item = chats.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val messages = mutableListOf<Pair<String, String>>()
                val stored = item.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until stored.length()) {
                    val message = stored.optJSONObject(j) ?: continue
                    val role = message.optString("role")
                    val body = message.optString("text")
                    if ((role == "user" || role == "model") && body.isNotBlank()) {
                        messages.add(role to body)
                    }
                }
                if (messages.isNotEmpty()) {
                    saveChatInsideTransaction(db, id, item.optString("title", "Conversation"), messages)
                    imported.add(id)
                }
            }

            if (currentChatId !in imported) {
                val legacy = JSONArray(historyJson ?: "[]")
                val messages = mutableListOf<Pair<String, String>>()
                for (i in 0 until legacy.length()) {
                    val item = legacy.optJSONObject(i) ?: continue
                    val role = item.optString("role")
                    val body = item.optString("text")
                    if ((role == "user" || role == "model") && body.isNotBlank()) {
                        messages.add(role to body)
                    }
                }
                if (messages.isNotEmpty()) {
                    saveChatInsideTransaction(db, currentChatId, "Previous conversation", messages)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveChat(id: String, title: String, messages: List<Pair<String, String>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            saveChatInsideTransaction(db, id, title, messages)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveChatInsideTransaction(
        db: SQLiteDatabase,
        id: String,
        title: String,
        messages: List<Pair<String, String>>
    ) {
        val now = System.currentTimeMillis()
        db.insertWithOnConflict(
            "chats",
            null,
            ContentValues().apply {
                put("id", id)
                put("title", title)
                put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        db.delete("messages", "chat_id = ?", arrayOf(id))
        messages.forEach { (role, body) ->
            if ((role == "user" || role == "model") && body.isNotBlank()) {
                db.insert(
                    "messages",
                    null,
                    ContentValues().apply {
                        put("chat_id", id)
                        put("role", role)
                        put("body", body)
                        put("created_at", now)
                    }
                )
            }
        }
    }

    fun listChats(): List<StoredChatSummary> {
        val result = mutableListOf<StoredChatSummary>()
        readableDatabase.rawQuery(
            """SELECT c.id, c.title,
                (SELECT m.body FROM messages m WHERE m.chat_id = c.id ORDER BY m.id DESC LIMIT 1) AS preview,
                c.updated_at
                FROM chats c ORDER BY c.updated_at ASC""".trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    StoredChatSummary(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2).orEmpty(),
                        cursor.getLong(3)
                    )
                )
            }
        }
        return result
    }

    fun loadMessages(chatId: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        readableDatabase.query(
            "messages",
            arrayOf("role", "body"),
            "chat_id = ?",
            arrayOf(chatId),
            null,
            null,
            "id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.getString(0) to cursor.getString(1))
        }
        return result
    }

    fun deleteChat(chatId: String): Int =
        writableDatabase.delete("chats", "id = ?", arrayOf(chatId)).also {
            writableDatabase.delete("messages", "chat_id = ?", arrayOf(chatId))
        }

    /** Delete conversations older than the selected retention window. */
    fun deleteOlderThan(cutoffMillis: Long, keepChatId: String? = null): Int {
        val db = writableDatabase
        val ids = mutableListOf<String>()
        db.query(
            "chats",
            arrayOf("id"),
            if (keepChatId.isNullOrBlank()) "updated_at < ?" else "updated_at < ? AND id != ?",
            if (keepChatId.isNullOrBlank()) arrayOf(cutoffMillis.toString()) else arrayOf(cutoffMillis.toString(), keepChatId),
            null, null, null
        ).use { cursor -> while (cursor.moveToNext()) ids.add(cursor.getString(0)) }
        db.beginTransaction()
        try {
            ids.forEach { id ->
                db.delete("messages", "chat_id = ?", arrayOf(id))
                db.delete("chats", "id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ids.size
    }

    fun chatCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM chats", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    /**
     * Lightweight lexical retrieval over older conversations. Recent context is
     * supplied separately by the caller; these are additional older matches.
     */
    fun findRelevantMessages(query: String, limit: Int = 6): List<Pair<String, String>> {
        val terms = Regex("[\\p{L}\\p{N}]{3,}")
            .findAll(query.lowercase())
            .map { it.value }
            .distinct()
            .take(12)
            .toList()
        if (terms.isEmpty()) return emptyList()

        data class Candidate(val index: Int, val role: String, val body: String, val score: Int)
        val candidates = mutableListOf<Candidate>()
        readableDatabase.rawQuery(
            "SELECT id, role, body FROM messages ORDER BY id DESC LIMIT 1200",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val body = cursor.getString(2)
                val normalized = body.lowercase()
                val score = terms.sumOf { term ->
                    if (normalized.contains(term)) 1 + normalized.windowed(term.length).count { it == term } else 0
                }
                if (score > 0) candidates.add(Candidate(cursor.getInt(0), cursor.getString(1), body, score))
            }
        }
        return candidates
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.index })
            .take(limit)
            .sortedBy { it.index }
            .map { it.role to it.body }
    }
}

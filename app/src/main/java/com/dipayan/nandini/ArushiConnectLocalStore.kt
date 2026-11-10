package com.dipayan.nandini

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dipayan.nandini.ArushiConnectRepository.ChatMessage
import java.io.File

/**
 * Private on-device store for ARUSHI Connect. No chat rows or downloaded media
 * are stored in a public/shared directory.
 */
class ArushiConnectLocalStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "arushi_connect_local.db", null, 2) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "arushi_connect_local_retention", Context.MODE_PRIVATE
    )
    private val mediaDirectory = File(appContext.filesDir, "connect_media")

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages (
                conversation_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                body TEXT NOT NULL,
                message_type TEXT NOT NULL,
                media_url TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                reply_to TEXT NOT NULL,
                edited INTEGER NOT NULL,
                media_local_path TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(conversation_id, message_id)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX messages_by_conversation ON messages(conversation_id, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN media_local_path TEXT NOT NULL DEFAULT ''")
        }
    }

    @Synchronized
    fun cache(messages: List<ChatMessage>) {
        val writable = writableDatabase
        writable.beginTransaction()
        try {
            messages.filter {
                it.createdAt > 0L && !isHidden(it.conversationId, it.id, it.createdAt)
            }.forEach { message ->
                val existingPath = writable.rawQuery(
                    "SELECT media_local_path FROM messages WHERE conversation_id = ? AND message_id = ?",
                    arrayOf(message.conversationId, message.id)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }
                val values = ContentValues().apply {
                    put("conversation_id", message.conversationId)
                    put("message_id", message.id)
                    put("sender_id", message.senderId)
                    put("body", message.text)
                    put("message_type", message.type)
                    put("media_url", message.mediaUrl)
                    put("created_at", message.createdAt)
                    put("reply_to", message.replyTo)
                    put("edited", if (message.edited) 1 else 0)
                    put("media_local_path", message.localMediaPath.ifBlank { existingPath })
                }
                writable.insertWithOnConflict(
                    "messages", null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    @Synchronized
    fun readConversation(conversationId: String): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.query(
            "messages", null, "conversation_id = ?",
            arrayOf(conversationId), null, null, "created_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val item = ChatMessage(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                    conversationId = cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                    senderId = cursor.getString(cursor.getColumnIndexOrThrow("sender_id")),
                    text = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("message_type")),
                    mediaUrl = cursor.getString(cursor.getColumnIndexOrThrow("media_url")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    replyTo = cursor.getString(cursor.getColumnIndexOrThrow("reply_to")),
                    edited = cursor.getInt(cursor.getColumnIndexOrThrow("edited")) == 1,
                    localMediaPath = cursor.getString(cursor.getColumnIndexOrThrow("media_local_path"))
                )
                if (!isHidden(item.conversationId, item.id, item.createdAt)) result.add(item)
            }
        }
        return result
    }

    fun mediaFile(conversationId: String, messageId: String): File {
        if (!mediaDirectory.exists()) mediaDirectory.mkdirs()
        val safeConversation = conversationId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeMessage = messageId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(mediaDirectory, "${safeConversation}_${safeMessage}.bin")
    }

    @Synchronized
    fun saveMediaPath(conversationId: String, messageId: String, path: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("media_local_path", path) },
            "conversation_id = ? AND message_id = ?",
            arrayOf(conversationId, messageId)
        )
    }

    fun visible(conversationId: String, messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter {
            it.conversationId == conversationId &&
                !isHidden(conversationId, it.id, it.createdAt)
        }

    @Synchronized
    fun hideMessageLocally(conversationId: String, messageId: String) {
        val ids = preferences.getStringSet("cleared_ids:$conversationId", emptySet())
            .orEmpty().toMutableSet()
        ids.add(messageId)
        preferences.edit().putStringSet("cleared_ids:$conversationId", ids).apply()
        deleteMediaForMessage(conversationId, messageId)
        writableDatabase.delete(
            "messages", "conversation_id = ? AND message_id = ?",
            arrayOf(conversationId, messageId)
        )
    }

    @Synchronized
    fun clearConversation(conversationId: String, knownMessages: List<ChatMessage>) {
        val ids = knownMessages.filter { it.conversationId == conversationId }
            .map { it.id }.toMutableSet()
        readableDatabase.query(
            "messages", arrayOf("message_id"), "conversation_id = ?",
            arrayOf(conversationId), null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getString(0))
        }
        val previouslyHidden = preferences.getStringSet("cleared_ids:$conversationId", emptySet()).orEmpty()
        ids.addAll(previouslyHidden)
        preferences.edit().putStringSet("cleared_ids:$conversationId", ids).apply()
        deleteMediaForConversation(conversationId)
        writableDatabase.delete("messages", "conversation_id = ?", arrayOf(conversationId))
    }

    @Synchronized
    fun clearAllLocalHistory() {
        val idsByConversation = mutableMapOf<String, MutableSet<String>>()
        readableDatabase.query(
            "messages", arrayOf("conversation_id", "message_id"), null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val conversationId = cursor.getString(0)
                val messageId = cursor.getString(1)
                idsByConversation.getOrPut(conversationId) { mutableSetOf() }.add(messageId)
            }
        }
        val editor = preferences.edit()
        idsByConversation.forEach { (conversationId, ids) ->
            val oldIds = preferences.getStringSet("cleared_ids:$conversationId", emptySet()).orEmpty()
            ids.addAll(oldIds)
            editor.putStringSet("cleared_ids:$conversationId", ids)
        }
        editor.apply()
        mediaDirectory.listFiles()?.forEach { it.deleteRecursively() }
        writableDatabase.delete("messages", null, null)
    }

    private fun deleteMediaForMessage(conversationId: String, messageId: String) {
        val file = mediaFile(conversationId, messageId)
        if (file.exists()) file.delete()
    }

    private fun deleteMediaForConversation(conversationId: String) {
        mediaDirectory.listFiles()?.filter {
            it.name.startsWith(conversationId.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_")
        }?.forEach { it.deleteRecursively() }
    }

    private fun isHidden(conversationId: String, messageId: String, createdAt: Long): Boolean {
        val hiddenIds = preferences.getStringSet("cleared_ids:$conversationId", emptySet()).orEmpty()
        if (messageId in hiddenIds) return true
        return false
    }
}

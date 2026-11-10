package com.dipayan.nandini

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dipayan.nandini.ArushiConnectRepository.ChatMessage

/**
 * Device-local cache for ARUSHI Connect. The database lives in the app's private
 * data directory and is removed by Android when the app is uninstalled or its data is cleared.
 */
class ArushiConnectLocalStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "arushi_connect_local.db", null, 1) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "arushi_connect_local_retention", Context.MODE_PRIVATE
    )

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
                PRIMARY KEY(conversation_id, message_id)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX messages_by_conversation ON messages(conversation_id, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the initial local cache schema. Future upgrades must preserve chat data.
    }

    @Synchronized
    fun cache(messages: List<ChatMessage>) {
        val writable = writableDatabase
        writable.beginTransaction()
        try {
            messages.filter { it.createdAt > 0L && !isHidden(it.conversationId, it.id, it.createdAt) }
                .forEach { message ->
                    val values = android.content.ContentValues().apply {
                        put("conversation_id", message.conversationId)
                        put("message_id", message.id)
                        put("sender_id", message.senderId)
                        put("body", message.text)
                        put("message_type", message.type)
                        put("media_url", message.mediaUrl)
                        put("created_at", message.createdAt)
                        put("reply_to", message.replyTo)
                        put("edited", if (message.edited) 1 else 0)
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
                    edited = cursor.getInt(cursor.getColumnIndexOrThrow("edited")) == 1
                )
                if (!isHidden(item.conversationId, item.id, item.createdAt)) result.add(item)
            }
        }
        return result
    }

    fun visible(conversationId: String, messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { it.conversationId == conversationId &&
            !isHidden(conversationId, it.id, it.createdAt) }

    @Synchronized
    fun hideMessageLocally(conversationId: String, messageId: String) {
        val ids = preferences.getStringSet("cleared_ids:$conversationId", emptySet()).orEmpty().toMutableSet()
        ids.add(messageId)
        preferences.edit().putStringSet("cleared_ids:$conversationId", ids).apply()
        writableDatabase.delete(
            "messages", "conversation_id = ? AND message_id = ?",
            arrayOf(conversationId, messageId)
        )
    }

    @Synchronized
    fun clearConversation(conversationId: String, knownMessages: List<ChatMessage>) {
        val ids = knownMessages.filter { it.conversationId == conversationId }.map { it.id }.toSet()
        preferences.edit()
            .putLong("cleared_before:$conversationId", System.currentTimeMillis())
            .putStringSet("cleared_ids:$conversationId", ids)
            .apply()
        writableDatabase.delete("messages", "conversation_id = ?", arrayOf(conversationId))
    }

    @Synchronized
    fun clearAllLocalHistory() {
        preferences.edit().putLong("cleared_all_before", System.currentTimeMillis()).apply()
        writableDatabase.delete("messages", null, null)
    }

    private fun isHidden(conversationId: String, messageId: String, createdAt: Long): Boolean {
        val hiddenIds = preferences.getStringSet("cleared_ids:$conversationId", emptySet()).orEmpty()
        if (messageId in hiddenIds) return true
        val cutoff = maxOf(
            preferences.getLong("cleared_before:$conversationId", 0L),
            preferences.getLong("cleared_all_before", 0L)
        )
        return cutoff > 0L && createdAt > 0L && createdAt <= cutoff
    }
}

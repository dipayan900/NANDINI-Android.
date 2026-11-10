package com.dipayan.nandini

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed data layer for ARUSHI Connect.
 *
 * Firestore/Storage rules in the repository are part of this feature and must be
 * deployed before using the service. This class deliberately does not claim
 * end-to-end encryption; transport security is provided by Firebase TLS.
 */
class ArushiConnectRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    data class Person(
        val uid: String,
        val username: String,
        val displayName: String,
        val photoUrl: String = "",
        val about: String = ""
    )

    data class ChatMessage(
        val id: String,
        val conversationId: String,
        val senderId: String,
        val text: String,
        val type: String,
        val mediaUrl: String,
        val createdAt: Long,
        val replyTo: String,
        val edited: Boolean
    )

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: error("Could not create an ARUSHI Connect session.")
    }

    suspend fun createUsername(username: String, displayName: String): Person {
        val uid = ensureSignedIn()
        val normalized = username.trim().removePrefix("@").lowercase()
        require(normalized.matches(Regex("[a-z0-9_]{3,20}"))) {
            "Username must be 3–20 characters using letters, numbers or underscores."
        }
        val safeName = displayName.trim().take(40).ifBlank { normalized }
        val usernameRef = db.collection("connect_usernames").document(normalized)
        val profileRef = db.collection("connect_profiles").document(uid)
        db.runTransaction { tx ->
            val existing = tx.get(usernameRef)
            check(!existing.exists() || existing.getString("uid") == uid) {
                "That ARUSHI username is already taken."
            }
            tx.set(usernameRef, mapOf("uid" to uid, "username" to normalized))
            tx.set(profileRef, mapOf(
                "uid" to uid,
                "username" to normalized,
                "displayName" to safeName,
                "photoUrl" to "",
                "about" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "discoverable" to true
            ), SetOptions.merge())
            true
        }.await()
        return Person(uid, normalized, safeName)
    }

    suspend fun getMyProfile(): Person? {
        val uid = ensureSignedIn()
        return db.collection("connect_profiles").document(uid).get().await().toPerson()
    }

    suspend fun findByUsername(username: String): Person? {
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return null
        val index = db.collection("connect_usernames").document(normalized).get().await()
        val uid = index.getString("uid") ?: return null
        val profile = db.collection("connect_profiles").document(uid).get().await()
        return profile.toPerson()
    }

    suspend fun createDirectConversation(otherUid: String): String {
        val me = ensureSignedIn()
        require(otherUid != me) { "You cannot start a chat with yourself." }
        val members = listOf(me, otherUid).sorted()
        val key = members.joinToString("_")
        val ref = db.collection("connect_conversations").document(key)
        db.runTransaction { tx ->
            val current = tx.get(ref)
            if (!current.exists()) {
                tx.set(ref, mapOf(
                    "members" to members,
                    "type" to "direct",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp()
                ))
            }
            true
        }.await()
        return key
    }

    suspend fun createGroup(name: String, memberUids: List<String>): String {
        val me = ensureSignedIn()
        val members = (memberUids + me).distinct()
        require(members.size in 2..256) { "A group needs 2–256 members." }
        val groupName = name.trim().take(60)
        require(groupName.isNotBlank()) { "Enter a group name." }
        val ref = db.collection("connect_conversations").document()
        ref.set(mapOf(
            "members" to members,
            "admins" to listOf(me),
            "type" to "group",
            "name" to groupName,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessage" to "",
            "lastMessageAt" to FieldValue.serverTimestamp()
        )).await()
        return ref.id
    }

    suspend fun sendText(conversationId: String, text: String, replyTo: String = "") {
        val clean = text.trim()
        require(clean.isNotBlank() && clean.length <= 10000) { "Message is empty or too long." }
        val uid = ensureSignedIn()
        val conversation = db.collection("connect_conversations").document(conversationId)
        val message = conversation.collection("messages").document()
        val batch = db.batch()
        batch.set(message, mapOf(
            "conversationId" to conversationId,
            "senderId" to uid,
            "text" to clean,
            "type" to "text",
            "mediaUrl" to "",
            "replyTo" to replyTo,
            "edited" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "deletedFor" to emptyList<String>(),
            "reactions" to emptyMap<String, String>()
        ))
        batch.update(conversation, mapOf(
            "lastMessage" to clean.take(160),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to uid
        ))
        batch.commit().await()
    }

    suspend fun uploadAttachment(
        conversationId: String,
        uri: Uri,
        mimeType: String,
        displayName: String
    ): String {
        val uid = ensureSignedIn()
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        val ref = storage.reference.child(
            "connect_media/$conversationId/$uid/${System.currentTimeMillis()}_$safeName"
        )
        ref.putFile(uri, com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(mimeType).build()).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun sendMedia(
        conversationId: String,
        mediaUrl: String,
        mediaType: String,
        caption: String = ""
    ) {
        require(mediaType in setOf("image", "video", "audio", "document"))
        val uid = ensureSignedIn()
        val conversation = db.collection("connect_conversations").document(conversationId)
        val message = conversation.collection("messages").document()
        val batch = db.batch()
        batch.set(message, mapOf(
            "conversationId" to conversationId,
            "senderId" to uid,
            "text" to caption.trim().take(2000),
            "type" to mediaType,
            "mediaUrl" to mediaUrl,
            "replyTo" to "",
            "edited" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "deletedFor" to emptyList<String>(),
            "reactions" to emptyMap<String, String>()
        ))
        batch.update(conversation, mapOf(
            "lastMessage" to if (caption.isBlank()) mediaType.uppercase() else caption.take(160),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to uid
        ))
        batch.commit().await()
    }

    suspend fun editMessage(conversationId: String, messageId: String, newText: String) {
        val clean = newText.trim()
        require(clean.isNotBlank() && clean.length <= 10000)
        db.collection("connect_conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update("text", clean, "edited", true, "editedAt", FieldValue.serverTimestamp())
            .await()
    }

    suspend fun addReaction(
        conversationId: String,
        messageId: String,
        emoji: String
    ) {
        require(emoji.length <= 12)
        val uid = ensureSignedIn()
        db.collection("connect_conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update("reactions.$uid", emoji).await()
    }

    suspend fun publishStatus(text: String, mediaUrl: String = "", mediaType: String = "text") {
        val uid = ensureSignedIn()
        db.collection("connect_status").add(mapOf(
            "ownerId" to uid,
            "text" to text.take(1000),
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to System.currentTimeMillis() + 24L * 60L * 60L * 1000L
        )).await()
    }

    fun listenToMessages(
        conversationId: String,
        onMessages: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("connect_conversations").document(conversationId)
            .collection("messages").orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error)
                else onMessages(snapshot?.documents?.mapNotNull { it.toChatMessage() }.orEmpty())
            }
    }

    fun listenToMyConversations(
        onConversationIds: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val uid = currentUid ?: return db.collection("connect_conversations")
            .whereArrayContains("members", "__not_signed_in__")
            .addSnapshotListener { _, error -> if (error != null) onError(error) }
        return db.collection("connect_conversations")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error)
                else onConversationIds(snapshot?.documents?.map { it.id }.orEmpty())
            }
    }

    suspend fun updatePrivacy(discoverable: Boolean, readReceipts: Boolean, lastSeen: Boolean) {
        val uid = ensureSignedIn()
        db.collection("connect_profiles").document(uid).set(mapOf(
            "discoverable" to discoverable,
            "readReceipts" to readReceipts,
            "lastSeenVisible" to lastSeen,
            "privacyUpdatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge()).await()
    }

    suspend fun deleteLocalAndCloudAccountData() {
        val uid = ensureSignedIn()
        db.collection("connect_profiles").document(uid).delete().await()
        // Account deletion is deliberately not automatic: conversation retention,
        // legal requirements and media ownership need an explicit user-facing flow.
    }

    private fun DocumentSnapshot.toPerson(): Person? {
        val id = getString("uid") ?: return null
        return Person(
            uid = id,
            username = getString("username") ?: "",
            displayName = getString("displayName") ?: "",
            photoUrl = getString("photoUrl") ?: "",
            about = getString("about") ?: ""
        )
    }

    private fun DocumentSnapshot.toChatMessage(): ChatMessage? {
        val sender = getString("senderId") ?: return null
        return ChatMessage(
            id = id,
            conversationId = getString("conversationId") ?: "",
            senderId = sender,
            text = getString("text") ?: "",
            type = getString("type") ?: "text",
            mediaUrl = getString("mediaUrl") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            replyTo = getString("replyTo") ?: "",
            edited = getBoolean("edited") ?: false
        )
    }
}

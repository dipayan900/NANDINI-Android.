package com.dipayan.nandini

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.security.MessageDigest

/**
 * Receives data-only chat pushes. Message text is deliberately never sent in FCM:
 * this remains safe when E2EE is added and avoids exposing message contents in
 * notification-provider payloads.
 */
class ArushiConnectMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        ArushiConnectPushRegistration.register(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val incomingType = message.data["type"].orEmpty()
        val conversationId = message.data["conversationId"].orEmpty()
        if (incomingType == "call_invite" && conversationId.isNotBlank()) {
            val callChannel = "arushi_connect_calls"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(
                    android.app.NotificationChannel(callChannel, "ARUSHI incoming calls", android.app.NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val answer = Intent(this, ArushiVoipActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("conversation_id", conversationId)
                putExtra("incoming_call", true)
            }
            val answerPending = PendingIntent.getActivity(
                this, conversationId.hashCode(), answer,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val callNotification = NotificationCompat.Builder(this, callChannel)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Incoming ARUSHI call")
                .setContentText("A member of your ARUSHI Connect conversation is calling.")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(answerPending)
                .addAction(android.R.drawable.sym_action_call, "ANSWER", answerPending)
                .build()
            try {
                NotificationManagerCompat.from(this).notify(("call:" + conversationId).hashCode(), callNotification)
            } catch (_: SecurityException) { }
            return
        }
        val title = "ARUSHI Connect"
        val body = "You have a new message"
        val channelId = "arushi_connect_messages"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                channelId, "ARUSHI messages", android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New message alerts from ARUSHI Connect" }
            manager.createNotificationChannel(channel)
        }

        val open = Intent(this, ArushiConnectActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversation_id", conversationId)
        }
        val pending = PendingIntent.getActivity(
            this, conversationId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                conversationId.hashCode(), notification
            )
        } catch (_: SecurityException) {
            // Android 13+ notification permission may be denied by the user.
        }
    }
}

/** Stores the current FCM token under the authenticated user's private device list. */
object ArushiConnectPushRegistration {
    fun register(context: Context, token: String) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        fun save(uid: String) {
            val tokenId = MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
            db.collection("connect_profiles").document(uid)
                .collection("devices").document(tokenId)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        }
        val current = auth.currentUser
        if (current != null) {
            save(current.uid)
        } else {
            auth.signInAnonymously().addOnSuccessListener { result ->
                result.user?.uid?.let(::save)
            }
        }
    }
}

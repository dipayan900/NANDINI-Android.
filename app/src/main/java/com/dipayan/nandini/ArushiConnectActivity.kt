package com.dipayan.nandini

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** ARUSHI Connect workspace using the existing ARUSHI Experience palette. */
class ArushiConnectActivity : Activity() {
    private val repo by lazy { ArushiConnectRepository() }
    private val localStore by lazy { ArushiConnectLocalStore(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val palette by lazy { ArushiExperience.palette(this) }
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private var messageListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var pendingConversation: String? = null
    private var currentChatMessages: List<ArushiConnectRepository.ChatMessage> = emptyList()
    private val mediaDownloadsInFlight = mutableSetOf<String>()
    private val pickAttachment = 8401
    private val requestAudio = 8402

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)
        showLoading()
        scope.launch {
            try {
                repo.ensureSignedIn()
                val profile = repo.getMyProfile()
                if (profile == null) showOnboarding() else showHome(profile.username)
            } catch (e: Exception) {
                showError("ARUSHI Connect could not start: ${e.message ?: "Check your connection."}")
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun base(title: String, subtitle: String = "") {
        scroll = ScrollView(this).apply { setBackgroundColor(palette.background); isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(26))
            setBackgroundColor(palette.background)
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(label("✦  ARUSHI CONNECT", 13f, palette.accentSecondary, true))
        root.addView(label(title, 27f, palette.text, true))
        if (subtitle.isNotBlank()) root.addView(label(subtitle, 13f, palette.muted))
        root.addView(space(12))
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.START
            if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun field(hintText: String, singleLine: Boolean = true) =
        EditText(this).apply {
            hint = hintText
            setSingleLine(singleLine)
            textSize = 15f
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(palette.panel)
        }

    private fun action(title: String, onClick: () -> Unit) =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            setOnClickListener { onClick() }
        }

    private fun showLoading() {
        base("Connecting…", "Setting up your ARUSHI identity")
        root.addView(label("Connecting to ARUSHI services…", 14f, palette.muted))
    }

    private fun showError(message: String) {
        base("Connect unavailable", "No messages have been sent.")
        root.addView(label(message, 14f, Color.rgb(255, 170, 170)))
        root.addView(action("RETRY") { recreate() })
    }

    private fun showOnboarding() {
        base("Your space. Your people.", "Create your ARUSHI username. Chat over the internet — no phone number or SMS needed.")
        val username = field("Choose username (3–20 characters)")
        val displayName = field("Display name")
        root.addView(label("ARUSHI ID", 12f, palette.accentSecondary, true))
        root.addView(username)
        root.addView(label("DISPLAY NAME", 12f, palette.accentSecondary, true))
        root.addView(displayName)
        root.addView(action("CREATE MY ARUSHI ID") {
            scope.launch {
                try {
                    val profile = repo.createUsername(username.text.toString(), displayName.text.toString())
                    showHome(profile.username)
                } catch (e: Exception) { toast(e.message ?: "Could not create username.") }
            }
        })
        root.addView(label("People can find you using your exact ARUSHI username. ARUSHI Connect does not need SMS verification.", 12f, palette.muted))
    }

    private fun showHome(username: String) {
        messageListener?.remove()
        base("Messages", "@$username  •  ARUSHI purple/blue")
        root.addView(action("＋  FIND PEOPLE / NEW CHAT") { showDiscover() })
        root.addView(action("◉  STATUS UPDATES") { showStatus() })
        root.addView(action("☎  INTERNET CALLS") { showCallInfo() })
        root.addView(action("⚙  PRIVACY & STORAGE") { showSettings() })
        root.addView(space(14))
        root.addView(label("YOUR CHATS", 12f, palette.accentSecondary, true))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        messageListener = repo.listenToMyConversations({ ids ->
            list.removeAllViews()
            if (ids.isEmpty()) list.addView(label("No chats yet. Find someone by their ARUSHI ID to start.", 14f, palette.muted))
            ids.forEach { id -> list.addView(action("◈  Conversation  •  ${id.take(12)}…") { openChat(id) }) }
        }, { toast("Chat list error: ${it.message}") })
    }

    private fun showDiscover() {
        base("Find people", "Search by exact ARUSHI username or share your username.")
        val query = field("@username")
        root.addView(query)
        root.addView(action("SEARCH ARUSHI ID") {
            scope.launch {
                try {
                    val person = repo.findByUsername(query.text.toString())
                    if (person == null) toast("No matching ARUSHI ID found.")
                    else AlertDialog.Builder(this@ArushiConnectActivity)
                        .setTitle(person.displayName)
                        .setMessage("@${person.username}")
                        .setPositiveButton("START CHAT") { _, _ ->
                            scope.launch {
                                try { openChat(repo.createDirectConversation(person.uid)) }
                                catch (e: Exception) { toast(e.message ?: "Could not start chat.") }
                            }
                        }.setNegativeButton("CANCEL", null).show()
                } catch (e: Exception) { toast(e.message ?: "Search failed.") }
            }
        })
        root.addView(action("← BACK TO CHATS") { backHome() })
    }

    private fun backHome() {
        scope.launch {
            try { showHome(repo.getMyProfile()?.username ?: "") }
            catch (e: Exception) { showError(e.message ?: "Could not load profile.") }
        }
    }

    private fun openChat(conversationId: String) {
        messageListener?.remove()
        base("Conversation", "ARUSHI Connect  •  ${conversationId.take(12)}")
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(messages)
        renderMessages(messages, localStore.readConversation(conversationId))
        val input = field("Message…")
        root.addView(input)
        root.addView(action("SEND MESSAGE") {
            val text = input.text.toString()
            scope.launch {
                try { repo.sendText(conversationId, text); input.setText("") }
                catch (e: Exception) { toast(e.message ?: "Message could not be sent.") }
            }
        })
        root.addView(action("＋  ATTACH PHOTO / VIDEO / DOCUMENT / AUDIO") {
            pendingConversation = conversationId
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "audio/*", "application/pdf", "application/*"))
            }
            startActivityForResult(intent, pickAttachment)
        })
        root.addView(action("🎙  RECORD VOICE NOTE") { toggleVoiceNote(conversationId) })
        root.addView(action("☎  CALL SETUP") { showCallInfo() })
        root.addView(action("DELETE CHAT HISTORY ON THIS PHONE") {
            AlertDialog.Builder(this)
                .setTitle("Delete local chat history?")
                .setMessage("This removes this conversation's saved history from this phone only. The other person's copy is not deleted.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE") { _, _ ->
                    localStore.clearConversation(conversationId, currentChatMessages)
                    currentChatMessages = emptyList()
                    messages.removeAllViews()
                    messages.addView(label("Local chat history cleared on this phone.", 13f, palette.muted))
                    toast("This phone's chat history was cleared.")
                }.show()
        })
        root.addView(action("← BACK TO CHATS") { backHome() })
        messageListener = repo.listenToMessages(conversationId, { items ->
            val visible = localStore.visible(conversationId, items)
            localStore.cache(visible)
            currentChatMessages = localStore.readConversation(conversationId)
            renderMessages(messages, currentChatMessages)
            visible.forEach { item ->
                if (item.senderId == repo.currentUid) {
                    if (item.type != "text" && item.deliveredBy.size >= 2) {
                        scope.launch {
                            runCatching { repo.cleanupDeliveredMedia(conversationId, item.id) }
                        }
                    }
                } else if (item.type == "text") {
                    scope.launch {
                        runCatching { repo.acknowledgeMessage(conversationId, item.id) }
                    }
                } else {
                    val cached = currentChatMessages.firstOrNull { it.id == item.id }
                    val localFile = cached?.localMediaPath?.takeIf { it.isNotBlank() }?.let(::File)
                    if (localFile != null && localFile.exists()) {
                        scope.launch {
                            runCatching { repo.acknowledgeMessage(conversationId, item.id) }
                        }
                    }
                }
            }
        }, { toast("Could not load messages: ${it.message}") })
    }

    private fun renderMessages(
        container: LinearLayout,
        items: List<ArushiConnectRepository.ChatMessage>
    ) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(label("No messages saved on this phone yet.", 13f, palette.muted))
            return
        }
        items.forEach { item ->
            val mine = item.senderId == repo.currentUid
            val mediaReady = item.localMediaPath.isNotBlank() && File(item.localMediaPath).exists()
            val body = when (item.type) {
                "text" -> item.text
                "image" -> if (mediaReady) "▧  Image  •  TAP TO OPEN" else "▧  Image  •  TAP TO DOWNLOAD"
                "video" -> if (mediaReady) "▶  Video  •  TAP TO OPEN" else "▶  Video  •  TAP TO DOWNLOAD"
                "audio" -> if (mediaReady) "♫  Voice note  •  TAP TO PLAY" else "♫  Voice note  •  TAP TO DOWNLOAD"
                else -> if (mediaReady) "▤  File  •  TAP TO OPEN" else "▤  File  •  TAP TO DOWNLOAD"
            }
            val messageView = label((if (mine) "YOU  " else "THEM  ") + body +
                if (item.edited) "  (edited)" else "", 14f,
                if (mine) palette.accentSecondary else palette.text)
            if (item.type != "text" && item.mediaUrl.isNotBlank()) {
                messageView.setOnClickListener { downloadOrOpenMedia(item, container) }
            }
            messageView.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete this message here?")
                    .setMessage("This removes the saved copy from this phone only. It does not delete the other person's message.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE HERE") { _, _ ->
                        localStore.hideMessageLocally(item.conversationId, item.id)
                        currentChatMessages = currentChatMessages.filterNot { it.id == item.id }
                        renderMessages(container, currentChatMessages)
                    }.show()
                true
            }
            container.addView(messageView)
        }
    }

    private fun downloadOrOpenMedia(
        message: ArushiConnectRepository.ChatMessage,
        container: LinearLayout
    ) {
        val existing = message.localMediaPath.takeIf { it.isNotBlank() }?.let(::File)
        if (existing != null && existing.exists()) {
            openLocalMedia(existing, message.type)
            return
        }
        if (!mediaDownloadsInFlight.add(message.id)) return
        toast("Downloading media to ARUSHI's private app storage…")
        scope.launch {
            val destination = localStore.mediaFile(message.conversationId, message.id)
            try {
                withContext(Dispatchers.IO) {
                    destination.parentFile?.mkdirs()
                    repo.downloadAttachment(message.mediaUrl, destination)
                }
                localStore.saveMediaPath(message.conversationId, message.id, destination.absolutePath)
                currentChatMessages = localStore.readConversation(message.conversationId)
                renderMessages(container, currentChatMessages)
                repo.acknowledgeMessage(message.conversationId, message.id)
                openLocalMedia(destination, message.type)
            } catch (e: Exception) {
                destination.delete()
                toast(e.message ?: "Could not download this attachment.")
            } finally {
                mediaDownloadsInFlight.remove(message.id)
            }
        }
    }

    private fun getDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return "attachment_${System.currentTimeMillis()}"
    }

    private fun openLocalMedia(file: File, type: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this, packageName + ".connect.files", file
            )
            val mime = when (type) {
                "image" -> "image/*"
                "video" -> "video/*"
                "audio" -> "audio/*"
                else -> MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"
            }
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            toast("No app is available to open this file.")
        } catch (e: Exception) {
            toast(e.message ?: "Could not open this file.")
        }
    }

    private fun toggleVoiceNote(conversationId: String) {
        if (recorder != null) {
            try {
                recorder?.stop()
                recorder?.release()
                recorder = null
                val file = voiceFile
                if (file != null && file.exists() && file.length() > 0L) scope.launch {
                    try {
                        val url = repo.uploadAttachment(conversationId, Uri.fromFile(file), "audio/mp4", file.name)
                        repo.sendMedia(conversationId, url, "audio")
                        file.delete()
                        voiceFile = null
                        toast("Voice note sent.")
                    } catch (e: Exception) { toast(e.message ?: "Voice note upload failed.") }
                }
            } catch (e: Exception) {
                runCatching { recorder?.release() }
                recorder = null
                toast("Voice note recording failed.")
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            return
        }
        try {
            val file = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            voiceFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            toast("Recording… tap again to send the voice note.")
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            toast("Microphone could not start: ${e.message}")
        }
    }

    private fun showStatus() {
        base("Status", "Text status updates currently use a 24-hour expiry field.")
        val text = field("What's happening?", false)
        root.addView(text)
        root.addView(action("POST TEXT STATUS") {
            scope.launch {
                try { repo.publishStatus(text.text.toString()); toast("Status posted.") }
                catch (e: Exception) { toast(e.message ?: "Could not post status.") }
            }
        })
        root.addView(action("← BACK") { backHome() })
    }

    private fun showCallInfo() {
        AlertDialog.Builder(this)
            .setTitle("ARUSHI internet calls")
            .setMessage("The existing WebRTC prototype still needs a deployed signaling service, authenticated call invites, TURN relay, incoming-call notifications and video-track support. It is not yet a WhatsApp-equivalent calling service.")
            .setPositiveButton("OPEN CALL TEST") { _, _ ->
                startActivity(Intent(this, ArushiVoipActivity::class.java))
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showSettings() {
        base("Privacy & storage", "Your ARUSHI identity and visibility controls.")
        val discoverable = android.widget.CheckBox(this).apply { text = "Allow username discovery"; isChecked = true; setTextColor(palette.text) }
        val receipts = android.widget.CheckBox(this).apply { text = "Read receipts"; isChecked = true; setTextColor(palette.text) }
        val lastSeen = android.widget.CheckBox(this).apply { text = "Show last seen"; isChecked = false; setTextColor(palette.text) }
        root.addView(discoverable); root.addView(receipts); root.addView(lastSeen)
        root.addView(action("SAVE PRIVACY SETTINGS") {
            scope.launch {
                try {
                    repo.updatePrivacy(discoverable.isChecked, receipts.isChecked, lastSeen.isChecked)
                    toast("Privacy settings saved.")
                } catch (e: Exception) { toast(e.message ?: "Could not save settings.") }
            }
        })
        root.addView(label("Chat history is cached in this phone's private ARUSHI app data. Clearing it here does not erase the other participant's copy. Android also removes this local database if ARUSHI app data is cleared or the app is uninstalled.", 12f, palette.muted))
        root.addView(action("CLEAR ALL LOCAL CHAT HISTORY") {
            AlertDialog.Builder(this)
                .setTitle("Clear all chats on this phone?")
                .setMessage("This clears locally saved message history on this device only. Other participants' copies are unchanged.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR ALL") { _, _ ->
                    localStore.clearAllLocalHistory()
                    currentChatMessages = emptyList()
                    toast("All locally cached chat history was cleared.")
                }.show()
        })
        root.addView(label("Messages still pass through the online delivery service so they can reach recipients and remain available while they are offline.", 12f, palette.muted))
        root.addView(action("← BACK TO CHATS") { backHome() })
    }

    @Deprecated("Deprecated by Android; retained for document picker compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickAttachment && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val conversationId = pendingConversation ?: return
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> "document"
            }
            scope.launch {
                try {
                    val url = repo.uploadAttachment(conversationId, uri, mime, getDisplayName(uri))
                    repo.sendMedia(conversationId, url, type)
                    toast("Attachment sent.")
                } catch (e: Exception) { toast(e.message ?: "Attachment upload failed.") }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        messageListener?.remove()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        scope.cancel()
        super.onDestroy()
    }
}

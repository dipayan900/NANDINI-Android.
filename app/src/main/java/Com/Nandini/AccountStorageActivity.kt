package com.dipayan.nandini

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.content.res.Configuration
import android.view.View
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.UUID

/** Local guest profile, portable backup/restore, and device storage controls. */
class AccountStorageActivity : Activity() {
    private var bg = Color.rgb(10, 8, 22)
    private var panel = Color.rgb(29, 20, 48)
    private var accent = Color.rgb(190, 139, 255)
    private var white = Color.rgb(250, 247, 255)
    private var muted = Color.rgb(185, 174, 215)
    private val accountPrefs by lazy { getSharedPreferences("arushi_guest_account", Context.MODE_PRIVATE) }
    private val storagePrefs by lazy { getSharedPreferences("arushi_storage", Context.MODE_PRIVATE) }
    private val chatStore by lazy { ChatStore(this) }
    private lateinit var content: LinearLayout

    companion object {
        private const val REQUEST_EXPORT = 5101
        private const val REQUEST_IMPORT = 5102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        bg = if (isNight) Color.rgb(10, 8, 22) else Color.rgb(248, 246, 252)
        panel = if (isNight) Color.rgb(29, 20, 48) else Color.rgb(255, 255, 255)
        accent = if (isNight) Color.rgb(190, 139, 255) else Color.rgb(112, 63, 157)
        white = if (isNight) Color.rgb(250, 247, 255) else Color.rgb(35, 28, 43)
        muted = if (isNight) Color.rgb(185, 174, 215) else Color.rgb(100, 91, 112)
        window.decorView.systemUiVisibility = if (isNight) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "ARUSHI • ACCOUNT & STORAGE"
            textSize = 19f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(makeButton("← BACK TO STUDIO") { finish() }, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        render()
    }

    private fun render() {
        content.removeAllViews()
        val guestId = accountPrefs.getString("guest_id", null)
        val guestName = accountPrefs.getString("display_name", null)
        addSection(
            "GUEST ACCOUNT",
            if (guestId == null) {
                "Create a private guest profile on this device. No email, phone number, or online sign-in is required."
            } else {
                "Profile: $guestName\nGuest ID: $guestId\nThis profile is local-only; move it to another device using an ARUSHI backup."
            }
        )
        addButton(if (guestId == null) "CREATE GUEST ACCOUNT" else "EDIT GUEST DISPLAY NAME") {
            editGuestName(guestId == null)
        }
        addButton("COPY GUEST ID") {
            if (guestId == null) {
                toast("Create a guest account first.")
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ARUSHI Guest ID", guestId))
                toast("Guest ID copied.")
            }
        }

        addSection(
            "PORTABLE ACCOUNT BACKUP",
            "Export chats, generated images, Studio memories and tasks, guest profile, and app preferences into one ZIP file. Import it on another device with ARUSHI installed. The ZIP is not encrypted, so keep it private."
        )
        addButton("EXPORT FULL BACKUP (.ZIP)") { launchExport() }
        addButton("IMPORT BACKUP FROM ANOTHER DEVICE") { launchImport() }
        addSection(
            "CHAT RETENTION",
            "Automatic cleanup is OFF by default. When enabled, ARUSHI removes conversations older than the selected age while keeping the currently open chat."
        )
        val days = storagePrefs.getInt("auto_delete_days", 0)
        addButton("AUTO-DELETE: " + when (days) {
            7 -> "OLDER THAN 7 DAYS"
            30 -> "OLDER THAN 30 DAYS"
            90 -> "OLDER THAN 90 DAYS"
            else -> "OFF"
        }) { chooseRetention() }
        addButton("DELETE CHATS OLDER THAN 30 DAYS NOW") {
            AlertDialog.Builder(this)
                .setTitle("Delete old conversations?")
                .setMessage("This permanently deletes saved chats last updated more than 30 days ago. Export a backup first if you may need them.")
                .setPositiveButton("DELETE") { _, _ ->
                    val keepId = getSharedPreferences("nandini_chat", Context.MODE_PRIVATE)
                        .getString("currentChatId", null)
                    val deleted = chatStore.deleteOlderThan(
                        System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L,
                        keepId
                    )
                    cleanupOrphanedImages()
                    toast("Deleted $deleted old conversation(s).")
                    render()
                }
                .setNegativeButton("CANCEL", null)
                .show().also { styleDialog(it) }
        }

        addSection("DEVICE STORAGE", storageSummary())
        addButton("CLEAR APP CACHE") {
            AlertDialog.Builder(this)
                .setTitle("Clear temporary cache?")
                .setMessage("Temporary cache files will be removed. Saved chats, memories, generated images and your guest profile will remain.")
                .setPositiveButton("CLEAR CACHE") { _, _ ->
                    cacheDir.listFiles()?.forEach { deleteRecursively(it) }
                    toast("Temporary cache cleared.")
                    render()
                }
                .setNegativeButton("CANCEL", null)
                .show().also { styleDialog(it) }
        }
        addButton("DELETE ALL SAVED GENERATED IMAGES") {
            AlertDialog.Builder(this)
                .setTitle("Delete generated images?")
                .setMessage("This permanently removes every saved generated image from this device. Chat messages remain, but their image previews will no longer be available. Export a backup first if you want to keep them.")
                .setPositiveButton("DELETE IMAGES") { _, _ ->
                    deleteRecursively(File(filesDir, "generated_images"))
                    toast("Saved generated images deleted.")
                    render()
                }
                .setNegativeButton("CANCEL", null)
                .show().also { styleDialog(it) }
        }
        addSection(
            "IMPORTANT",
            "Phone storage is always finite, so no app can provide literally infinite storage. Backups help move or archive data. The 614 MB local AI model is excluded from backups to keep them manageable and can be downloaded again from Studio. Sign-in tokens and credentials are excluded for security."
        )
    }

    private fun editGuestName(create: Boolean) {
        val editor = EditText(this).apply {
            setText(accountPrefs.getString("display_name", ""))
            hint = "Example: My ARUSHI Guest"
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 16f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(113, 82, 150))
            }
        }
        AlertDialog.Builder(this)
            .setTitle(if (create) "Create guest account" else "Guest display name")
            .setMessage("This is a local profile, not an online login.")
            .setView(editor)
            .setPositiveButton(if (create) "CREATE" else "SAVE") { _, _ ->
                val name = editor.text.toString().trim().take(32).ifBlank { "Guest" }
                val id = accountPrefs.getString("guest_id", null) ?: UUID.randomUUID().toString()
                accountPrefs.edit()
                    .putString("guest_id", id)
                    .putString("display_name", name)
                    .putLong("created_at", accountPrefs.getLong("created_at", System.currentTimeMillis()))
                    .apply()
                toast("Guest profile saved on this device.")
                render()
            }
            .setNegativeButton("CANCEL", null)
            .show().also { styleDialog(it) }
    }

    private fun launchExport() {
        if (accountPrefs.getString("guest_id", null) == null) {
            AlertDialog.Builder(this)
                .setTitle("Create your guest account first")
                .setMessage("A guest profile gives this backup a portable account identity.")
                .setPositiveButton("CREATE ACCOUNT") { _, _ -> editGuestName(true) }
                .setNegativeButton("CANCEL", null)
                .show().also { styleDialog(it) }
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "ARUSHI-Guest-Backup.zip")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Uses the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT -> exportBackup(uri)
            REQUEST_IMPORT -> confirmImport(uri)
        }
    }

    private fun exportBackup(uri: Uri) {
        toast("Preparing your backup…")
        Thread {
            try {
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Couldn't open the selected backup destination.")
                output.use { BackupRestoreManager.createBackup(this, it) }
                runOnUiThread {
                    if (!isFinishing) toast("Backup exported successfully. Keep the ZIP file somewhere safe.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing) toast("Backup export failed: ${e.message ?: "Please try again."}")
                }
            }
        }.start()
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Restore this ARUSHI backup?")
            .setMessage("Restoring replaces the current chats, Studio records, guest profile and saved app preferences with the backup contents. Export your current data first if you want to keep it. The restore is applied after ARUSHI restarts.")
            .setPositiveButton("CONTINUE") { _, _ ->
                toast("Checking backup file…")
                Thread {
                    try {
                        val input = contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Couldn't open the selected file.")
                        input.use { BackupRestoreManager.stageRestore(this, it) }
                        runOnUiThread {
                            if (!isFinishing) {
                                AlertDialog.Builder(this)
                                    .setTitle("Backup verified")
                                    .setMessage("The backup is ready. ARUSHI will close now. Open the app again to apply the restore.")
                                    .setPositiveButton("CLOSE ARUSHI") { _, _ -> finishAffinity() }
                                    .setCancelable(false)
                                    .show().also { styleDialog(it) }
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            if (!isFinishing) toast("Import failed: ${e.message ?: "This file may not be a valid ARUSHI backup."}")
                        }
                    }
                }.start()
            }
            .setNegativeButton("CANCEL", null)
            .show().also { styleDialog(it) }
    }

    private fun chooseRetention() {
        val options = arrayOf("Off — keep all chats", "Delete chats older than 7 days", "Delete chats older than 30 days", "Delete chats older than 90 days")
        val values = intArrayOf(0, 7, 30, 90)
        val current = storagePrefs.getInt("auto_delete_days", 0)
        val selected = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Automatic chat cleanup")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                storagePrefs.edit().putInt("auto_delete_days", values[which]).apply()
                dialog.dismiss()
                val deleted = applyRetentionNow(values[which])
                toast(if (values[which] == 0) "Automatic cleanup disabled." else "Retention saved. Removed $deleted expired chat(s).")
                render()
            }
            .setNegativeButton("CLOSE", null)
            .show().also { styleDialog(it) }
    }

    private fun applyRetentionNow(days: Int): Int {
        if (days <= 0) return 0
        val keepId = getSharedPreferences("nandini_chat", Context.MODE_PRIVATE)
            .getString("currentChatId", null)
        val deleted = chatStore.deleteOlderThan(
            System.currentTimeMillis() - days.toLong() * 24L * 60L * 60L * 1000L,
            keepId
        )
        if (deleted > 0) cleanupOrphanedImages()
        return deleted
    }

    private fun cleanupOrphanedImages() {
        val directory = File(filesDir, "generated_images")
        if (!directory.isDirectory) return
        val referenced = chatStore.referencedImageNames()
        directory.listFiles()?.filter { it.isFile && it.name !in referenced }?.forEach { it.delete() }
    }

    private fun storageSummary(): String {
        val databaseSize = directorySize(File(applicationInfo.dataDir, "databases"))
        val imageSize = directorySize(File(filesDir, "generated_images"))
        val cacheSize = directorySize(cacheDir)
        val model = LocalAiRuntime.modelFile(this)
        val modelSize = if (model.isFile) model.length() else 0L
        val chats = chatStore.chatCount()
        return "Saved conversations: $chats\nChat database: ${formatBytes(databaseSize)}\nGenerated images: ${formatBytes(imageSize)}\nTemporary cache: ${formatBytes(cacheSize)}\nOffline AI model: ${if (modelSize > 0L) formatBytes(modelSize) else "not downloaded"}"
    }

    private fun addSection(title: String, description: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = .08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(dp(3), dp(15), dp(3), dp(5))
        })
        content.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(3), 0, dp(3), dp(10))
        })
    }

    private fun addButton(label: String, action: () -> Unit) {
        content.addView(makeButton(label, action), LinearLayout.LayoutParams(-1, dp(48)).apply {
            bottomMargin = dp(8)
        })
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(white)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.argb(170, 150, 105, 210))
            }
            setOnClickListener { action() }
        }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), accent)
        })
        dialog.window?.setDimAmount(0.72f)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(muted)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
        dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.setTextColor(white)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(muted)
    }

    private fun directorySize(file: File): Long =
        if (!file.exists()) 0L else if (file.isFile) file.length()
        else file.listFiles()?.sumOf { directorySize(it) } ?: 0L

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }

    override fun onDestroy() {
        if (this::content.isInitialized) chatStore.close()
        super.onDestroy()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}

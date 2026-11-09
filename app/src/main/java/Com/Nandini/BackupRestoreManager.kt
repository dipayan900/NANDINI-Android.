package com.dipayan.nandini

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, local-first guest-account backup.
 *
 * The archive contains conversations, app preferences, Studio data and app files.
 * OAuth/session credentials and the large downloadable language model are excluded.
 * Restores are staged and applied on the next cold app launch so SQLite is not
 * overwritten while the chat screen still has an open database connection.
 */
object BackupRestoreManager {
    private const val FORMAT_VERSION = 1
    private const val PENDING_NAME = "arushi_pending_restore.zip"
    private const val MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L

    fun createBackup(context: Context, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            val manifest = JSONObject()
                .put("format", "ARUSHI_BACKUP")
                .put("formatVersion", FORMAT_VERSION)
                .put("packageName", context.packageName)
                .put("createdAt", System.currentTimeMillis())
                .put("note", "Credentials and the separately downloadable local AI model are not included.")
            zip.putNextEntry(ZipEntry("backup_manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            addSharedPreferences(context, zip)
            addDirectory(
                File(context.applicationInfo.dataDir, "databases"),
                "databases",
                zip
            )
            addDirectory(
                context.filesDir,
                "files",
                zip,
                excludedTopLevel = setOf("local_models", PENDING_NAME)
            )
        }
    }

    private fun addSharedPreferences(context: Context, zip: ZipOutputStream) {
        val directory = File(context.applicationInfo.dataDir, "shared_prefs")
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") }?.forEach { file ->
            val name = file.name.removeSuffix(".xml")
            if (isSensitivePreferenceFile(name)) return@forEach
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val json = JSONObject()
            values.forEach { (key, value) ->
                if (isSensitiveKey(key) || value == null) return@forEach
                val encoded = when (value) {
                    is String -> JSONObject().put("type", "string").put("value", value)
                    is Boolean -> JSONObject().put("type", "boolean").put("value", value)
                    is Int -> JSONObject().put("type", "int").put("value", value)
                    is Long -> JSONObject().put("type", "long").put("value", value)
                    is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                    is Set<*> -> JSONObject().put(
                        "type", "string_set"
                    ).put("value", JSONArray(value.filterIsInstance<String>()))
                    else -> null
                }
                if (encoded != null) json.put(key, encoded)
            }
            zip.putNextEntry(ZipEntry("prefs/$name.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun isSensitivePreferenceFile(name: String): Boolean {
        val n = name.lowercase()
        return listOf("spotify_auth", "firebase", "appcheck", "credential", "oauth", "auth_token")
            .any { it in n }
    }

    private fun isSensitiveKey(key: String): Boolean {
        val k = key.lowercase()
        return listOf("token", "secret", "password", "credential", "api_key", "client_secret", "auth_code", "verifier")
            .any { it in k }
    }

    private fun addDirectory(
        directory: File,
        archiveRoot: String,
        zip: ZipOutputStream,
        excludedTopLevel: Set<String> = emptySet()
    ) {
        if (!directory.exists()) return
        val root = directory.canonicalFile
        directory.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val firstSegment = relative.substringBefore('/')
            if (firstSegment in excludedTopLevel) return@forEach
            val entryName = "$archiveRoot/$relative"
            zip.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Copy and validate an imported archive, without changing live app data. */
    fun stageRestore(context: Context, input: InputStream) {
        val pending = File(context.filesDir, PENDING_NAME)
        pending.parentFile?.mkdirs()
        FileOutputStream(pending).use { input.copyTo(it) }

        try {
            var manifest: JSONObject? = null
            var total = 0L
            ZipInputStream(FileInputStream(pending).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    validateEntryName(entry.name)
                    if (entry.name == "backup_manifest.json") {
                        manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    } else if (!entry.isDirectory) {
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_ARCHIVE_BYTES) { "Backup is too large to import safely." }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val info = manifest ?: error("This file is not an ARUSHI backup.")
            require(info.optString("format") == "ARUSHI_BACKUP") { "Unsupported backup format." }
            require(info.optInt("formatVersion") == FORMAT_VERSION) { "This backup was created by an incompatible version." }
            require(info.optString("packageName") == context.packageName) { "This backup belongs to a different app package." }
        } catch (e: Exception) {
            pending.delete()
            throw e
        }
    }

    /**
     * Called before MainActivity opens ChatStore. The restore is applied once,
     * then the staging archive is removed.
     */
    fun applyPendingRestore(context: Context): Boolean {
        val pending = File(context.filesDir, PENDING_NAME)
        if (!pending.isFile) return false

        val stage = File(context.cacheDir, "arushi_restore_stage")
        deleteRecursively(stage)
        stage.mkdirs()

        try {
            ZipInputStream(FileInputStream(pending).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    validateEntryName(entry.name)
                    if (entry.isDirectory || entry.name == "backup_manifest.json") {
                        zip.closeEntry()
                        continue
                    }
                    val target = File(stage, entry.name)
                    val stagePath = stage.canonicalPath + File.separator
                    require(target.canonicalPath.startsWith(stagePath)) { "Invalid backup path." }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zip.copyTo(it) }
                    zip.closeEntry()
                }
            }

            val manifestFile = File(stage, "backup_manifest.json")
            val manifest = JSONObject(manifestFile.readText())
            require(manifest.optString("format") == "ARUSHI_BACKUP")
            require(manifest.optString("packageName") == context.packageName)

            File(stage, "prefs").listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach { file ->
                val name = file.nameWithoutExtension
                if (isSensitivePreferenceFile(name)) return@forEach
                val json = JSONObject(file.readText())
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (isSensitiveKey(key)) continue
                    val item = json.optJSONObject(key) ?: continue
                    when (item.optString("type")) {
                        "string" -> editor.putString(key, item.optString("value"))
                        "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
                        "int" -> editor.putInt(key, item.optInt("value"))
                        "long" -> editor.putLong(key, item.optLong("value"))
                        "float" -> editor.putFloat(key, item.optDouble("value").toFloat())
                        "string_set" -> {
                            val array = item.optJSONArray("value") ?: JSONArray()
                            val set = buildSet { for (i in 0 until array.length()) add(array.optString(i)) }
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.commit()
            }

            val stagedDb = File(stage, "databases")
            if (stagedDb.isDirectory) {
                val liveDb = File(context.applicationInfo.dataDir, "databases")
                liveDb.mkdirs()
                liveDb.listFiles()?.forEach { it.delete() }
                stagedDb.listFiles()?.filter { it.isFile }?.forEach { file ->
                    file.copyTo(File(liveDb, file.name), overwrite = true)
                }
            }

            val stagedFiles = File(stage, "files")
            if (stagedFiles.isDirectory) {
                context.filesDir.listFiles()?.filter {
                    it.name != "local_models" && it.name != PENDING_NAME
                }?.forEach { deleteRecursively(it) }
                stagedFiles.listFiles()?.forEach { item ->
                    val destination = File(context.filesDir, item.name)
                    if (item.isDirectory) copyDirectory(item, destination)
                    else item.copyTo(destination, overwrite = true)
                }
            }

            pending.delete()
            deleteRecursively(stage)
            return true
        } catch (e: Exception) {
            deleteRecursively(stage)
            throw e
        }
    }

    private fun validateEntryName(name: String) {
        require(name.isNotBlank() && !name.startsWith("/") && !name.contains("\\") &&
            name.split('/').none { it == ".." || it.isBlank() }) { "The backup contains an unsafe file path." }
        require(name == "backup_manifest.json" ||
            name.startsWith("prefs/") || name.startsWith("databases/") || name.startsWith("files/")) {
            "The backup contains an unsupported data section."
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        destination.mkdirs()
        source.listFiles()?.forEach { child ->
            val target = File(destination, child.name)
            if (child.isDirectory) copyDirectory(child, target)
            else child.copyTo(target, overwrite = true)
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }
}

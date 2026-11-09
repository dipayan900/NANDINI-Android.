package com.dipayan.nandini

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipInputStream

/** Imports complete LocalDream SD 1.5 MNN packages with safe ZIP extraction. */
object LocalDreamModelStore {
    private const val PREFS = "arushi_localdream"
    private const val SELECTED_MODEL = "selected_model"
    private const val MAX_ENTRIES = 256
    private const val MAX_EXPANDED_BYTES = 5L * 1024L * 1024L * 1024L

    val requiredFiles = listOf(
        "tokenizer.json", "clip_v2.mnn", "pos_emb.bin", "token_emb.bin",
        "unet.mnn", "vae_encoder.mnn", "vae_decoder.mnn"
    )

    data class InstalledModel(val id: String, val directory: File)

    fun modelsDirectory(context: Context): File =
        File(context.filesDir, "localdream/models").apply { mkdirs() }

    fun selectedModelId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SELECTED_MODEL, null)
            ?.takeIf { id -> installedModels(context).any { it.id == id } }

    fun select(context: Context, id: String) {
        require(installedModels(context).any { it.id == id }) { "That model is not installed." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_MODEL, id).apply()
    }

    fun installedModels(context: Context): List<InstalledModel> {
        return modelsDirectory(context).listFiles()
            ?.filter { it.isDirectory && isComplete(it) }
            ?.map { InstalledModel(it.name, it) }
            ?.sortedBy { it.id.lowercase() }
            .orEmpty()
    }

    fun delete(context: Context, id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "Invalid model name." }
        val root = modelsDirectory(context).canonicalFile
        val directory = File(root, id).canonicalFile
        require(directory.parentFile == root) { "Invalid model path." }
        check(directory.deleteRecursively()) { "Could not delete the model folder." }
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(SELECTED_MODEL, null) == id) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(SELECTED_MODEL).apply()
        }
    }

    fun importZip(context: Context, uri: Uri, requestedName: String): String {
        val id = requestedName.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_').take(40)
        require(id.isNotBlank()) { "Enter a model name using letters or numbers." }

        val root = modelsDirectory(context)
        val destination = File(root, id)
        require(!destination.exists()) { "A model with this name already exists." }

        val staging = File(root, ".import-$id-" + System.nanoTime())
        check(staging.mkdirs()) { "Could not create a temporary model folder." }

        try {
            var entries = 0
            var expandedBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries++
                        require(entries <= MAX_ENTRIES) { "The ZIP contains too many files." }

                        val name = entry.name.replace('\\', '/')
                        require(!name.startsWith("/") && !name.split('/').any { it == ".." }) {
                            "The ZIP contains an unsafe file path."
                        }
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }

                        val parts = name.split('/').filter { it.isNotBlank() && it != "." }
                        require(parts.isNotEmpty()) { "The ZIP contains an invalid path." }
                        val target = parts.fold(staging) { parent, part -> File(parent, part) }
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            "The ZIP contains an unsafe file path."
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count <= 0) break
                                expandedBytes += count
                                require(expandedBytes <= MAX_EXPANDED_BYTES) {
                                    "The expanded model package exceeds the 5 GB import limit."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("Could not open the selected ZIP file.")

            val candidates = staging.walkTopDown().filter { it.isDirectory }
                .filter { dir -> requiredFiles.all { File(dir, it).isFile && File(dir, it).length() > 0L } }
                .toList()
            require(candidates.size == 1) {
                "Unsupported package. This importer currently accepts LocalDream SD 1.5 MNN packages only."
            }

            val packageDirectory = candidates.single()
            if (packageDirectory == staging) {
                check(staging.renameTo(destination)) { "Could not finish installing the model." }
            } else {
                check(packageDirectory.renameTo(destination)) { "Could not finish installing the model." }
                staging.deleteRecursively()
            }
            return id
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun suggestedName(context: Context, uri: Uri): String {
        val name = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "SD15_Model"
        return name.substringBeforeLast('.', name)
            .replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40)
    }

    private fun isComplete(directory: File): Boolean =
        requiredFiles.all { File(directory, it).isFile && File(directory, it).length() > 0L }
}

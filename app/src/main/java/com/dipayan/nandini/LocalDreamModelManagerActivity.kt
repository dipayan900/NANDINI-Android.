package com.dipayan.nandini

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/** Simple in-app manager for supported LocalDream SD 1.5 MNN model packages. */
class LocalDreamModelManagerActivity : Activity() {
    private val palette get() = ArushiExperience.palette(this)
    private val pageBackground get() = palette.background
    private val panel get() = palette.panel
    private val accent get() = palette.accent
    private val muted get() = palette.muted
    private lateinit var nameInput: EditText
    private lateinit var status: TextView
    private lateinit var modelList: LinearLayout
    private lateinit var importButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        ArushiExperience.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ArushiExperience.applySystemBars(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(pageBackground)
        }
        root.addView(label("ARUSHI MODEL MANAGER", 22f, accent, true))
        root.addView(label(
            "Import a compatible LocalDream SD 1.5 MNN model package as a ZIP. " +
                "A normal .safetensors checkpoint is not a ready-to-run package.",
            13f, muted
        ))

        root.addView(label("MODEL RECOMMENDATIONS", 16f, accent, true), params(-1, -2, 0, 10, 0, 2))
        root.addView(label(
            "Good starting families: Absolute Reality (general photo style), DreamShaper (versatile illustration), " +
                "and Anything V5 (anime illustration). Model quality and speed vary; start at 512×512 and 8 steps.",
            12f, muted
        ))
        root.addView(actionButton("BROWSE CPU / MNN MODEL CATALOG") {
            openExternal("https://huggingface.co/xororz/sd-mnn")
        }, params(-1, dp(44), 0, 4, 0, 6))
        root.addView(actionButton("OPEN MODEL COMPATIBILITY GUIDE") {
            openExternal("https://ld-guide.chino.icu/models/")
        }, params(-1, dp(44), 0, 0, 0, 8))
        root.addView(label(
            "Compatibility warning: choose an SD 1.5 CPU/MNN package only. Qualcomm QNN-only ZIPs are not compatible " +
                "with this CPU/MNN importer. Before importing, check that the ZIP contains tokenizer.json, clip_v2.mnn, " +
                "pos_emb.bin, token_emb.bin, unet.mnn, vae_encoder.mnn and vae_decoder.mnn.",
            11f, muted
        ))

        nameInput = EditText(this).apply {
            hint = "Model name (example: SD15-MyModel)"
            setSingleLine(true)
            setTextColor(palette.text)
            setHintTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        root.addView(nameInput, params(-1, dp(52), 0, 12, 0, 8))

        importButton = actionButton("IMPORT MODEL ZIP") { chooseZip() }
        root.addView(importButton, params(-1, dp(48), 0, 0, 0, 8))
        status = label("No model selected yet.", 12f, accent)
        root.addView(status, params(-1, -2, 0, 4, 0, 12))

        val scroll = ScrollView(this)
        modelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(modelList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actionButton("BACK TO IMAGE STUDIO") { finish() }, params(-1, dp(48), 0, 10, 0, 0))
        setContentView(root)
        refreshModels()
    }

    private fun openExternal(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            status.text = "Could not open the model catalogue: ${it.message}"
        }
    }

    private fun chooseZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_ZIP)
    }

    @Deprecated("Deprecated by Android but retained for API 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ZIP || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val typedName = nameInput.text.toString().trim()
        val modelName = typedName.ifBlank { LocalDreamModelStore.suggestedName(this, uri) }
        importButton.isEnabled = false
        status.text = "Checking and importing the model package… Keep ARUSHI open."
        thread(name = "arushi-model-import") {
            try {
                val id = LocalDreamModelStore.importZip(this, uri, modelName)
                LocalDreamModelStore.select(this, id)
                runOnUiThread {
                    nameInput.setText(id)
                    status.text = "Model imported and selected: $id"
                    refreshModels()
                    importButton.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "Import failed: ${error.message ?: "Unknown error"}"
                    importButton.isEnabled = true
                }
            }
        }
    }

    private fun refreshModels() {
        modelList.removeAllViews()
        val selected = LocalDreamModelStore.selectedModelId(this)
        val models = LocalDreamModelStore.installedModels(this)
        if (models.isEmpty()) {
            modelList.addView(label(
                "No compatible models installed. Import a prepared SD 1.5 MNN ZIP above.",
                13f, muted
            ))
            return
        }
        models.forEach { model ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(panel)
            }
            row.addView(label(
                model.id + if (model.id == selected) "  •  SELECTED" else "",
                15f, if (model.id == selected) accent else Color.WHITE, true
            ))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            actions.addView(actionButton("USE MODEL") {
                runCatching { LocalDreamModelStore.select(this@LocalDreamModelManagerActivity, model.id) }
                    .onSuccess {
                        status.text = "Selected model: ${model.id}"
                        refreshModels()
                    }
                    .onFailure { status.text = it.message ?: "Could not select model." }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
            actions.addView(actionButton("DELETE") {
                runCatching { LocalDreamModelStore.delete(this@LocalDreamModelManagerActivity, model.id) }
                    .onSuccess {
                        status.text = "Deleted ${model.id}"
                        refreshModels()
                    }
                    .onFailure { status.text = it.message ?: "Could not delete model." }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(actions, params(-1, -2, 0, 8, 0, 0))
            modelList.addView(row, params(-1, -2, 0, 0, 0, 8))
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(4), dp(7), dp(4), dp(7))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(82, 55, 135))
        setOnClickListener { action() }
    }

    private fun params(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_ZIP = 6124
    }
}

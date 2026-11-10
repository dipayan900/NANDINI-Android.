package com.dipayan.nandini

import android.content.Context

/** User-adjustable, persisted LocalDream generation parameters. */
data class LocalDreamGenerationSettings(
    val negativePrompt: String = "blurry, low quality, distorted, extra fingers, malformed hands",
    val steps: Int = 8,
    val width: Int = 512,
    val height: Int = 512,
    val guidanceScale: Float = 7.0f,
    val seed: Long? = null,
    val scheduler: String = "dpm"
)

object LocalDreamSettingsStore {
    private const val PREFS = "arushi_localdream_generation"

    fun load(context: Context): LocalDreamGenerationSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawSeed = p.getString("seed", "").orEmpty().trim()
        return LocalDreamGenerationSettings(
            negativePrompt = p.getString("negative_prompt",
                "blurry, low quality, distorted, extra fingers, malformed hands").orEmpty(),
            steps = p.getInt("steps", 8).coerceIn(1, 30),
            width = p.getInt("width", 512).coerceIn(256, 768).let { it - it % 8 },
            height = p.getInt("height", 512).coerceIn(256, 768).let { it - it % 8 },
            guidanceScale = p.getFloat("cfg", 7.0f).coerceIn(1.0f, 15.0f),
            seed = rawSeed.toLongOrNull()?.takeIf { it >= 0L },
            scheduler = p.getString("scheduler", "dpm").orEmpty()
                .takeIf { it in SUPPORTED_SCHEDULERS } ?: "dpm"
        )
    }

    fun save(context: Context, settings: LocalDreamGenerationSettings) {
        require(settings.steps in 1..30) { "Steps must be between 1 and 30." }
        require(settings.width in 256..768 && settings.width % 8 == 0) {
            "Width must be 256–768 and divisible by 8."
        }
        require(settings.height in 256..768 && settings.height % 8 == 0) {
            "Height must be 256–768 and divisible by 8."
        }
        require(settings.guidanceScale in 1.0f..15.0f) { "Guidance scale must be between 1 and 15." }
        require(settings.seed == null || settings.seed >= 0L) { "Seed must be a non-negative number." }
        require(settings.scheduler in SUPPORTED_SCHEDULERS) { "Unsupported scheduler." }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("negative_prompt", settings.negativePrompt)
            .putInt("steps", settings.steps)
            .putInt("width", settings.width)
            .putInt("height", settings.height)
            .putFloat("cfg", settings.guidanceScale)
            .putString("seed", settings.seed?.toString().orEmpty())
            .putString("scheduler", settings.scheduler)
            .apply()
    }

    val SUPPORTED_SCHEDULERS = setOf(
        "dpm", "dpm_karras", "dpm_sde", "dpm_sde_karras",
        "euler_a", "euler_a_karras", "euler", "euler_karras", "lcm"
    )
}

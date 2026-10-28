package com.aliahad.photoeffects.photo

import android.content.Context

/**
 * Persists the export preferences that are not photo-specific. Resolution is deliberately not
 * stored: availability depends on the photo's size, and every photo starts at the source-size
 * default so a small choice on one photo cannot silently downgrade the next one.
 */
object ExportSettings {

    private const val PREFS = "export_settings"
    private const val KEY_QUALITY = "quality"
    private const val KEY_METADATA_MODE = "metadata_mode"

    fun load(context: Context): ExportOptions {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = ExportOptions()
        return defaults.copy(
            quality = prefs.getString(KEY_QUALITY, null)
                ?.let { name -> ExportQuality.entries.firstOrNull { it.name == name } }
                ?: defaults.quality,
            metadata = prefs.getString(KEY_METADATA_MODE, null)
                ?.let { name -> ExportMetadataMode.entries.firstOrNull { it.name == name } }
                ?: defaults.metadata,
        )
    }

    fun save(context: Context, options: ExportOptions) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUALITY, options.quality.name)
            .putString(KEY_METADATA_MODE, options.metadata.name)
            .apply()
    }
}

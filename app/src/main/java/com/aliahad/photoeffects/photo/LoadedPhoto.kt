package com.aliahad.photoeffects.photo

import android.graphics.Bitmap

/**
 * A photo loaded into the editor.
 *
 * [sourceWidth] / [sourceHeight] are the full-resolution dimensions of the original (after EXIF
 * orientation), before the editor's [PhotoLoader.MAX_SOURCE_DIMENSION] downsample; the export
 * planner uses them to offer source-limited resolution tiers.
 *
 * [seed] is derived deterministically from the photo content so the same photo always
 * produces the same dust/grain pattern (and thumbnails match the full preview).
 */
data class LoadedPhoto(
    val bitmap: Bitmap,
    val label: String,
    val sourceUri: String?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val id: Long,
    val seed: Long,
)

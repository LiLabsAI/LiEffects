package com.aliahad.photoeffects.photo

import android.graphics.Bitmap

/**
 * A photo loaded into the editor.
 *
 * [seed] is derived deterministically from the photo content so the same photo always
 * produces the same dust/grain pattern (and thumbnails match the full preview).
 */
data class LoadedPhoto(
    val bitmap: Bitmap,
    val label: String,
    val sourceUri: String?,
    val id: Long,
    val seed: Long,
)

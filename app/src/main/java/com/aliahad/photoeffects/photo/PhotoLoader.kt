package com.aliahad.photoeffects.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Loads photos into the editor:
 *  - bundled CC0/license-free demo photo (assets/demo_photo.jpg) as the always-available fallback
 *  - the Android photo picker (PickVisualMedia) for user photos
 *
 * Both paths downsample to [MAX_SOURCE_DIMENSION] so the editor stays memory friendly, and
 * ImageDecoder applies EXIF orientation for picked images.
 */
object PhotoLoader {

    const val DEMO_ASSET = "demo_photo.jpg"
    const val MAX_SOURCE_DIMENSION = 2400

    private var nextId = 1L

    suspend fun loadDemo(context: Context): LoadedPhoto = withContext(Dispatchers.IO) {
        val bytes = context.assets.open(DEMO_ASSET).use { it.readBytes() }
        val bitmap = decodeByteArray(bytes, MAX_SOURCE_DIMENSION)
        LoadedPhoto(
            bitmap = bitmap,
            label = "Demo photo",
            sourceUri = null,
            id = nextId++,
            seed = contentSeed(bitmap),
        )
    }

    suspend fun loadFromUri(context: Context, uri: Uri): LoadedPhoto = withContext(Dispatchers.IO) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > MAX_SOURCE_DIMENSION) {
                val scale = MAX_SOURCE_DIMENSION.toFloat() / longest
                decoder.setTargetSize(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        LoadedPhoto(
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap,
            label = "Photo",
            sourceUri = uri.toString(),
            id = nextId++,
            seed = contentSeed(bitmap),
        )
    }

    private fun decodeByteArray(bytes: ByteArray, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: error("Could not decode demo photo")
        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        return bitmap
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** Deterministic content-derived seed so the same photo always gets the same grain/dust. */
    private fun contentSeed(bitmap: Bitmap): Long {
        val size = min(16, min(bitmap.width, bitmap.height)).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        if (small !== bitmap) small.recycle()
        var hash = 1125899906842597L
        for (p in pixels) hash = 31 * hash + p
        return hash and 0x7FFFFFFF
    }
}

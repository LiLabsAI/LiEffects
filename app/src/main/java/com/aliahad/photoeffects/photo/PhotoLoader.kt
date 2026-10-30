package com.aliahad.photoeffects.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
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
    /**
     * Editor-only decode cap. The preview card is roughly 1080 px wide, so 1920 keeps detail
     * while cutting the bitmap footprint by ~35% versus the old 2400 cap. Export re-decodes the
     * original source at the planned size.
     */
    const val MAX_SOURCE_DIMENSION = 1920

    private var nextId = 1L

    suspend fun loadDemo(context: Context): LoadedPhoto = withContext(Dispatchers.IO) {
        val bytes = context.assets.open(DEMO_ASSET).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val bitmap = decodeByteArray(bytes, MAX_SOURCE_DIMENSION)
        LoadedPhoto(
            bitmap = bitmap,
            label = "Demo photo",
            sourceUri = null,
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            id = nextId++,
            seed = contentSeed(bitmap),
        )
    }

    suspend fun loadFromUri(context: Context, uri: Uri): LoadedPhoto = withContext(Dispatchers.IO) {
        var sourceWidth = 0
        var sourceHeight = 0
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // The fitted grades are display-referred sRGB; normalize wide-gamut sources so the
            // editor, GPU shaders and JPEG export all operate in the same working space.
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val w = info.size.width
            val h = info.size.height
            sourceWidth = w
            sourceHeight = h
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
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            id = nextId++,
            seed = contentSeed(bitmap),
        )
    }

    /**
     * Decode [photo] at (about) [width] x [height], for saving.
     *
     * The editor bitmap is capped at [MAX_SOURCE_DIMENSION]; export re-decodes the original so
     * applying an effect does not throw away most of the photo's resolution. Falls back to a
     * downscaled copy of the editor bitmap when the source cannot be decoded again.
     */
    suspend fun decodeForExport(
        context: Context,
        photo: LoadedPhoto,
        width: Int,
        height: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val decoded = runCatching {
            val uri = photo.sourceUri?.let(Uri::parse)
            if (uri != null) decodeUriForExport(context, uri, width, height)
            else decodeAssetForExport(context, width, height)
        }.getOrNull() ?: return@withContext downscaleFallback(photo.bitmap, width, height)
        if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false)?.also { decoded.recycle() }
                ?: downscaleFallback(photo.bitmap, width, height)
        }
    }

    private fun decodeUriForExport(context: Context, uri: Uri, width: Int, height: Int): Bitmap =
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri),
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val targetWidth = width.coerceIn(1, info.size.width.coerceAtLeast(1))
            val targetHeight = height.coerceIn(1, info.size.height.coerceAtLeast(1))
            if (targetWidth != info.size.width || targetHeight != info.size.height) {
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }

    private fun decodeAssetForExport(context: Context, width: Int, height: Int): Bitmap {
        val bytes = context.assets.open(DEMO_ASSET).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, max(width, height))
        }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: error("Could not decode demo photo")
        if (bitmap.width != width || bitmap.height != height) {
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    private fun downscaleFallback(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width <= width && bitmap.height <= height) return bitmap
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
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

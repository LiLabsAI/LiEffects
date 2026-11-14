/*
 * LiEffects — Android photo editor
 * Copyright (C) 2026 Ali Ahad
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliahad.photoeffects.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
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

    private val nextId = AtomicLong(1L)

    suspend fun loadDemo(context: Context): LoadedPhoto = decodeOwned({ it.bitmap.recycle() }) {
        val bytes = context.assets.open(DEMO_ASSET).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val bitmap = decodeByteArray(bytes, MAX_SOURCE_DIMENSION)
        try {
            LoadedPhoto(
                bitmap = bitmap,
                label = "Demo photo",
                sourceUri = null,
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                id = nextId.getAndIncrement(),
                seed = contentSeed(bitmap),
            )
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
    }

    suspend fun loadFromUri(context: Context, uri: Uri): LoadedPhoto = decodeOwned({ it.bitmap.recycle() }) {
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
        val normalized = toArgb8888(bitmap)
        try {
            LoadedPhoto(
                bitmap = normalized,
                label = "Photo",
                sourceUri = uri.toString(),
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                id = nextId.getAndIncrement(),
                seed = contentSeed(normalized),
            )
        } catch (t: Throwable) {
            normalized.recycle()
            throw t
        }
    }

    /**
     * Decode [photo] at (about) [width] x [height], for saving.
     *
     * The editor bitmap is capped at [MAX_SOURCE_DIMENSION]; export re-decodes the original so
     * applying an effect does not throw away most of the photo's resolution. An unavailable
     * original fails explicitly; allocation failures reach the export downsize/retry path.
     */
    suspend fun decodeForExport(
        context: Context,
        photo: LoadedPhoto,
        width: Int,
        height: Int,
    ): Bitmap = decodeOwned(Bitmap::recycle) {
        require(width > 0 && height > 0)
        val decoded = try {
            val uri = photo.sourceUri?.let(Uri::parse)
            if (uri != null) decodeUriForExport(context, uri, width, height)
            else decodeAssetForExport(context, width, height)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("The original photo is unavailable. Select it again before saving.", failure)
        }
        toArgb8888(decoded)
    }

    /** Reclaims a decoded result if cancellation wins while switching back from the IO thread. */
    private suspend fun <T : Any> decodeOwned(recycle: (T) -> Unit, decode: () -> T): T {
        var decoded: T? = null
        return try {
            withContext(Dispatchers.IO) { decode().also { decoded = it } }
        } catch (t: Throwable) {
            decoded?.let(recycle)
            throw t
        }
    }

    private fun toArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw OutOfMemoryError("Could not allocate export pixels")
        } finally {
            bitmap.recycle()
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
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: error("Could not decode demo photo")
        if (bitmap.width == width && bitmap.height == height) return bitmap
        return try {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeByteArray(bytes: ByteArray, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: error("Could not decode demo photo")
        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            return try {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            } finally {
                bitmap.recycle()
            }
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
        return try {
            val pixels = IntArray(size * size)
            small.getPixels(pixels, 0, size, 0, 0, size, size)
            var hash = 1125899906842597L
            for (p in pixels) hash = 31 * hash + p
            hash and 0x7FFFFFFF
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }
}

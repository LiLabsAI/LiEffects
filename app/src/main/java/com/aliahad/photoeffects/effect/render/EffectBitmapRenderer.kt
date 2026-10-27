package com.aliahad.photoeffects.effect.render

import android.graphics.Bitmap
import com.aliahad.photoeffects.effect.PhotoEffect
import kotlin.math.roundToInt

/**
 * Android bitmap glue around the deterministic [com.aliahad.photoeffects.effect.cpu.CpuEffect]:
 * used for effect-card thumbnails, the CPU preview fallback and MediaStore export.
 */
object EffectBitmapRenderer {

    /** Largest edge used for exported images (balance of quality vs. CPU render time). */
    const val EXPORT_MAX_DIMENSION = 1440

    /** Thumbnails are rendered at this size, then shown cropped in the card. */
    const val THUMBNAIL_MAX_DIMENSION = 420

    /**
     * Render [effect] onto a downscaled copy of [source].
     *
     * @param intensityPercent 0..100 slider value
     * @param seed deterministic per-photo seed (same value => identical result)
     */
    fun render(
        source: Bitmap,
        effect: PhotoEffect,
        intensityPercent: Int,
        seed: Long,
        maxDimension: Int,
    ): Bitmap {
        val scaled = downscale(source, maxDimension)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = effect.cpu.apply(pixels, width, height, intensityPercent / 100f, seed)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()
        return result
    }

    /** Small deterministic render used for the Effects panel cards. */
    fun renderThumbnail(
        source: Bitmap,
        effect: PhotoEffect,
        intensityPercent: Int = effect.defaultIntensity,
        seed: Long,
        maxDimension: Int = THUMBNAIL_MAX_DIMENSION,
    ): Bitmap = render(source, effect, intensityPercent, seed, maxDimension)

    fun downscale(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}

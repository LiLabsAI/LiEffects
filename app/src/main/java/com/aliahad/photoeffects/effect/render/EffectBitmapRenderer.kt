package com.aliahad.photoeffects.effect.render

import android.graphics.Bitmap
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.cpu.RenderCancellation
import kotlin.math.roundToInt

/**
 * Android bitmap glue around the deterministic [com.aliahad.photoeffects.effect.cpu.CpuEffect]:
 * used for effect-card thumbnails, the CPU preview fallback and MediaStore export.
 */
object EffectBitmapRenderer {

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
        cancel: (() -> Boolean)? = null,
    ): Bitmap {
        val scaled = downscale(source, maxDimension)
        val previous = RenderCancellation.current()
        try {
            val width = scaled.width
            val height = scaled.height
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            RenderCancellation.set(cancel)
            val out = effect.cpu.apply(pixels, width, height, intensityPercent / 100f, seed)
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                result.setPixels(out, 0, width, 0, 0, width, height)
                return result
            } catch (t: Throwable) {
                result.recycle()
                throw t
            }
        } finally {
            RenderCancellation.set(previous)
            if (scaled !== source) scaled.recycle()
        }
    }

    /**
     * Render [effect] at the source resolution without downscaling; used for full-quality
     * export, where the caller already sized the source to fit the memory budget.
     */
    fun renderFull(
        source: Bitmap,
        effect: PhotoEffect,
        intensityPercent: Int,
        seed: Long,
        cancel: (() -> Boolean)? = null,
    ): Bitmap = render(
        source,
        effect,
        intensityPercent,
        seed,
        maxOf(source.width, source.height),
        cancel,
    )

    /** Small deterministic render used for the Effects panel cards. */
    fun renderThumbnail(
        source: Bitmap,
        effect: PhotoEffect,
        intensityPercent: Int = effect.defaultIntensity,
        seed: Long,
        maxDimension: Int = THUMBNAIL_MAX_DIMENSION,
        cancel: (() -> Boolean)? = null,
    ): Bitmap = render(source, effect, intensityPercent, seed, maxDimension, cancel)

    fun downscale(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}

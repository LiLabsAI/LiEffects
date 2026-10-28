package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.effect.PhotoEffect
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Picks the largest safe export resolution for the deterministic CPU renderers.
 *
 * Each effect reports its peak Java-heap cost per pixel ([PhotoEffect.exportBytesPerPixel]);
 * the CPU mirror of HALATION/SENSUAL needs several full-frame float planes, so rendering a
 * 12 MP photo directly would exhaust the heap. The budget keeps the render within
 * [HEAP_FRACTION] of the process heap and lets [PhotoSaver] fall back further on OOM.
 */
object ExportBudget {

    /** Share of the process heap the render may use; the rest is the app's working set. */
    private const val HEAP_FRACTION = 0.40f

    /** Never export below the previous 1440 px long edge (≈1.55 MP on 4:3). */
    const val MIN_PIXELS = 1_500_000

    /** Upper bound for very large sources (48 MP+ camera modes). */
    const val MAX_PIXELS = 24_000_000

    /** Decode + JPEG encode only (no effect selected): two ARGB bitmaps, no pixel arrays. */
    private const val NO_EFFECT_BYTES_PER_PIXEL = 8

    fun bytesPerPixel(effect: PhotoEffect?): Int =
        effect?.exportBytesPerPixel ?: NO_EFFECT_BYTES_PER_PIXEL

    fun maxPixels(
        effect: PhotoEffect?,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    ): Int {
        val budgetBytes = (maxMemoryBytes * HEAP_FRACTION).toLong()
        val pixels = (budgetBytes / bytesPerPixel(effect)).coerceAtMost(Int.MAX_VALUE.toLong())
        return pixels.toInt().coerceIn(MIN_PIXELS, MAX_PIXELS)
    }

    /** Fit [width] x [height] into [maxPixels], preserving aspect ratio. */
    fun targetSize(width: Int, height: Int, maxPixels: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val pixels = width.toLong() * height
        if (pixels <= maxPixels) return width to height
        val scale = sqrt(maxPixels.toDouble() / pixels)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** Longest-edge equivalent of [maxPixels] for a [width] x [height] source. */
    fun maxDimension(width: Int, height: Int, maxPixels: Int): Int {
        val (w, h) = targetSize(width, height, maxPixels)
        return max(w, h)
    }
}

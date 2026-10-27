package com.aliahad.photoeffects.effect.cpu

/**
 * Bundled PREQUEL `adj-Blur` mask (recovered from the app's own static evidence, see
 * `docs/prequel-research/static-packs/evidence/adj-Blur/blurmask.jpg`).
 *
 * The mask is a 1920x1920 radial gradient: 0 at the centre (sharp), ~0.36 at edge midpoints and
 * ~0.82 in the corners. PREQUEL's "Blur" tool is a spatially-varying blur; the fitted
 * approximation used by this app is `B = sharp*(1-mask) + blurred*mask`, so the edges get the
 * full blur and the centre stays (mostly) sharp.
 *
 * The store is populated at app start from `assets/blurmask.jpg` (see `MainActivity`), so the
 * CPU film pipelines stay pure Kotlin/JVM-testable. JVM tests inject the same mask by decoding
 * the asset from disk. When no bitmap is available (e.g. a plain unit test) an analytic radial
 * fallback with the same profile is used.
 */
class BlurMaskData(val values: FloatArray, val width: Int, val height: Int) {

    init {
        require(values.size == width * height) { "mask buffer does not match $width x $height" }
        require(width > 0 && height > 0) { "mask dimensions must be positive" }
    }

    /** Bilinear sample of the mask stretched over the whole image (keepAspect = 0). */
    fun sample(u: Float, v: Float): Float {
        val x = (u.coerceIn(0f, 1f) * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v.coerceIn(0f, 1f) * (height - 1)).coerceIn(0f, (height - 1).toFloat())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0
        val a = values[y0 * width + x0]
        val b = values[y0 * width + x1]
        val c = values[y1 * width + x0]
        val d = values[y1 * width + x1]
        return (a * (1f - fx) + b * fx) * (1f - fy) + (c * (1f - fx) + d * fx) * fy
    }

    companion object {
        /**
         * Analytic fallback matching the measured radial profile (used only when the bundled
         * asset has not been loaded, e.g. JVM-only tests).
         */
        fun analytic(mask: Int = 1): BlurMaskData {
            val w = 64
            val h = 64
            val values = FloatArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val u = (x + 0.5f) / w
                    val v = (y + 0.5f) / h
                    val dx = (u - 0.5f) * 2f
                    val dy = (v - 0.5f) * 2f
                    val r = kotlin.math.sqrt(dx * dx + dy * dy)
                    values[y * w + x] = (0.46f * r * r - 0.10f).coerceIn(0f, 1f)
                }
            }
            return BlurMaskData(values, w, h)
        }
    }
}

/** Process-wide store for the bundled blur mask (populated by `MainActivity`). */
object BlurMaskStore {
    @Volatile
    var current: BlurMaskData? = null

    /** Mask to use for rendering; falls back to the analytic radial approximation. */
    fun requireMask(): BlurMaskData = current ?: BlurMaskData.analytic()
}

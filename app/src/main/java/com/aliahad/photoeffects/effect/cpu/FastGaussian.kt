package com.aliahad.photoeffects.effect.cpu

/**
 * Fast approximate gaussian blur for the CPU film pipelines.
 *
 * Implemented as three successive box-blur passes (`BoxBlur.blurFloatFast`, O(n) each), which
 * converge to a gaussian; sigma (in pixels) maps to a box radius of roughly sigma - 0.5.
 * Used by the YESTERDAY pre-blur and the SENSUAL soft-focus + masked variable blur.
 *
 * Pure Kotlin, deterministic, no Android dependencies.
 */
internal object FastGaussian {

    /** Blur one float plane with an approximate gaussian of [sigma] pixels. */
    fun blurChannel(src: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
        if (sigma <= 0.05f || width <= 0 || height <= 0) return src.copyOf()
        val radius = kotlin.math.max(1, (sigma - 0.5f + 0.5f).toInt())
        // The first pass reads the caller's plane; blurFloatFast never mutates its input.
        var acc = src
        repeat(3) {
            acc = BoxBlur.blurFloatFast(acc, width, height, radius)
        }
        return acc
    }

    /** Blur packed RGB planes (each `width * height`) with the same gaussian. */
    fun blurPlanes(
        r: FloatArray,
        g: FloatArray,
        b: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
    ): Triple<FloatArray, FloatArray, FloatArray> = Triple(
        blurChannel(r, width, height, sigma),
        blurChannel(g, width, height, sigma),
        blurChannel(b, width, height, sigma),
    )
}

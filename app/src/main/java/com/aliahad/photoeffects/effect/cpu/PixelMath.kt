package com.aliahad.photoeffects.effect.cpu

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin mirror of the AGSL helpers in `DustyAgsl.kt` plus small colour utilities.
 *
 * Noise uses an **integer lattice hash** (32-bit wrap-around arithmetic, identical constants in
 * AGSL `uint` and Kotlin `Int`) instead of `fract(large_float)` hashes: float hashes lose
 * fractional precision in device-pixel coordinate ranges and produce visible lattice/moire
 * banding. Value noise / fbm are built on top of that hash and are band-limited in the shader.
 *
 * Determinism: every function is a pure function of its arguments; same input => same output.
 */
internal object PixelMath {

    fun fract(x: Float): Float = x - floor(x)

    /**
     * Lattice hash -> float in 0..1 (mirrors the AGSL `hashF`).
     *
     * AGSL RuntimeShader rejects `uint` and shift operators, so both sides use a float sin-hash.
     * Lattice coordinates are scaled to a moderate magnitude so float32 sin() precision is kept
     * (hashing raw pixel coordinates with fract(large*const) loses fractional bits and produces
     * visible lattice/moire banding).
     */
    fun hashF(ix: Int, iy: Int, seed: Int): Float {
        val cx = ix * 0.1f + seed * 0.137f
        val cy = iy * 0.1f + seed * 0.291f
        val v = sin(cx * 12.9898f + cy * 78.233f) * 43758.5453f
        return fract(v)
    }

    /** Bilinear value noise on an integer lattice (mirrors AGSL vnoise). */
    fun vnoise(px: Float, py: Float, seed: Int): Float {
        val ix = floor(px).toInt()
        val iy = floor(py).toInt()
        val fx = px - ix
        val fy = py - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hashF(ix, iy, seed)
        val b = hashF(ix + 1, iy, seed)
        val c = hashF(ix, iy + 1, seed)
        val d = hashF(ix + 1, iy + 1, seed)
        return mix(mix(a, b, ux), mix(c, d, ux), uy)
    }

    /** Band-limited 2-octave fbm (mirrors AGSL fbm2). */
    fun fbm2(px: Float, py: Float, seed: Int): Float =
        (vnoise(px, py, seed) * 2f + vnoise(px * 2f, py * 2f, seed + 17) * 1f) / 3f

    /** 4-octave fbm (kept for tests / low-frequency texture stages). */
    fun fbm4(px: Float, py: Float, seed: Int): Float {
        var x = px
        var y = py
        var v = 0.5f * vnoise(x, y, seed)
        x *= 2f; y *= 2f
        v += 0.25f * vnoise(x, y, seed + 17)
        x *= 2f; y *= 2f
        v += 0.125f * vnoise(x, y, seed + 34)
        x *= 2f; y *= 2f
        v += 0.0625f * vnoise(x, y, seed + 51)
        return v / 0.9375f
    }

    fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun softLight(a: Float, b: Float): Float =
        if (b <= 0.5f) {
            a - (1f - 2f * b) * a * (1f - a)
        } else {
            a + (2f * b - 1f) * (sqrt(a.coerceAtLeast(0f)) - a)
        }

    /** PREQUEL film-grain soft-light kernel (D(x) polynomial for dark base values). */
    fun softLightFilm(base: Float, b: Float): Float {
        val d = if (base <= 0.25f) ((16f * base - 12f) * base + 4f) * base else sqrt(base.coerceAtLeast(0f))
        return if (b <= 0.5f) {
            base - (1f - 2f * b) * base * (1f - base)
        } else {
            base + (2f * b - 1f) * (d - base)
        }
    }

    fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    fun red(argb: Int): Float = ((argb shr 16) and 0xFF) / 255f
    fun green(argb: Int): Float = ((argb shr 8) and 0xFF) / 255f
    fun blue(argb: Int): Float = (argb and 0xFF) / 255f

    /** Pack float RGB (0..1) + integer alpha into ARGB. */
    fun pack(r: Float, g: Float, b: Float, alpha: Int): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f).roundToInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).roundToInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).roundToInt()
        return (alpha shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * Bilinear ARGB sample at pixel-space coordinates (pixel centres at +0.5).
     * Used for the radial chromatic-aberration stage.
     */
    fun sampleArgb(src: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val cx = (x - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val cy = (y - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = cx.toInt()
        val y0 = cy.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = cx - x0
        val fy = cy - y0
        val p00 = src[y0 * width + x0]
        val p10 = src[y0 * width + x1]
        val p01 = src[y1 * width + x0]
        val p11 = src[y1 * width + x1]
        val r = mix(mix(red(p00), red(p10), fx), mix(red(p01), red(p11), fx), fy)
        val g = mix(mix(green(p00), green(p10), fx), mix(green(p01), green(p11), fx), fy)
        val b = mix(mix(blue(p00), blue(p10), fx), mix(blue(p01), blue(p11), fx), fy)
        return pack(r, g, b, 255)
    }

    fun mixArgb(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        return pack(
            mix(red(a), red(b), t),
            mix(green(a), green(b), t),
            mix(blue(a), blue(b), t),
            (a ushr 24) and 0xFF,
        )
    }

    fun absDiff(a: Int, b: Int): Int {
        var d = 0
        d += abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        d += abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        d += abs((a and 0xFF) - (b and 0xFF))
        return d
    }
}

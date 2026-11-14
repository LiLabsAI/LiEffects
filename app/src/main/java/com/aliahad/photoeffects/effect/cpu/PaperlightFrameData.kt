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

package com.aliahad.photoeffects.effect.cpu

import kotlin.math.roundToInt

/**
 * Bundled paper-frame pixels, sampled with premultiplied bilinear filtering.
 * Transparent texels carry zero RGB, preventing dark fringes when the frame is
 * scaled over the graded photograph. Asset provenance is recorded in README.md.
 */
class PaperlightFrameData(val pixels: IntArray, val width: Int, val height: Int) {

    init {
        require(width > 0 && height > 0) { "frame dimensions must be positive" }
        require(pixels.size == width * height) { "frame buffer does not match $width x $height" }
    }

    /**
     * Bilinear sample at normalized coordinates.
     *
     * @return premultiplied ARGB (alpha 0 => fully transparent, 0)
     */
    fun samplePremultiplied(u: Float, v: Float): Int {
        val x = (u.coerceIn(0f, 1f) * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v.coerceIn(0f, 1f) * (height - 1)).coerceIn(0f, (height - 1).toFloat())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0

        val p00 = pixels[y0 * width + x0]
        val p10 = pixels[y0 * width + x1]
        val p01 = pixels[y1 * width + x0]
        val p11 = pixels[y1 * width + x1]
        if (p00 == 0 && p10 == 0 && p01 == 0 && p11 == 0) return 0

        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy

        val a = a(p00) * w00 + a(p10) * w10 + a(p01) * w01 + a(p11) * w11
        if (a <= 0f) return 0
        val r = cr(p00) * w00 + cr(p10) * w10 + cr(p01) * w01 + cr(p11) * w11
        val g = cg(p00) * w00 + cg(p10) * w10 + cg(p01) * w01 + cg(p11) * w11
        val b = cb(p00) * w00 + cb(p10) * w10 + cb(p01) * w01 + cb(p11) * w11
        val ai = a.roundToInt().coerceIn(0, 255)
        val ri = r.roundToInt().coerceIn(0, 255)
        val gi = g.roundToInt().coerceIn(0, 255)
        val bi = b.roundToInt().coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun a(p: Int): Float = ((p ushr 24) and 0xFF).toFloat()

    /** Premultiplied red of one texel (0 when transparent). */
    private fun cr(p: Int): Float = ((p ushr 16) and 0xFF) * (a(p) / 255f)

    private fun cg(p: Int): Float = ((p ushr 8) and 0xFF) * (a(p) / 255f)

    private fun cb(p: Int): Float = (p and 0xFF) * (a(p) / 255f)
}

/**
 * Process-wide store for the five bundled PAPERLIGHT frame overlays, populated by `MainActivity` at
 * startup so the CPU pipelines (thumbnails / export / preview fallback) stay pure Kotlin.
 * JVM tests inject the same PNGs decoded from `src/main/assets`.
 */
object PaperlightFrameStore {
    private val frames = java.util.concurrent.ConcurrentHashMap<String, PaperlightFrameData>()

    fun put(asset: String, data: PaperlightFrameData) {
        frames[asset] = data
    }

    fun get(asset: String): PaperlightFrameData? = frames[asset]

    fun clear() = frames.clear()
}

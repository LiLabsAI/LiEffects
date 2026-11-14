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

/**
 * One calibrated Prism 33³ colour LUT. Asset provenance is recorded in README.md.
 *
 * All six variants share `assets/prism_luts.png` (594x396): six 198x198 blocks in a 3x2 grid,
 * one per variant. Each block is a 6x6 grid of 33x33 tiles indexed by blue; inside a tile
 * x = red, y = green. Node (r, g, b) therefore lives at
 * `(blockX + (b % 6) * 33 + r, blockY + (b / 6) * 33 + g)`.
 *
 * Values are stored as float RGB in 0..1 for trilinear sampling by the CPU mirror; the GPU
 * preview samples the same atlas directly (see `PRISM_AGSL`).
 */
class PrismLutData private constructor(val values: FloatArray) {

    init {
        require(values.size == SIZE * SIZE * SIZE * 3) { "LUT buffer must be 33^3 x 3" }
    }

    /**
     * Trilinear sample at [r], [g], [b] in 0..1.
     *
     * @param out receives the 3 channel values in 0..1 (reused to avoid per-pixel allocations)
     */
    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val fr = r.coerceIn(0f, 1f) * (SIZE - 1)
        val fg = g.coerceIn(0f, 1f) * (SIZE - 1)
        val fb = b.coerceIn(0f, 1f) * (SIZE - 1)
        val r0 = minOf(fr.toInt(), SIZE - 2)
        val g0 = minOf(fg.toInt(), SIZE - 2)
        val b0 = minOf(fb.toInt(), SIZE - 2)
        val tr = fr - r0
        val tg = fg - g0
        val tb = fb - b0

        val i000 = (((b0 * SIZE + g0) * SIZE) + r0) * 3
        val i100 = i000 + 3
        val i010 = i000 + SIZE * 3
        val i110 = i010 + 3
        val i001 = i000 + SIZE * SIZE * 3
        val i101 = i001 + 3
        val i011 = i001 + SIZE * 3
        val i111 = i011 + 3

        for (c in 0 until 3) {
            val c00 = PixelMath.mix(values[i000 + c], values[i100 + c], tr)
            val c10 = PixelMath.mix(values[i010 + c], values[i110 + c], tr)
            val c01 = PixelMath.mix(values[i001 + c], values[i101 + c], tr)
            val c11 = PixelMath.mix(values[i011 + c], values[i111 + c], tr)
            out[c] = PixelMath.mix(
                PixelMath.mix(c00, c10, tg),
                PixelMath.mix(c01, c11, tg),
                tb,
            )
        }
    }

    companion object {
        const val SIZE = 33
        const val TILES_PER_ROW = 6
        const val TILE = 33
        const val BLOCK = TILES_PER_ROW * TILE
        const val ATLAS_COLUMNS = 3
        const val ATLAS_ROWS = 2
        const val ATLAS_WIDTH = BLOCK * ATLAS_COLUMNS
        const val ATLAS_HEIGHT = BLOCK * ATLAS_ROWS

        /** Extracts one variant block from a decoded atlas bitmap (ARGB pixels). */
        fun fromAtlas(pixels: IntArray, atlasWidth: Int, variantIndex: Int): PrismLutData {
            require(variantIndex in 0 until ATLAS_COLUMNS * ATLAS_ROWS) {
                "variant index $variantIndex outside the LUT atlas"
            }
            require(atlasWidth == ATLAS_WIDTH && pixels.size == ATLAS_WIDTH * ATLAS_HEIGHT) {
                "LUT atlas must be ${ATLAS_WIDTH}x$ATLAS_HEIGHT, got width=$atlasWidth"
            }
            val originX = (variantIndex % ATLAS_COLUMNS) * BLOCK
            val originY = (variantIndex / ATLAS_COLUMNS) * BLOCK
            val values = FloatArray(SIZE * SIZE * SIZE * 3)
            for (b in 0 until SIZE) {
                val tileRow = b / TILES_PER_ROW
                val tileCol = b % TILES_PER_ROW
                for (g in 0 until SIZE) {
                    val rowBase = (originY + tileRow * TILE + g) * atlasWidth + originX +
                        tileCol * TILE
                    for (r in 0 until SIZE) {
                        val p = pixels[rowBase + r]
                        val o = (((b * SIZE + g) * SIZE) + r) * 3
                        values[o] = ((p shr 16) and 0xFF) / 255f
                        values[o + 1] = ((p shr 8) and 0xFF) / 255f
                        values[o + 2] = (p and 0xFF) / 255f
                    }
                }
            }
            return PrismLutData(values)
        }

        /** Identity LUT used when the atlas has not been loaded (renders the photo unchanged). */
        fun identity(): PrismLutData {
            val values = FloatArray(SIZE * SIZE * SIZE * 3)
            for (b in 0 until SIZE) {
                for (g in 0 until SIZE) {
                    for (r in 0 until SIZE) {
                        val o = (((b * SIZE + g) * SIZE) + r) * 3
                        values[o] = r / (SIZE - 1f)
                        values[o + 1] = g / (SIZE - 1f)
                        values[o + 2] = b / (SIZE - 1f)
                    }
                }
            }
            return PrismLutData(values)
        }
    }
}

/**
 * Process-wide store for the six PRISM LUTs, populated by `MainActivity` at startup so the CPU
 * pipelines (thumbnails / export / preview fallback) stay pure Kotlin. JVM tests inject the same
 * atlas decoded from `src/main/assets/prism_luts.png`.
 */
object PrismLutStore {
    private val luts = java.util.concurrent.ConcurrentHashMap<String, PrismLutData>()

    fun put(effectId: String, data: PrismLutData) {
        luts[effectId] = data
    }

    fun get(effectId: String): PrismLutData? = luts[effectId]

    fun clear() = luts.clear()
}

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

package com.aliahad.photoeffects.effect

import kotlin.math.min

/**
 * Procedural dot-matrix date stamp for Daymark.
 *
 * Geometry is calibrated in normalized source coordinates
 * :
 *
 *  - text: current local date formatted `DD MM 'YY` (e.g. `27 10 '26`)
 *  - 5x7 dot-matrix glyphs (standard font, apostrophe = 2-dot vertical mark in a 2-wide cell),
 *    1-dot gap between glyphs, 2-dot-wide space
 *  - dot pitch 4.32 px per 1080-scale minimum dimension (approx. 6.4 px on the 1600-wide export)
 *  - dot radius 0.35 x pitch (thin dots), amber `#F8CF96`, soft 1px edge
 *  - in-photo orientation = readable text rotated 90 degrees counter-clockwise:
 *    text runs bottom->top, glyph tops face left
 *  - bbox: left edge at 6.25 % of W, bottom edge at 82.78 % of H (44 x 280 px on 1600x2398)
 *
 * One shared layout generator feeds BOTH paths so preview and export agree:
 *  - `CpuDaymarkRenderer` rasterises the dots into the pixel buffer (thumbnails/export),
 *  - `PhotoPreviewCard` draws the same dots as a Compose canvas overlay (GPU preview).
 *
 * Pure Kotlin: deterministic and JVM-testable.
 */
object DotMatrixDate {

    /** Calendar date rendered by the stamp (year is displayed as its last two digits). */
    data class DateParts(val day: Int, val month: Int, val year: Int) {
        /** `DD MM 'YY`, e.g. `27 10 '26`. */
        val text: String get() = "%02d %02d '%02d".format(day, month, year % 100)
    }

    /** One dot of the matrix, in pixel coordinates of the rendered photo/preview. */
    data class Dot(val cx: Float, val cy: Float, val r: Float)

    /** Stamp metadata attached to a [PhotoEffect]: colour + current-date provider. */
    data class StampSpec(
        val colorRgb: Long = STAMP_COLOR_RGB,
        val dateProvider: () -> DateParts = systemDate,
    )

    /** Real-clock provider used by the app (tests inject fixed dates). */
    val systemDate: () -> DateParts = {
        val d = java.time.LocalDate.now()
        DateParts(d.dayOfMonth, d.monthValue, d.year)
    }

    // ---- fitted geometry (spec §1.6) ----
    /** Dot pitch in px per 1080-scale minimum dimension (6.4 px on the 1600-wide export). */
    const val PITCH_1080 = 4.32f
    /** Dot radius as a fraction of the pitch. */
    const val RADIUS_RATIO = 0.35f
    /** Stamp bbox left edge as a fraction of W. */
    const val LEFT_FRAC = 0.0625f
    /** Stamp bbox bottom edge as a fraction of H. */
    const val BOTTOM_FRAC = 0.8278f
    /** Amber dot colour (`#F8CF96`). */
    const val STAMP_COLOR_RGB = 0xF8CF96L

    private const val GLYPH_COLS = 5
    private const val SPACE_COLS = 2
    private const val APOSTROPHE_COLS = 2
    private const val GLYPH_ROWS = 7
    private const val GAP_COLS = 1

    // Standard 5x7 LED font, rows top->bottom, bits left->right ('1' = dot on).
    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "10000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ' ' to listOf("00", "00", "00", "00", "00", "00", "00"),
        '\'' to listOf("01", "01", "00", "00", "00", "00", "00"),
    )

    private fun glyphWidth(ch: Char): Int = when (ch) {
        ' ' -> SPACE_COLS
        '\'' -> APOSTROPHE_COLS
        else -> GLYPH_COLS
    }

    /**
     * Dot centres for the stamp on a [width] x [height] photo/preview.
     *
     * Layout in native (in-photo) coordinates: the readable glyph matrix is rotated 90 degrees
     * counter-clockwise, so glyph row 0 (readable top) is the leftmost native column, and text
     * column 0 (first character) is the bottom-most native row. Deterministic: identical
     * inputs -> identical dots.
     */
    fun layout(parts: DateParts, width: Int, height: Int): List<Dot> {
        if (width <= 0 || height <= 0) return emptyList()
        val text = parts.text
        val sc = min(width, height) / 1080f
        val pitch = PITCH_1080 * sc
        val radius = RADIUS_RATIO * pitch
        // Calibrated bounding box: left edge + bottom edge relative anchors.
        val left = LEFT_FRAC * width
        val bottom = BOTTOM_FRAC * height

        val dots = ArrayList<Dot>(text.length * GLYPH_COLS * GLYPH_ROWS)
        var col = 0
        for (i in text.indices) {
            val ch = text[i]
            val glyph = GLYPHS[ch] ?: GLYPHS.getValue(' ')
            val glyphWidth = glyphWidth(ch)
            for (row in 0 until GLYPH_ROWS) {
                val line = glyph[row]
                for (b in 0 until glyphWidth) {
                    if (line.getOrElse(b) { '0' } != '1') continue
                    val u = col + b
                    // Native coords: x = bboxLeft + r + glyphRow*pitch (glyph top faces left)
                    //               y = bboxBottom - r - textCol*pitch (text runs bottom->top)
                    dots.add(
                        Dot(
                            cx = left + radius + row * pitch,
                            cy = bottom - radius - u * pitch,
                            r = radius,
                        ),
                    )
                }
            }
            col += glyphWidth + GAP_COLS
        }
        return dots
    }

    /** Number of dot columns of the rendered string (widths + 1-dot gaps). */
    fun columnCount(parts: DateParts): Int {
        var totalCols = 0
        val text = parts.text
        for (i in text.indices) {
            totalCols += glyphWidth(text[i])
            if (i != text.lastIndex) totalCols += GAP_COLS
        }
        return totalCols
    }

    /**
     * Expected bbox (left, top, right, bottom) of the stamp in pixels, used by the unit tests
     * to verify placement against the reference export.
     */
    fun bbox(parts: DateParts, width: Int, height: Int): FloatArray {
        val sc = min(width, height) / 1080f
        val pitch = PITCH_1080 * sc
        val radius = RADIUS_RATIO * pitch
        val totalCols = columnCount(parts)
        val left = LEFT_FRAC * width
        val bottom = BOTTOM_FRAC * height
        // width = 6*pitch + 2r (7 dot rows), height = (totalCols-1)*pitch + 2r
        return floatArrayOf(
            left,
            bottom - (totalCols - 1) * pitch - 2f * radius,
            left + 6f * pitch + 2f * radius,
            bottom,
        )
    }
}

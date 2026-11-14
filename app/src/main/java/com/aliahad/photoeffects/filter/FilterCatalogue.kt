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

package com.aliahad.photoeffects.filter

import java.io.Reader
import java.util.Properties

/** Versioned recipe data, independent of Android so the same loader runs in JVM tests. */
internal object FilterCatalogue {
    const val RESOURCE = "/filters/catalogue.properties"

    fun bundled(): List<FilterParams> = checkNotNull(javaClass.getResourceAsStream(RESOURCE)) {
        "Missing bundled filter catalogue: $RESOURCE"
    }.bufferedReader(Charsets.UTF_8).use(::read)

    fun read(reader: Reader): List<FilterParams> {
        val data = object : Properties() {
            override fun put(key: Any, value: Any): Any? {
                require(!containsKey(key)) { "Duplicate filter catalogue key: $key" }
                return super.put(key, value)
            }
        }.apply { load(reader) }
        val used = mutableSetOf<String>()
        fun value(key: String): String? {
            used += key
            return data.getProperty(key)?.trim()
        }
        fun required(key: String): String = requireNotNull(value(key)?.takeIf { it.isNotEmpty() }) {
            "Missing filter catalogue field: $key"
        }
        require(required("schemaVersion") == "4") { "Unsupported filter catalogue schema" }
        val ids = required("presets").split(',').map(String::trim)
        require(ids.all { it.matches(Regex("[a-z][a-z0-9_]*")) } && ids.distinct().size == ids.size) {
            "Filter IDs must be unique nonempty identifiers"
        }
        val recipes = ids.map { id ->
            fun field(name: String) = "$id.$name"
            fun number(name: String, default: Float, range: ClosedFloatingPointRange<Float>): Float {
                val raw = value(field(name)) ?: return default
                val n = raw.toFloatOrNull()
                require(n != null && n.isFinite() && n in range) { "Invalid ${field(name)}: $raw" }
                return n
            }
            val variant = required(field("variant")).toIntOrNull()
            require(variant != null && variant > 0) { "Invalid variant for $id" }
            val neutral = FilterParams(id, required(field("family")), variant)
            val black = number("black", neutral.black, 0f..1f)
            val white = number("white", neutral.white, 0f..1f)
            require(black < white) { "Black must be below white for $id" }
            val sharpen = value(field("sharpen"))?.let { raw ->
                requireNotNull(raw.toIntOrNull()?.takeIf { it in 0..100 }) { "Invalid sharpen for $id" }
            } ?: neutral.sharpen
            fun integer(name: String, default: Int, range: IntRange): Int {
                val raw = value(field(name)) ?: return default
                return requireNotNull(raw.toIntOrNull()?.takeIf { it in range }) { "Invalid ${field(name)}" }
            }
            val controls = value(field("controls"))?.split(',')?.map { label ->
                requireNotNull(FilterControl.entries.firstOrNull { it.label == label.trim() }) {
                    "Unknown control for $id: $label"
                }
            } ?: neutral.controls
            require(controls.distinct().size == controls.size && FilterControl.Strength in controls) {
                "Controls must be unique and include Filter for $id"
            }
            neutral.copy(
                exposure = number("exposure", neutral.exposure, 0f..4f),
                contrast = number("contrast", neutral.contrast, 0f..4f),
                saturation = number("saturation", neutral.saturation, 0f..3f),
                black = black, white = white,
                gainR = number("gainR", neutral.gainR, 0f..4f),
                gainG = number("gainG", neutral.gainG, 0f..4f),
                gainB = number("gainB", neutral.gainB, 0f..4f),
                shadowR = number("shadowR", neutral.shadowR, -1f..1f),
                shadowG = number("shadowG", neutral.shadowG, -1f..1f),
                shadowB = number("shadowB", neutral.shadowB, -1f..1f),
                highlightR = number("highlightR", neutral.highlightR, -1f..1f),
                highlightG = number("highlightG", neutral.highlightG, -1f..1f),
                highlightB = number("highlightB", neutral.highlightB, -1f..1f),
                sharpen = sharpen,
                summary = required(field("summary")),
                strength = number("strength", neutral.strength, 0f..1f),
                intensity = integer("intensity", neutral.intensity, 0..100),
                softFocus = number("softFocus", neutral.softFocus, 0f..96f),
                focusRadiusCurve = number("focusRadiusCurve", neutral.focusRadiusCurve, 0f..1f),
                focusSpread = number("focusSpread", neutral.focusSpread, 0f..8f),
                focusSpreadGrowth = number("focusSpreadGrowth", neutral.focusSpreadGrowth, 0f..8f),
                focusDetail = number("focusDetail", neutral.focusDetail, 0f..2f),
                focusDetailRadius = number("focusDetailRadius", neutral.focusDetailRadius, 0f..8f),
                focusLift = number("focusLift", neutral.focusLift, 0f..0.02f),
                grain = integer("grain", neutral.grain, 0..100),
                grainSize = number("grainSize", neutral.grainSize, 0.25f..8f),
                grainSeed = integer("grainSeed", neutral.grainSeed, 0..4090),
                highlights = integer("highlights", neutral.highlights, -100..100),
                shadows = integer("shadows", neutral.shadows, -100..100),
                aberration = integer("aberration", neutral.aberration, 0..100),
                aberrationRadius = number("aberrationRadius", neutral.aberrationRadius, 0f..64f),
                controls = controls,
            )
        }
        require(recipes.map { it.family to it.variant }.distinct().size == recipes.size) {
            "Duplicate filter family/variant"
        }
        val unknown = data.stringPropertyNames() - used
        require(unknown.isEmpty()) { "Unknown filter catalogue fields: $unknown" }
        return recipes
    }
}

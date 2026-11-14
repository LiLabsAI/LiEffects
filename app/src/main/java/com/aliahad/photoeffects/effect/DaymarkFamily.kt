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

/**
 * A single warm film preset with a dot-matrix date stamp.
 * The single-card family has no numbered variant strip.
 */
object DaymarkFamily {

    const val ID = "Daymark"

    val presets: List<DaymarkParams> = listOf(
        DaymarkParams(
            id = "daymark_1",
            displayName = "Daymark",
            description = "Warm faded film with grain, strong vignette and the amber dot-matrix date stamp",
            seed = 301,
            defaultIntensity = 100,
        ),
    )

    /** photo-editor cross-listing (DAYMARK was found under TRENDS on the device). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "daymark_1" to setOf(EffectCategory.Trends, EffectCategory.ForYou),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        DaymarkEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}

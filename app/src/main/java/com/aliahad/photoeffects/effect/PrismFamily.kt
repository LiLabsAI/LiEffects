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
 * Six colour-lookup grades using the shared 33³ LUT atlas in assets/prism_luts.png.
 * Only the first variant adds portrait-ellipse soft focus. Retune these rows together
 * with the atlas data; both renderers consume the same variant indices.
 */
object PrismFamily {

    const val ID = "Prism"

    val presets: List<PrismParams> = listOf(
        PrismParams(
            id = "prism_1",
            displayName = "Prism #1",
            description = "Soft-focus portrait grade with a crisp centre and gently blurred edges",
            lutIndex = 0,
            ellipseBlur = true,
        ),
        PrismParams(
            id = "prism_2",
            displayName = "Prism #2",
            description = "Cool teal grade with deep shadows",
            lutIndex = 1,
        ),
        PrismParams(
            id = "prism_3",
            displayName = "Prism #3",
            description = "Clean neutral grade with a soft cool cast",
            lutIndex = 2,
        ),
        PrismParams(
            id = "prism_4",
            displayName = "Prism #4",
            description = "Warm neutral grade with creamy skin tones",
            lutIndex = 3,
        ),
        PrismParams(
            id = "prism_5",
            displayName = "Prism #5",
            description = "Bright sunlit grade with warm highlights",
            lutIndex = 4,
        ),
        PrismParams(
            id = "prism_6",
            displayName = "Prism #6",
            description = "Airy cyan-green grade with lifted midtones",
            lutIndex = 5,
        ),
    )

    /** Cross-list the colour-lookup presets on the For You shelf. */
    private val extraCategories: Map<String, Set<EffectCategory>> = presets.associate { params ->
        params.id to setOf(EffectCategory.ForYou)
    }

    val effects: List<PhotoEffect> = presets.map { params ->
        PrismEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}

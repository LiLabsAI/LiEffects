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

object NocturneFamily {
    const val ID = "Nocturne"

    val presets: List<NocturneParams> = listOf(
        NocturneParams(
            id = "nocturne_gilded",
            displayName = "Gilded",
            description = "Champagne key light over deep velvet shadows, with a fine satin texture",
            summary = "champagne spotlight · velvet shadows",
            gilded = true,
            seed = 701,
            exposure = 0.91f,
            saturation = 0.90f,
            edgeShade = 0.38f,
            light = 0.19f,
            grain = 0.012f,
            defaultIntensity = 80,
        ),
        NocturneParams(
            id = "nocturne_afterhours",
            displayName = "Afterhours",
            description = "Cool penthouse window light, platinum reflections and cinematic edge shading",
            summary = "platinum rim light · midnight shade",
            gilded = false,
            seed = 702,
            exposure = 0.88f,
            saturation = 0.76f,
            edgeShade = 0.32f,
            light = 0.16f,
            grain = 0.008f,
            defaultIntensity = 75,
        ),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        NocturneEffectFactory.create(params)
    }
}

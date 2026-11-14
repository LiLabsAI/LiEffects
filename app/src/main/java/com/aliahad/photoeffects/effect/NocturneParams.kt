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

data class NocturneParams(
    val id: String,
    val displayName: String,
    val description: String,
    val summary: String,
    val gilded: Boolean,
    val seed: Int,
    val exposure: Float,
    val saturation: Float,
    val edgeShade: Float,
    val light: Float,
    val grain: Float,
    val defaultIntensity: Int,
) {
    fun toUniforms(): Map<String, Float> = mapOf(
        "uStyle" to if (gilded) 0f else 1f,
        "uSeed" to seed.toFloat(),
        "uExposure" to exposure,
        "uSaturation" to saturation,
        "uEdgeShade" to edgeShade,
        "uLight" to light,
        "uGrain" to grain,
    )
}

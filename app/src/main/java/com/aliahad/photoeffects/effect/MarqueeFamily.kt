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
 * Two dark-blue Hollywood grades. Both keep warm skin alive while everything else goes cool
 * and low-key; they differ in curve depth, tint balance and how the frame falls away.
 */
object MarqueeFamily {
    const val ID = "Marquee"

    val presets: List<MarqueeParams> = listOf(
        MarqueeParams(
            id = "marquee_premiere",
            displayName = "Premiere",
            description = "Blockbuster night grade: navy shadows, rolled-off highlights and warm skin",
            summary = "navy shadows · amber skin · film shoulder",
            premiere = true,
            seed = 801,
            exposure = 0.82f,
            coolR = 0.93f,
            coolG = 0.985f,
            coolB = 1.10f,
            curve = 0.50f,
            shoulder = 0.85f,
            blackR = 0.010f,
            blackG = 0.016f,
            blackB = 0.045f,
            white = 0.965f,
            shadowR = -0.040f,
            shadowG = -0.008f,
            shadowB = 0.075f,
            highlightR = 0.045f,
            highlightG = 0.020f,
            highlightB = -0.020f,
            saturation = 0.78f,
            skinSaturation = 1.02f,
            skinProtect = 0.85f,
            falloff = 0.50f,
            grain = 0.014f,
            defaultIntensity = 85,
        ),
        MarqueeParams(
            id = "marquee_midnight",
            displayName = "Midnight",
            description = "Neo-noir cut: colder, deeper blue floor and a narrow band of surviving light",
            summary = "deep blue floor · steel light · noir falloff",
            premiere = false,
            seed = 802,
            exposure = 0.78f,
            coolR = 0.90f,
            coolG = 0.97f,
            coolB = 1.14f,
            curve = 0.58f,
            shoulder = 1.05f,
            blackR = 0.008f,
            blackG = 0.013f,
            blackB = 0.050f,
            white = 0.945f,
            shadowR = -0.048f,
            shadowG = -0.014f,
            shadowB = 0.090f,
            highlightR = 0.022f,
            highlightG = 0.014f,
            highlightB = -0.006f,
            saturation = 0.64f,
            skinSaturation = 0.86f,
            skinProtect = 0.70f,
            falloff = 0.60f,
            grain = 0.018f,
            defaultIntensity = 80,
        ),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        MarqueeEffectFactory.create(params)
    }
}
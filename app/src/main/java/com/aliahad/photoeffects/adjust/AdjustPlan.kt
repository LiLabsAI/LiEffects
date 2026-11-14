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

package com.aliahad.photoeffects.adjust

/** Which optional Adjust GPU/CPU passes exist for a parameter set. */
data class AdjustPlan(
    val detail: Boolean,
    val blur: Boolean,
    val color: Boolean = false,
) {
    val passCount: Int get() = 1 + (if (color) 1 else 0) + (if (detail) 1 else 0) + (if (blur) 1 else 0)
}

fun planFor(params: AdjustParams): AdjustPlan =
    AdjustPlan(
        detail = params.layers.detail,
        blur = params.layers.blur,
        color = AdjustControl.entries.any { it.pass == AdjustPass.Color && params.value(it) != it.neutral },
    )

/**
 * Rough peak heap cost of one CPU Adjust render. The color/finish path keeps source, working and
 * output ARGB buffers; detail adds support planes and blur adds an ARGB blur chain.
 */
fun adjustBytesPerPixel(plan: AdjustPlan): Int =
    24 + (if (plan.detail) 12 else 0) + (if (plan.blur) 32 else 0)

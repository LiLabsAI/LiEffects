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
 * Shared colour-science primitives for the "pack" film families (DAYMARK / VELOUR).
 *
 * Both rewritten families use the same fitted model shape:
 *   1. per-channel cubic tone curve `out = ((a3*x + a2)*x + a1)*x + a0`
 *   2. saturation mix `t = mix(gray, c, 1 - satMix)`
 *   3. per-channel gain/offset `y = gain*c + offset`
 *
 * Keeping these as small value types makes the fitted spec tables auditable in one place and
 * lets the CPU mirrors stay byte-deterministic. Pure Kotlin, no Android dependencies.
 */
data class CubicTone(val a3: Float, val a2: Float, val a1: Float, val a0: Float) {
    /** Evaluate the cubic for [x] (usually 0..1, may overshoot slightly). */
    fun eval(x: Float): Float = ((a3 * x + a2) * x + a1) * x + a0
}

data class GainOffset(val gain: Float, val offset: Float) {
    fun eval(x: Float): Float = gain * x + offset
}

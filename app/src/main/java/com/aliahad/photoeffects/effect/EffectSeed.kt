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
 * Shared seed derivation so the GPU preview, CPU thumbnails and CPU export all use exactly the
 * same deterministic noise seed for a given photo + variant.
 *
 * The 16-bit photo seed keeps the value small and exactly representable as an AGSL float
 * uniform (integers above 2^24 lose precision in float32).
 */
object EffectSeed {
    fun of(photoSeed: Long, variantSeed: Int): Int =
        ((photoSeed and 0xFFFF).toInt()) + variantSeed
}

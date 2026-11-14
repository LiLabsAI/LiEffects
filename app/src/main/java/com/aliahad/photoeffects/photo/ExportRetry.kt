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

package com.aliahad.photoeffects.photo

import kotlin.math.min

/** Retries only allocation failures, always including a final attempt at the retry floor. */
internal object ExportRetry {
    private const val MIN_RETRY_PIXELS = 500_000L

    suspend fun <T> render(width: Int, height: Int, attempt: suspend (Int, Int) -> T): T {
        require(width > 0 && height > 0)
        var pixels = width.toLong() * height
        val floor = min(pixels, MIN_RETRY_PIXELS)
        while (true) {
            val (targetWidth, targetHeight) = ExportBudget.targetSize(
                width, height, pixels.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            try {
                return attempt(targetWidth, targetHeight)
            } catch (oom: OutOfMemoryError) {
                if (pixels <= floor) throw oom
                pixels = (pixels / 4).coerceAtLeast(floor)
            }
        }
    }
}

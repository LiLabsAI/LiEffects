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

package com.aliahad.photoeffects.effect.cpu

import java.util.concurrent.CancellationException

/**
 * Cooperative cancellation for blocking CPU renders.
 *
 * [PixelRunner] checks this token once per row, so a cancelled thumbnail pass stops within a
 * tile instead of running to completion and competing with the UI. The token is thread-local
 * on the caller; worker tasks capture the same predicate at fan-out time.
 */
internal object RenderCancellation {
    private val current = ThreadLocal<(() -> Boolean)?>()

    fun current(): (() -> Boolean)? = current.get()

    fun set(predicate: (() -> Boolean)?) {
        if (predicate == null) current.remove() else current.set(predicate)
    }

    fun check() {
        if (current.get()?.invoke() == true) throw CancellationException("CPU render cancelled")
    }
}

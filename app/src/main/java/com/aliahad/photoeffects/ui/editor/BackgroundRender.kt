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

package com.aliahad.photoeffects.ui.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reclaims a completed render if cancellation wins the handoff back to the UI dispatcher. */
internal suspend fun <T : Any> renderOnBackground(dispose: (T) -> Unit, render: () -> T): T {
    var completed: T? = null
    return try {
        withContext(Dispatchers.Default) { render().also { completed = it } }
    } catch (failure: Throwable) {
        completed?.let(dispose)
        throw failure
    }
}

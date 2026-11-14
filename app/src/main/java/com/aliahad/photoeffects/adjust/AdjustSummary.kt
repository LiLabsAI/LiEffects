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

/** Short names for the Adjust panel/composition summary. */
object AdjustSummary {
    fun describe(params: AdjustParams): String {
        val edited = AdjustControl.entries.filter { params.value(it) != it.neutral }
        if (edited.isEmpty()) return "no adjustments"
        val parts = edited.take(3).map { control ->
            val band = AdjustControl.hslBandOf(control)
            val component = AdjustControl.hslComponentOf(control)
            val label = if (band != null && component != null) {
                "${band.label.lowercase()} ${component.label.lowercase()}"
            } else {
                control.label.lowercase()
            }
            "$label ${format(params.value(control), control)}"
        }.toMutableList()
        if (edited.size > 3) parts += "+${edited.size - 3}"
        return parts.joinToString(" · ")
    }

    private fun format(value: Int, control: AdjustControl): String = when {
        value > 0 && control.signed -> "+$value"
        else -> value.toString()
    }
}

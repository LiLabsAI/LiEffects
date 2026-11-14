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

/** Read compatibility for saved sessions written before the catalogue was renamed. */
internal object LegacyCatalogueNames {
    private val effects = mapOf(
        "Dusty" to "Motes", "Halation" to "Lumen", "Yesterday" to "Daymark",
        "Sensual" to "Velour", "Kuji" to "Paperlight", "Focal" to "Prism",
    )
    private val filters = mapOf(
        "G7X" to "Lucent", "Dim" to "Nightfall", "Vanilla" to "Marzipan",
        "Insomnia" to "Driftglass", "Cine Film" to "Lantern", "Chicago" to "Graphite",
        "Teal Tone" to "Estuary", "Burgundy" to "Mulberry",
    )

    fun effectId(id: String): String = renamedId(id, effects)
    fun filterId(id: String): String = renamedId(id, filters)
    fun effectShelf(id: String?): String? = effects[id] ?: id
    fun filterShelf(id: String?): String? = filters[id] ?: id

    private fun renamedId(id: String, families: Map<String, String>): String {
        val prefix = id.substringBeforeLast('_', "")
        val family = families.entries.firstOrNull {
            it.key.lowercase().replace(' ', '_') == prefix
        } ?: return id
        return family.value.lowercase() + "_" + id.substringAfterLast('_')
    }
}

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
 * photo-editor category shelves. Effects can additionally appear on these shelves via
 * [EffectSpec.extraCategories]; the primary shelf of every preset is its family (see
 * [EffectFamily] / [EffectRegistry.shelves]).
 */
enum class EffectCategory(val label: String) {
    ForYou("FOR YOU"),
    Trends("TRENDS"),
    New("NEW"),
    Films("FILMS"),
}

/**
 * Immutable, UI-facing description of one effect slot.
 *
 * Rendering parameters live on the effect's family parameter object (e.g. [MotesParams],
 * [LumenParams]) so that presets of one family share a render pipeline and differ only
 * by data.
 */
data class EffectSpec(
    val id: String,
    val displayName: String,
    /** Family id: "Motes" | "Lumen" (matches [EffectFamily.id]). */
    val familyId: String,
    val description: String,
    /** Default slider position (0..100) when the effect is first selected. */
    val defaultIntensity: Int = 100,
    /** Extra photo-editor category shelves this preset is cross-listed on. */
    val extraCategories: Set<EffectCategory> = emptySet(),
    /** Optional small badge shown on the thumbnail card (e.g. "NEW"). */
    val badge: String? = null,
) {
    val categories: Set<EffectCategory>
        get() = extraCategories
}

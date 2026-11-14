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
 * One titled presets family rendered by a single pipeline.
 *
 * The app is organised as families (e.g. MOTES with 5 presets, LUMEN with 3 presets); each
 * family is one chip in the Effects panel and its presets are the card row. Families are
 * additive: adding a family does not touch the dusty pipeline.
 */
data class EffectFamily(
    /** Stable id; also used for the chip content-description: `category_<id>`. */
    val id: String,
    /** Human title shown on the chip ("MOTES", "LUMEN"). */
    val title: String,
    val effects: List<PhotoEffect>,
)

/**
 * One chip in the Effects panel: either a [EffectFamily] shelf or a photo-editor category shelf
 * (FOR YOU / TRENDS / NEW / FILMS) aggregating cross-listed presets from all families.
 */
data class EffectShelf(
    val id: String,
    val title: String,
    val effects: List<PhotoEffect>,
    val isFamily: Boolean = false,
)

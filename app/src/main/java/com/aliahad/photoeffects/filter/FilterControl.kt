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

package com.aliahad.photoeffects.filter

/** Controls exposed by a recipe, independently of its catalogue family or display name. */
enum class FilterControl(val label: String, val range: IntRange = 0..100) {
    Strength("Filter"), Sharpen("Sharpen"), Intensity("Intensity"), Grain("Grain"),
    Highlights("Highlights", -100..100), Shadows("Shadows", -100..100),
    Aberration("Aberration"),
}

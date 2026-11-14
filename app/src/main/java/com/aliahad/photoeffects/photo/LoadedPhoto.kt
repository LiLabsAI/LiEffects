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

import android.graphics.Bitmap

/**
 * A photo loaded into the editor.
 *
 * [sourceWidth] / [sourceHeight] are the full-resolution dimensions of the original (after EXIF
 * orientation), before the editor's [PhotoLoader.MAX_SOURCE_DIMENSION] downsample; the export
 * planner uses them to offer source-limited resolution tiers.
 *
 * [seed] is derived deterministically from the photo content so the same photo always
 * produces the same dust/grain pattern (and thumbnails match the full preview).
 */
data class LoadedPhoto(
    val bitmap: Bitmap,
    val label: String,
    val sourceUri: String?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val id: Long,
    val seed: Long,
)

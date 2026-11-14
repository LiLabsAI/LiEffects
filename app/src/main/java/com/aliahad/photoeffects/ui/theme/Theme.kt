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

package com.aliahad.photoeffects.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** LiEffects dark editor palette. */
object EditorColors {
    val Background = Color(0xFF0B0B0D)
    val Panel = Color(0xFF111115)
    val PanelElevated = Color(0xFF17171C)
    val Card = Color(0xFF15151A)
    val Accent = Color(0xFFF6A21E)
    val AccentDim = Color(0x66F6A21E)
    val TextPrimary = Color(0xFFF4F4F6)
    val TextSecondary = Color(0xFF9A9AA5)
    val TextMuted = Color(0xFF63636E)
    val Hairline = Color(0xFF262630)
    val Track = Color(0xFF3A3A45)
    /** Bottom sheet surface (export settings), one step above [Background]. */
    val Sheet = Color(0xFF1B1B1F)
    /** Segmented option tiles inside the sheet. */
    val Tile = Color(0xFF26262B)
}

private val EditorTypography = Typography(
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun LiEffectsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EditorColors.Accent,
            onPrimary = Color.Black,
            secondary = EditorColors.Accent,
            background = EditorColors.Background,
            onBackground = EditorColors.TextPrimary,
            surface = EditorColors.Panel,
            onSurface = EditorColors.TextPrimary,
            surfaceVariant = EditorColors.PanelElevated,
            onSurfaceVariant = EditorColors.TextSecondary,
        ),
        typography = EditorTypography,
        content = content,
    )
}

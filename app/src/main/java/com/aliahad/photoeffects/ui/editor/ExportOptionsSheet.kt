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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.photo.ExportMetadataMode
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportPlan
import com.aliahad.photoeffects.photo.ExportPlanner
import com.aliahad.photoeffects.photo.ExportQuality
import com.aliahad.photoeffects.photo.ResolutionOption
import com.aliahad.photoeffects.ui.theme.EditorColors

/**
 * photo-editor export settings sheet: segmented Resolution / Quality / Metadata rows, a
 * full-width Save pill and the estimated file size underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOptionsSheet(
    options: ExportOptions,
    resolutions: List<ResolutionOption>,
    plan: ExportPlan,
    onSelect: (ExportOptions) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EditorColors.Sheet,
        contentColor = EditorColors.TextPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Resolution",
                    color = EditorColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Done",
                    color = EditorColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics { contentDescription = "export_done" },
                )
            }
            Spacer(Modifier.height(12.dp))

            SegmentedTiles(resolutions) { option ->
                OptionTile(
                    label = option.resolution.label,
                    subtitle = if (option.enabled) "${option.width}×${option.height}" else "Not available",
                    selected = option.enabled && option.resolution == options.resolution,
                    enabled = option.enabled,
                    contentDescription = "export_resolution_${option.resolution.name}",
                    onClick = { onSelect(options.copy(resolution = option.resolution)) },
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionTitle("Quality")
            Spacer(Modifier.height(12.dp))
            SegmentedTiles(ExportQuality.entries) { quality ->
                OptionTile(
                    label = quality.label,
                    subtitle = quality.hint,
                    selected = quality == options.quality,
                    enabled = true,
                    contentDescription = "export_quality_${quality.name}",
                    onClick = { onSelect(options.copy(quality = quality)) },
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionTitle("Metadata")
            Spacer(Modifier.height(12.dp))
            SegmentedTiles(ExportMetadataMode.entries) { mode ->
                OptionTile(
                    label = mode.label,
                    subtitle = mode.hint,
                    selected = mode == options.metadata,
                    enabled = true,
                    contentDescription = "export_metadata_${mode.name}",
                    onClick = { onSelect(options.copy(metadata = mode)) },
                )
            }

            Spacer(Modifier.height(26.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(EditorColors.Accent)
                    .clickable(onClick = onSave)
                    .semantics { contentDescription = "export_save" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Save",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = ExportPlanner.formatEstimate(plan.estimatedBytes),
                color = EditorColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = EditorColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** One rounded row of equal-width tiles separated by hairline dividers. */
@Composable
private fun <T> SegmentedTiles(items: List<T>, tile: @Composable RowScope.(T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(EditorColors.Tile),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(EditorColors.Sheet),
                )
            }
            tile(item)
        }
    }
}

@Composable
private fun RowScope.OptionTile(
    label: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 4.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = EditorColors.Accent,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (enabled) EditorColors.Track else EditorColors.TextMuted,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            color = if (enabled) EditorColors.TextPrimary else EditorColors.TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = subtitle,
            color = if (enabled) EditorColors.TextSecondary else EditorColors.TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.adjust.AdjustCategory
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.HslBand
import com.aliahad.photoeffects.adjust.HslComponent
import com.aliahad.photoeffects.ui.theme.EditorColors

/**
 * Adjust panel with progressive navigation. Categories disappear when a category is chosen;
 * control rows disappear when a control is chosen, leaving only the active slider. The header
 * arrow walks back through the stages.
 */
@Composable
fun AdjustPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val stage = state.panelStage
    val control = state.adjustControl
    val categoryEdited = AdjustControl.byCategory(state.adjustCategory)
        .any { state.adjustParams.value(it) != it.neutral }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(EditorColors.Panel)
            .animateContentSize(),
    ) {
        EditorPanelHeader(
            title = when (stage) {
                PanelStage.Categories -> "Adjust"
                PanelStage.Items -> state.adjustCategory.label
                PanelStage.Edit -> control.label
            },
            subtitle = when (stage) {
                PanelStage.Categories -> "${state.adjustEditedCount} edit(s) · ${AdjustControl.entries.size} controls"
                PanelStage.Items -> "Choose a control"
                PanelStage.Edit -> "${state.adjustCategory.label} · ${formatValue(state.adjustValue, control)}"
            },
            onBack = if (stage != PanelStage.Categories) {
                { state.panelBack() }
            } else null,
            actions = {
                if (stage != PanelStage.Categories) {
                    EditorPanelAction(
                        label = "Reset cat.",
                        enabled = categoryEdited,
                        description = "adjust_reset_category",
                        onClick = { state.resetAdjustCategory() },
                    )
                }
                EditorPanelAction(
                    label = "Reset",
                    enabled = state.adjustEditedCount > 0,
                    description = "adjust_reset",
                    onClick = { state.resetAdjust() },
                )
            },
        )

        AnimatedContent(
            targetState = stage,
            transitionSpec = { panelStageTransition() },
            label = "adjust_stage",
        ) { current ->
            when (current) {
                PanelStage.Categories -> ChipRow {
                    for (category in AdjustCategory.entries) {
                        Chip(
                            label = category.label,
                            selected = state.adjustCategory == category,
                            description = "adjust_category_${category.name.lowercase()}",
                            onClick = { state.selectAdjustCategory(category) },
                        )
                    }
                }

                PanelStage.Items -> Column {
                    if (state.adjustCategory == AdjustCategory.Hsl) {
                        ChipRow {
                            for (band in HslBand.entries) {
                                BandChip(
                                    band = band,
                                    selected = state.hslBand == band,
                                    onClick = { state.selectHslBand(band) },
                                )
                            }
                        }
                        ChipRow {
                            for (component in HslComponent.entries) {
                                Chip(
                                    label = component.label,
                                    selected = state.hslComponent == component,
                                    description = "adjust_hsl_${component.name.lowercase()}",
                                    onClick = { state.selectHslComponent(component) },
                                )
                            }
                        }
                    } else {
                        ChipRow {
                            for (item in AdjustControl.byCategory(state.adjustCategory)) {
                                ControlChip(
                                    control = item,
                                    value = state.adjustParams.value(item),
                                    selected = control == item,
                                    onClick = { state.selectAdjustControl(item) },
                                )
                            }
                        }
                    }
                }

                PanelStage.Edit -> Column {
                    IntensitySlider(
                        value = state.adjustValue,
                        valueRange = control.range,
                        neutralValue = control.neutral,
                        onValueChange = { state.setAdjustValue(it) },
                        onDragStart = { state.beginAdjustDrag() },
                        onDragFinished = { state.commitAdjustDrag() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

private fun formatValue(value: Int, control: AdjustControl): String =
    if (control.signed && value > 0) "+$value" else value.toString()

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color(0xFF141414) else EditorColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EditorColors.Accent else EditorColors.PanelElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    )
}

@Composable
private fun BandChip(
    band: HslBand,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EditorColors.Accent.copy(alpha = 0.18f) else EditorColors.PanelElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics {
                contentDescription = "adjust_band_${band.name.lowercase()}"
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFF000000L or band.colorRgb)),
        )
        Text(
            text = band.label,
            color = if (selected) EditorColors.Accent else EditorColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ControlChip(
    control: AdjustControl,
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EditorColors.PanelElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .semantics {
                contentDescription = "adjust_control_${control.id}"
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = control.label,
            color = if (selected) EditorColors.Accent else EditorColors.TextSecondary,
            fontSize = 11.sp,
        )
        if (value != control.neutral) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(EditorColors.Accent),
            )
        }
    }
}

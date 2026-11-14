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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.canvas.CanvasControl
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.ui.theme.EditorColors
import java.util.Locale
import kotlin.math.roundToInt

/** The current navigation stage alone is mounted; the photo receives the remaining height. */
@Composable
internal fun CanvasPanel(state: EditorState) {
    val control = state.canvasControl
    val stage = state.panelStage
    val p = state.canvasParams
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(EditorColors.Panel).animateContentSize(),
    ) {
        EditorPanelHeader(
            title = if (stage == PanelStage.Categories) "Canvas" else if (control == CanvasControl.Ratio) p.ratio.displayName else "Rotate",
            subtitle = when (stage) {
                PanelStage.Categories -> "Choose a category"
                PanelStage.Items -> if (control == CanvasControl.Ratio) "Tap a format · tap selected to zoom" else "Rotate, flip or straighten"
                PanelStage.Edit -> if (control == CanvasControl.Ratio) "Drag the photo to position" else "Keep edges inside the image"
            },
            onBack = if (stage == PanelStage.Categories) null else state::panelBack,
            actions = {
                if (stage == PanelStage.Items && control == CanvasControl.Ratio) {
                    EditorPanelAction("Edit", true, "canvas_edit", state::editCanvas, accessibilityLabel = "Edit canvas")
                }
                EditorPanelAction("Reset", !p.isIdentity, "canvas_reset", state::resetCanvas, accessibilityLabel = "Reset canvas")
            },
        )
        when (stage) {
            PanelStage.Categories -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CanvasButton("Ratio", "canvas_category_ratio") { state.selectCanvasControl(CanvasControl.Ratio) }
                CanvasButton("Rotate", "canvas_category_rotate") { state.selectCanvasControl(CanvasControl.Rotate) }
            }
            PanelStage.Items -> if (control == CanvasControl.Ratio) {
                val index = CanvasRatio.entries.indexOf(p.ratio)
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 1).coerceAtLeast(0))
                LaunchedEffect(p.ratio) {
                    listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                ) {
                    items(CanvasRatio.entries, key = { it.name }) { ratio ->
                        CanvasRatioCard(ratio, p.ratio == ratio) { state.selectCanvasRatio(ratio) }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasTransformButtons(state)
                    CanvasButton("Straighten", "canvas_straighten_edit", state::editCanvas)
                }
            }
            PanelStage.Edit -> {
                val zoom = control == CanvasControl.Ratio
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (zoom) "Zoom" else "Straighten", color = EditorColors.TextPrimary, fontSize = 13.sp)
                    Text(
                        if (zoom) String.format(Locale.US, "%.2f×", p.zoom) else String.format(Locale.US, "%.1f°", p.straighten),
                        color = EditorColors.Accent, fontSize = 13.sp,
                    )
                }
                IntensitySlider(
                    value = if (zoom) (p.zoom * 100).roundToInt() else (p.straighten * 10).roundToInt(),
                    onValueChange = {
                        if (zoom) state.setCanvasZoom(it / 100f) else state.setCanvasStraighten(it / 10f)
                    },
                    onDragStart = state::beginCanvasGesture,
                    onDragFinished = state::finishCanvasGesture,
                    modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 20.dp),
                    valueRange = if (zoom) 100..800 else -450..450,
                    neutralValue = if (zoom) 100 else 0,
                    description = if (zoom) "canvas_zoom" else "canvas_straighten",
                    accessibilityLabel = if (zoom) "Zoom" else "Straighten",
                )
                Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) { CanvasTransformButtons(state) }
            }
        }
    }
}

@Composable
private fun CanvasTransformButtons(state: EditorState) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CanvasButton("Rotate 90°", "canvas_rotate_90", state::rotateCanvas)
        CanvasButton("Flip", "canvas_flip", state::flipCanvas)
        CanvasButton("Center", "canvas_center", state::centerCanvas)
    }
}

@Composable
private fun CanvasButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(EditorColors.PanelElevated)
            .testTag(description).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = EditorColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun CanvasRatioCard(ratio: CanvasRatio, chosen: Boolean, onClick: () -> Unit) {
    val color = if (chosen) EditorColors.Accent else EditorColors.TextSecondary
    Column(
        Modifier.width(82.dp).clip(RoundedCornerShape(12.dp)).testTag("canvas_ratio_${ratio.name}")
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp).semantics {
                contentDescription = ratio.displayName
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(60.dp)) {
                val aspect = if (ratio == CanvasRatio.ORIGINAL) 0.75f else ratio.width.toFloat() / ratio.height
                val width = if (aspect >= 1f) size.width - 4.dp.toPx() else (size.height - 4.dp.toPx()) * aspect
                val height = width / aspect
                drawRoundRect(
                    color = color, topLeft = Offset((size.width - width) / 2, (size.height - height) / 2),
                    size = Size(width, height), cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(if (chosen) 2.dp.toPx() else 1.dp.toPx()),
                )
            }
            Text(if (ratio == CanvasRatio.ORIGINAL) "↗" else ratio.ratioLabel, color = color, fontSize = 12.sp)
        }
        Text(ratio.label, color = color, fontSize = 11.sp, maxLines = 1)
    }
}

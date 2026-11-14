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

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EffectShelf
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.filter.FilterRegistry
import com.aliahad.photoeffects.effect.render.EffectBitmapRenderer
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasBitmapRenderer
import com.aliahad.photoeffects.ui.theme.EditorColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Shared thumbnail cache; keys include the photo, preset and the other composed stage. */
private val thumbnailCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/**
 * Effects / Filters panel: family shelves, live thumbnails, and independent controls.
 * Cards are thumbnails of the *current* photo rendered with each preset by the CPU mirror.
 */
@Composable
fun EffectsPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val photo = state.photo
    val isFilters = state.activeTool == EditorTool.Filters
    // Render the shelf the user can see first; unrelated families must not delay its cards.
    val catalogue = state.effects
    val stage = if (state.panelStage == PanelStage.Edit && state.activePreset == null) {
        PanelStage.Items
    } else {
        state.panelStage
    }
    val otherStage = if (isFilters) {
        state.selectedEffect?.let { EditStage(it, state.selection?.intensity ?: it.defaultIntensity) }
    } else {
        state.selectedFilter?.let { EditStage(it, 100) }
    }
    val adjustStage = state.selectedAdjust?.let { EditStage(it, 100) }
    // Thumbnails are only needed while the preset cards are visible.
    val thumbnailSource = remember(photo?.id, photo?.bitmap) {
        photo?.bitmap?.let {
            EffectBitmapRenderer.downscale(it, EffectBitmapRenderer.THUMBNAIL_MAX_DIMENSION)
        }
    }
    val thumbnails = if (stage == PanelStage.Items) {
        rememberEffectThumbnails(
            photo,
            thumbnailSource,
            catalogue,
            otherStage,
            adjustStage,
            state.adjustParams.hashCode(),
            isFilters,
            state.canvasParams,
        )
    } else {
        emptyMap()
    }
    val selectedEffect = state.activePreset
    val title = when (stage) {
        PanelStage.Categories -> if (isFilters) "Filters" else "Effects"
        PanelStage.Items -> state.activeShelf.title
        PanelStage.Edit -> selectedEffect?.displayName ?: "Edit"
    }
    val subtitle = when (stage) {
        PanelStage.Categories -> "Choose a category"
        PanelStage.Items -> "Tap to try · tap selected again to edit"
        PanelStage.Edit -> selectedEffect?.summary ?: "Choose a preset"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(EditorColors.Panel)
            .animateContentSize(),
    ) {
        EditorPanelHeader(
            title = title,
            subtitle = subtitle,
            onBack = if (stage != PanelStage.Categories) {
                { state.panelBack() }
            } else null,
            actions = {
                if (selectedEffect != null && stage != PanelStage.Categories) {
                    if (stage == PanelStage.Items) {
                        EditorPanelAction(
                            label = "Edit",
                            enabled = true,
                            description = "preset_edit",
                            onClick = { state.editSelectedPreset() },
                        )
                    }
                    EditorPanelAction(
                        label = "Clear",
                        enabled = true,
                        description = "preset_clear",
                        onClick = { state.clearSelectedPreset() },
                    )
                }
            },
        )
        AnimatedContent(
            targetState = stage,
            transitionSpec = { panelStageTransition() },
            label = "preset_stage",
        ) { current ->
            when (current) {
                PanelStage.Categories -> CategoryChips(
                    activeId = state.activeShelf.id,
                    shelves = if (isFilters) FilterRegistry.shelves else EffectRegistry.shelves,
                    onSelect = { state.selectShelf(it) },
                )

                PanelStage.Items -> {
                    val effects = state.effects
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Fit small variant shelves on screen; wider shelves scroll at the 92.dp cap.
                        val edgePadding = 16.dp
                        val gap = 10.dp
                        val fitWidth =
                            (maxWidth - edgePadding * 2 - gap * (effects.size - 1)) /
                                effects.size.coerceAtLeast(1)
                        val cardWidth = fitWidth.coerceIn(48.dp, 92.dp)
                        val cardHeight = cardWidth * 1.22f
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = edgePadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            items(effects, key = { it.id }) { effect ->
                                EffectCard(
                                    effect = effect,
                                    thumbnail = thumbnails[effect.id],
                                    selected = selectedEffect?.id == effect.id,
                                    cardWidth = cardWidth,
                                    cardHeight = cardHeight,
                                    onClick = {
                                        if (selectedEffect?.id == effect.id) {
                                            state.editSelectedPreset()
                                        } else if (isFilters) {
                                            state.selectFilter(effect)
                                        } else {
                                            state.selectEffect(effect)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                PanelStage.Edit -> if (selectedEffect != null) {
                    Column {
                        if (isFilters) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 18.dp, end = 18.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                for (control in state.filterControls) {
                                    Text(
                                        text = control.label,
                                        color = if (state.activeFilterControl == control) {
                                            EditorColors.Accent
                                        } else {
                                            EditorColors.TextSecondary
                                        },
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { state.filterControl = control }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .semantics {
                                                contentDescription =
                                                    "filter_control_${control.name.lowercase()}"
                                                selected = state.activeFilterControl == control
                                            },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isFilters) state.activeFilterControl.label else "Intensity",
                                color = EditorColors.TextMuted,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = state.intensity.toString(),
                                color = EditorColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics {
                                    contentDescription = "intensity_value"
                                },
                            )
                        }
                        IntensitySlider(
                            value = state.intensity,
                            valueRange = if (isFilters) state.activeFilterControl.range else 0..100,
                            onValueChange = { state.setIntensity(it) },
                            onDragStart = { state.beginIntensityDrag() },
                            onDragFinished = { state.commitIntensityDrag() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(horizontal = 18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun rememberEffectThumbnails(
    photo: LoadedPhoto?,
    thumbnailSource: Bitmap?,
    catalogue: List<PhotoEffect>,
    otherStage: EditStage?,
    adjustStage: EditStage?,
    adjustKey: Int,
    isFilters: Boolean,
    canvas: CanvasParams,
): Map<String, Bitmap> {
    val thumbnails by produceState<Map<String, Bitmap>>(
        emptyMap(),
        photo?.id,
        thumbnailSource,
        catalogue,
        otherStage,
        adjustStage,
        adjustKey,
        isFilters,
        canvas,
    ) {
        value = emptyMap()
        val p = photo ?: return@produceState
        val source = thumbnailSource ?: p.bitmap
        // Coalesce slider/selection churn before starting an expensive render pass.
        if (otherStage != null || adjustStage != null) delay(160)
        val job = currentCoroutineContext()[Job]
        val otherKey = otherStage?.let {
            "${it.effect.id}:${it.effect.gpu.uniforms.hashCode()}:${it.intensity}"
        } ?: "none"
        for (effect in catalogue) {
            ensureActive()
            val key = "${p.id}|$isFilters|${effect.id}|$adjustKey|$otherKey|$canvas"
            val cached = thumbnailCache.get(key)
            if (cached != null) {
                value = value + (effect.id to cached)
                continue
            }
            val rendered = renderOnBackground(dispose = Bitmap::recycle) {
                val stage = EditStage(effect, effect.defaultIntensity)
                val composed = if (isFilters) {
                    EditComposition.compose(stage, adjustStage, otherStage)
                } else {
                    EditComposition.compose(otherStage, adjustStage, stage)
                }
                val look = EffectBitmapRenderer.renderThumbnail(
                    source = source,
                    effect = requireNotNull(composed),
                    intensityPercent = 100,
                    seed = p.seed,
                    cancel = { job?.isActive == false },
                )
                if (canvas.isIdentity) look else try {
                    CanvasBitmapRenderer.render(
                        look, canvas.geometry(p.sourceWidth, p.sourceHeight),
                        cancel = { job?.isActive == false },
                    )
                } finally {
                    if (look !== source) look.recycle()
                }
            }
            thumbnailCache.put(key, rendered)
            value = value + (effect.id to rendered)
        }
    }
    return thumbnails
}

@Composable
private fun CategoryChips(
    activeId: String,
    shelves: List<EffectShelf>,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (shelf in shelves) {
            val selected = shelf.id == activeId
            Row(
                modifier = Modifier
                    .clickable { onSelect(shelf.id) }
                    .semantics { contentDescription = "category_${shelf.id}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = EditorColors.Accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = shelf.title,
                    color = if (selected) EditorColors.Accent else EditorColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 1.1.sp,
                )
            }
        }
    }
}

@Composable
private fun EffectCard(
    effect: PhotoEffect,
    thumbnail: Bitmap?,
    selected: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(cardWidth * 0.15f)
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(shape)
            .background(EditorColors.Card)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) EditorColors.Accent else EditorColors.Hairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "effect_card_${effect.id}" },
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(EditorColors.PanelElevated, EditorColors.Card),
                        ),
                    ),
            ) {
                CircularProgressIndicator(
                    color = EditorColors.AccentDim,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp),
                )
            }
        }
        // Bottom scrim + name
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xE60B0B0D),
                    ),
                ),
        )
        Text(
            text = if (cardWidth < 72.dp && '#' in effect.displayName) {
                "#${effect.displayName.substringAfterLast('#').trim()}"
            } else effect.displayName,
            color = EditorColors.TextPrimary,
            fontSize = if (cardWidth < 72.dp) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (cardWidth < 72.dp) 0.2.sp else 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 7.dp),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(EditorColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF141414),
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun IntensitySection(
    effect: PhotoEffect,
    intensity: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = effect.displayName,
                color = EditorColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = effect.summary,
                color = EditorColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = intensity.toString(),
                color = EditorColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { contentDescription = "intensity_value" },
            )
        }
        IntensitySlider(
            value = intensity,
            valueRange = valueRange,
            onValueChange = onValueChange,
            onDragStart = onDragStart,
            onDragFinished = onDragFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        )
    }
}

/**
 * Recipe-ranged slider with tick marks; signed adjustments fill from the neutral centre.
 */
@Composable
fun IntensitySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 0..100,
    neutralValue: Int = 0,
    description: String = "intensity_slider",
    accessibilityLabel: String = description,
) {
    var widthPx by remember { mutableStateOf(1f) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    fun valueFor(x: Float): Int {
        val pad = widthPx * 0.02f
        val usable = (widthPx - pad * 2f).coerceAtLeast(1f)
        val t = ((x - pad) / usable).coerceIn(0f, 1f)
        return (valueRange.first + t * (valueRange.last - valueRange.first)).roundToInt()
    }
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx, valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnDragStart()
                    try {
                        currentOnValueChange(valueFor(down.position.x))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            currentOnValueChange(valueFor(change.position.x))
                            change.consume()
                        }
                    } finally {
                        // A panel/configuration change can cancel pointer input mid-drag.
                        currentOnDragFinished()
                    }
                }
            }
            .testTag(description)
            .semantics {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    value.toFloat(), valueRange.first.toFloat()..valueRange.last.toFloat(),
                    valueRange.last - valueRange.first - 1,
                )
                setProgress { target ->
                    onDragStart()
                    onValueChange(target.roundToInt().coerceIn(valueRange))
                    onDragFinished()
                    true
                }
            },
    ) {
        val pad = size.width * 0.02f
        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
        val centerY = size.height * 0.32f
        fun fraction(v: Int) = (v.coerceIn(valueRange) - valueRange.first).toFloat() /
            (valueRange.last - valueRange.first)
        val valueX = pad + usable * fraction(value)
        val neutralX = pad + usable * fraction(neutralValue)

        // Hairline track
        drawLine(
            color = EditorColors.Track,
            start = Offset(pad, centerY),
            end = Offset(size.width - pad, centerY),
            strokeWidth = 1.dp.toPx(),
        )
        // Progress
        drawLine(
            color = EditorColors.Accent,
            start = Offset(neutralX, centerY),
            end = Offset(valueX, centerY),
            strokeWidth = 1.5.dp.toPx(),
        )
        // Tick marks
        for (i in 0..20) {
            val tx = pad + usable * (i / 20f)
            val tickTop = centerY + 7.dp.toPx()
            val tickHeight = (if (i % 2 == 0) 7.dp else 4.dp).toPx()
            drawLine(
                color = if (tx >= minOf(neutralX, valueX) - 0.5f && tx <= maxOf(neutralX, valueX) + 0.5f) {
                    EditorColors.Accent
                } else EditorColors.Track,
                start = Offset(tx, tickTop),
                end = Offset(tx, tickTop + tickHeight),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // Thumb
        drawRoundRect(
            color = EditorColors.Accent,
            topLeft = Offset(valueX - 2.dp.toPx(), centerY - 9.dp.toPx()),
            size = Size(4.dp.toPx(), 18.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

package com.aliahad.photoeffects.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EffectShelf
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.render.EffectBitmapRenderer
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.ui.theme.EditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Effects panel: category shelves + live effect cards + intensity row (spec §2.5).
 * Cards are thumbnails of the *current* photo rendered with each preset by the CPU mirror.
 */
@Composable
fun EffectsPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val photo = state.photo
    val thumbnails = rememberEffectThumbnails(photo)
    val selectedEffect = state.selectedEffect
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(EditorColors.Panel),
    ) {
        CategoryChips(
            activeId = state.activeShelfId,
            shelves = EffectRegistry.shelves,
            onSelect = { state.selectShelf(it) },
        )
        val effects = state.effects
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Every card of a shelf fits the screen at once (PREQUEL's variant strip shows all
            // variants without scrolling); a shelf too wide for the 92.dp cap scrolls instead.
            val edgePadding = 16.dp
            val gap = 10.dp
            val fitWidth =
                (maxWidth - edgePadding * 2 - gap * (effects.size - 1)) / effects.size.coerceAtLeast(1)
            val cardWidth = fitWidth.coerceIn(56.dp, 92.dp)
            val cardHeight = cardWidth * 1.22f
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = edgePadding),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(effects, key = { it.id }) { effect ->
                    EffectCard(
                        effect = effect,
                        thumbnail = thumbnails[effect.id],
                        selected = selectedEffect?.id == effect.id,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onClick = { state.toggleEffect(effect) },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = selectedEffect != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (selectedEffect != null) {
                IntensitySection(
                    effect = selectedEffect,
                    intensity = state.intensity,
                    onValueChange = { state.setIntensity(it) },
                    onDragStart = { state.beginIntensityDrag() },
                    onDragFinished = { state.commitIntensityDrag() },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun rememberEffectThumbnails(photo: LoadedPhoto?): Map<String, Bitmap> {
    val thumbnails by produceState<Map<String, Bitmap>>(emptyMap(), photo?.id) {
        val p = photo ?: return@produceState
        value = withContext(Dispatchers.Default) {
            EffectRegistry.all.associate { effect ->
                effect.id to EffectBitmapRenderer.renderThumbnail(
                    source = p.bitmap,
                    effect = effect,
                    intensityPercent = effect.defaultIntensity,
                    seed = p.seed,
                )
            }
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
            text = effect.displayName,
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
 * Custom 0..100 slider with PREQUEL-style tick marks and orange thumb/progress.
 */
@Composable
fun IntensitySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1f) }
    fun valueFor(x: Float): Int {
        val pad = widthPx * 0.02f
        val usable = (widthPx - pad * 2f).coerceAtLeast(1f)
        val t = ((x - pad) / usable).coerceIn(0f, 1f)
        return (t * 100f).roundToInt()
    }
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onDragStart()
                    onValueChange(valueFor(down.position.x))
                    down.consume()
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            active = false
                            onDragFinished()
                        } else {
                            onValueChange(valueFor(change.position.x))
                            change.consume()
                        }
                    }
                }
            }
            .semantics { contentDescription = "intensity_slider" },
    ) {
        val pad = size.width * 0.02f
        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
        val centerY = size.height * 0.32f
        val valueX = pad + usable * (value.coerceIn(0, 100) / 100f)

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
            start = Offset(pad, centerY),
            end = Offset(valueX, centerY),
            strokeWidth = 1.5.dp.toPx(),
        )
        // Tick marks (PREQUEL-style)
        for (i in 0..20) {
            val tx = pad + usable * (i / 20f)
            val tickTop = centerY + 7.dp.toPx()
            val tickHeight = (if (i % 2 == 0) 7.dp else 4.dp).toPx()
            drawLine(
                color = if (tx <= valueX + 0.5f) EditorColors.Accent else EditorColors.Track,
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

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
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient
import android.graphics.Shader as AndroidShader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.effect.DotMatrixDate
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.gpu.GpuRenderGraph
import com.aliahad.photoeffects.effect.render.EffectBitmapRenderer
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.ui.theme.EditorColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.roundToInt

/**
 * Photo preview card:
 *  - GPU path: the selected preset's AGSL RuntimeShader is drawn as a Paint shader over a
 *    BitmapShader child (all pixel work stays on the GPU, no CPU readback).
 *  - CPU fallback: when the AGSL program cannot compile on the device, a downscaled bitmap is
 *    rendered with the deterministic CPU mirror.
 *  - Press-and-hold anywhere on the photo shows the untouched original (before/after).
 */
@Composable
fun PhotoPreviewCard(
    photo: LoadedPhoto,
    effect: PhotoEffect?,
    intensity: Int,
    showOriginal: Boolean,
    onChangePhoto: () -> Unit,
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    strengthLabel: String = "${intensity}%",
    canvas: CanvasParams = CanvasParams(),
    canvasEditing: Boolean = false,
    onCanvasBegin: () -> Unit = {},
    onCanvasEnd: () -> Unit = {},
    onCanvasTransform: (Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
) {
    val bitmap = photo.bitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val backdrop = rememberBackdrop(bitmap)
    val latestOnHoldChange by rememberUpdatedState(onHoldChange)
    DisposableEffect(Unit) {
        onDispose { latestOnHoldChange(false) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(EditorColors.Card)
            .semantics { contentDescription = "photo_preview" },
        contentAlignment = Alignment.Center,
    ) {
        // Ambient backdrop: a tiny pre-scaled copy fills the letterbox area. Bilinear upscaling
        // gives the same soft impression as a live 42.dp blur without a full-screen offscreen pass.
        Image(
            bitmap = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .alpha(0.28f),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(photo.id, canvasEditing) {
                    if (canvasEditing) return@pointerInput
                    detectTapGestures(onPress = {
                        latestOnHoldChange(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            latestOnHoldChange(false)
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            CanvasPhotoViewport(
                photo = photo, params = canvas, editing = canvasEditing,
                onBegin = onCanvasBegin, onEnd = onCanvasEnd, onTransform = onCanvasTransform,
            ) {
                if (effect != null) {
                    GpuOrCpuEffectImage(
                        photo = photo,
                        effect = effect,
                        intensity = intensity,
                        showOriginal = showOriginal,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        // The viewport owns aspect fitting. Fill its source layer so rounded
                        // decode sizes cannot insert a fractional letterbox before geometry.
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Top-right: change photo.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x99000000))
                    .clickable(onClick = onChangePhoto)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .semantics { contentDescription = "change_photo" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = EditorColors.TextPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Change",
                    color = EditorColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Top-center: ORIGINAL pill while the user holds the photo.
        AnimatedVisibility(
            visible = showOriginal,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(EditorColors.Accent)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "ORIGINAL",
                    color = Color(0xFF141414),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }
        }

        // Bottom-left: current effect + interaction hint.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC0B0B0D)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (effect != null) effect.displayName.uppercase() else "ORIGINAL PHOTO",
                color = EditorColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = if (canvasEditing) {
                    "Drag to position · pinch to zoom"
                } else if (effect != null) {
                    "Hold to compare · $strengthLabel"
                } else {
                    "Pick a preset below"
                },
                color = EditorColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GpuOrCpuEffectImage(
    photo: LoadedPhoto,
    effect: PhotoEffect,
    intensity: Int,
    showOriginal: Boolean,
    modifier: Modifier = Modifier,
) {
    val bitmap = photo.bitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val context = LocalContext.current
    // Render at a stable internal resolution. Binding the graph to the animated card size caused
    // a layout feedback loop and re-recorded every RenderNode on each panel-animation frame.
    val renderSize = remember(photo.id, bitmap) { previewRenderSize(bitmap) }
    val programs = remember(effect.gpu) { GpuRenderGraph.programs(effect.gpu) }
    val topology = remember(programs) { GpuRenderGraph.topology(programs) }
    val graph = remember(photo.id, bitmap, topology, renderSize) {
        GpuRenderGraph.build(context, bitmap, programs, renderSize.width, renderSize.height)
    }
    DisposableEffect(graph) {
        onDispose { graph?.release() }
    }
    if (graph == null) {
        // Keep the CPU producer mounted as well, so comparison does not restart rendering.
        Box(modifier) {
            CpuEffectImage(photo, effect, intensity, Modifier.fillMaxSize())
            if (showOriginal) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    // The stamp is rasterized once per size/date instead of emitting hundreds of radial-gradient
    // circles on every frame. It shares the graph's internal coordinate system and is scaled with
    // the photo during the final Canvas draw.
    val stampLayer = rememberStampLayer(effect.stamp, renderSize.width, renderSize.height)
    val stampPaint = remember(effect.stamp) { AndroidPaint() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused

    // Keep comparison atomic in a single Canvas without mounting/unmounting a separate Image node,
    // and refresh the drawing node after modal focus changes so its external RenderNode re-syncs.
    key(windowFocused) {
        Canvas(modifier = modifier) {
            if (showOriginal) {
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(
                        size.width.roundToInt().coerceAtLeast(1),
                        size.height.roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Low,
                )
                return@Canvas
            }
            val scaleX = size.width / graph.width
            val scaleY = size.height / graph.height
            graph.update(programs, photo.seed, intensity / 100f)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val save = native.save()
                native.scale(scaleX, scaleY)
                native.drawRenderNode(graph.root)
                if (stampLayer != null && intensity > 0 && effect.stampIntensity > 0f) {
                    stampPaint.alpha =
                        ((intensity / 100f * effect.stampIntensity).coerceIn(0f, 1f) * 255f).roundToInt()
                    native.drawBitmap(stampLayer.bitmap, stampLayer.left, stampLayer.top, stampPaint)
                }
                native.restoreToCount(save)
            }
        }
    }
}

private const val PREVIEW_MAX_DIMENSION = 1600

private fun previewRenderSize(bitmap: Bitmap): IntSize {
    val longest = maxOf(bitmap.width, bitmap.height, 1)
    val scale = minOf(1f, PREVIEW_MAX_DIMENSION.toFloat() / longest)
    return IntSize(
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
    )
}

private data class StampLayer(val bitmap: Bitmap, val left: Float, val top: Float)

@Composable
private fun rememberBackdrop(source: Bitmap): ImageBitmap = remember(source) {
    val maxDimension = 96
    val scale = maxDimension.toFloat() / maxOf(source.width, source.height, 1)
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    Bitmap.createScaledBitmap(source, width, height, true).asImageBitmap()
}

@Composable
private fun rememberStampLayer(
    spec: DotMatrixDate.StampSpec?,
    width: Int,
    height: Int,
): StampLayer? {
    if (spec == null || width <= 0 || height <= 0) return null
    val parts = spec.dateProvider()
    return remember(spec, parts, width, height) {
        val dots = DotMatrixDate.layout(parts, width, height)
        if (dots.isEmpty()) {
            null
        } else {
            val pad = dots.maxOf { it.r * 1.45f } + 2f
            val minX = dots.minOf { it.cx - it.r * 1.45f } - pad
            val minY = dots.minOf { it.cy - it.r * 1.45f } - pad
            val maxX = dots.maxOf { it.cx + it.r * 1.45f } + pad
            val maxY = dots.maxOf { it.cy + it.r * 1.45f } + pad
            val left = kotlin.math.floor(minX).toInt()
            val top = kotlin.math.floor(minY).toInt()
            val bitmapWidth = (kotlin.math.ceil(maxX).toInt() - left).coerceAtLeast(1)
            val bitmapHeight = (kotlin.math.ceil(maxY).toInt() - top).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            val core = (0xFF000000L or spec.colorRgb).toInt()
            val glow = (core and 0x00FFFFFF) or (89 shl 24)
            val clear = core and 0x00FFFFFF
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
            for (dot in dots) {
                val cx = dot.cx - left
                val cy = dot.cy - top
                val outer = dot.r * 1.45f
                paint.shader = RadialGradient(
                    cx,
                    cy,
                    outer,
                    intArrayOf(core, glow, clear),
                    floatArrayOf(0f, 0.35f, 1f),
                    AndroidShader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, outer, paint)
            }
            paint.shader = null
            StampLayer(bitmap, left.toFloat(), top.toFloat())
        }
    }
}

/**
 * CPU fallback used only if the AGSL program fails to compile on this device.
 * Renders at a reduced resolution on a background thread and caches per (effect, intensity).
 */
@Composable
private fun CpuEffectImage(
    photo: LoadedPhoto,
    effect: PhotoEffect,
    intensity: Int,
    modifier: Modifier = Modifier,
) {
    val rendered by produceState<Bitmap?>(initialValue = null, photo.id, effect, intensity) {
        val job = currentCoroutineContext()[Job]
        value = renderOnBackground(dispose = Bitmap::recycle) {
            EffectBitmapRenderer.render(
                photo.bitmap,
                effect,
                intensity,
                photo.seed,
                maxDimension = 1080,
                cancel = { job?.isActive == false },
            )
        }
    }
    val bitmap = rendered
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .background(EditorColors.Panel)
                .semantics { contentDescription = "effect_rendering" },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = EditorColors.Accent,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

/** Simple grey rule used when the photo is still decoding. */
@Composable
fun PhotoLoadingPlaceholder(
    modifier: Modifier = Modifier,
    isLoading: Boolean = true,
    onPickPhoto: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(EditorColors.Card),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = EditorColors.Accent,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isLoading) "Loading photo" else "Choose a photo to continue",
                color = EditorColors.TextSecondary,
                fontSize = 12.sp,
            )
            if (!isLoading && onPickPhoto != null) {
                Text(
                    text = "Choose photo",
                    color = EditorColors.Accent,
                    modifier = Modifier
                        .clickable(onClick = onPickPhoto)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { contentDescription = "change_photo" },
                )
            }
        }
    }
}

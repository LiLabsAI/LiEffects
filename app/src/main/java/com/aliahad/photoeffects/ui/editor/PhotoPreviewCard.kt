package com.aliahad.photoeffects.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliahad.photoeffects.effect.DotMatrixDate
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.gpu.GpuEffectRenderer
import com.aliahad.photoeffects.effect.render.EffectBitmapRenderer
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.ui.theme.EditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) {
    val bitmap = photo.bitmap
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(EditorColors.Card)
            .semantics { contentDescription = "photo_preview" },
        contentAlignment = Alignment.Center,
    ) {
        // Ambient backdrop: blurred copy of the same photo fills the letterbox area.
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(42.dp)
                .alpha(0.28f),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(effect) {
                    detectTapGestures(onPress = {
                        onHoldChange(true)
                        tryAwaitRelease()
                        onHoldChange(false)
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                if (effect != null && !showOriginal) {
                    GpuOrCpuEffectImage(
                        photo = photo,
                        effect = effect,
                        intensity = intensity,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
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
                text = if (effect != null) {
                    "Hold to compare · ${intensity}%"
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
    modifier: Modifier = Modifier,
) {
    val bitmap = photo.bitmap
    val shader = remember(effect.id) {
        GpuEffectRenderer.compile(effect.gpu)
    }
    if (shader == null) {
        CpuEffectImage(photo, effect, intensity, modifier)
        return
    }
    val program = effect.gpu
    // Extra asset-backed shader inputs (SENSUAL's bundled adj-Blur mask). If any asset fails to
    // decode we fall back to the CPU path so the shader never runs with an unset shader input.
    val context = LocalContext.current
    val extraShaders: Map<String, BitmapShader> = remember(program, context) {
        val loaded = LinkedHashMap<String, BitmapShader>()
        for ((name, asset) in program.extraShaderAssets) {
            val bitmap = runCatching {
                context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: return@remember emptyMap()
            loaded[name] = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        loaded
    }
    if (extraShaders.size != program.extraShaderAssets.size) {
        CpuEffectImage(photo, effect, intensity, modifier)
        return
    }
    val bitmapShader = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    val paint = remember(effect.id) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier) {
        bitmapShader.setLocalMatrix(
            matrix.apply {
                reset()
                setScale(size.width / bitmap.width, size.height / bitmap.height)
            },
        )
        shader.setInputShader(GpuEffectRenderer.CONTENT_SHADER, bitmapShader)
        for ((name, maskShader) in extraShaders) {
            maskShader.setLocalMatrix(matrix)
            shader.setInputShader(name, maskShader)
        }
        GpuEffectRenderer.applyUniforms(
            shader = shader,
            program = program,
            intensity = intensity / 100f,
            photoSeed = photo.seed,
            widthPx = size.width,
            heightPx = size.height,
        )
        paint.shader = shader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
        // Dot-matrix stamp overlay (YESTERDAY "Date" tool): same layout the CPU renderer
        // rasterizes, fading with the intensity slider like the rest of the look.
        val spec = effect.stamp
        if (spec != null && intensity > 0) {
            val dots = DotMatrixDate.layout(
                spec.dateProvider(),
                size.width.roundToInt(),
                size.height.roundToInt(),
            )
            if (dots.isNotEmpty()) {
                // Thin dots with a soft amber falloff (PREQUEL's stamp dots bloom slightly).
                val core = Color(spec.colorRgb).copy(alpha = intensity / 100f)
                val glow = core.copy(alpha = core.alpha * 0.35f)
                val clear = core.copy(alpha = 0f)
                for (dot in dots) {
                    val center = Offset(dot.cx, dot.cy)
                    val outer = dot.r * 1.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(core, glow, clear),
                            center = center,
                            radius = outer,
                        ),
                        radius = outer,
                        center = center,
                    )
                }
            }
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
    val rendered by produceState<Bitmap?>(initialValue = null, photo.id, effect.id, intensity) {
        value = withContext(Dispatchers.Default) {
            EffectBitmapRenderer.render(photo.bitmap, effect, intensity, photo.seed, maxDimension = 1080)
        }
    }
    val bitmap = rendered
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
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
fun PhotoLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(EditorColors.Card),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(
                color = EditorColors.Accent,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Loading photo",
                color = EditorColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

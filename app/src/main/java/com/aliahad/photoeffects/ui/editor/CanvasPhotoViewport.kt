package com.aliahad.photoeffects.ui.editor

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.photo.LoadedPhoto
import kotlin.math.abs
import kotlin.math.roundToInt

/** Affine drawing on the existing hardware canvas: gestures never read back or crop a bitmap. */
@Composable
internal fun CanvasPhotoViewport(
    photo: LoadedPhoto,
    params: CanvasParams,
    editing: Boolean,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onTransform: (Float, Float, Float, Float, Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val geometry = remember(params, photo.sourceWidth, photo.sourceHeight) {
        params.geometry(photo.sourceWidth, photo.sourceHeight)
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val viewportWidth = maxWidth.coerceAtMost(maxHeight * geometry.aspect)
        val viewportHeight = viewportWidth / geometry.aspect
        // A stable source layout keeps the effect graph mounted at the same size during crop
        // gestures and ratio switches. Only its enclosing canvas matrix/clip changes.
        val sourceWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val sourceHeight = (sourceWidth.toDouble() * photo.sourceHeight / photo.sourceWidth).roundToInt().coerceAtLeast(1)
        val outputWidth = with(density) { viewportWidth.roundToPx() }
        val outputHeight = with(density) { viewportHeight.roundToPx() }
        val matrix = remember(geometry, sourceWidth, sourceHeight, outputWidth, outputHeight) {
            Matrix().apply {
                setValues(geometry.matrixValues(sourceWidth, sourceHeight, outputWidth.toDouble(), outputHeight.toDouble()))
            }
        }
        val gestures = if (editing) Modifier.pointerInput(photo.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var started = false
                var accumulatedZoom = 1f
                var accumulatedPan = Offset.Zero
                try {
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        accumulatedZoom *= zoom
                        accumulatedPan += pan
                        if (!started) {
                            val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
                            if (accumulatedPan.getDistance() > viewConfiguration.touchSlop || zoomMotion > viewConfiguration.touchSlop) {
                                started = true
                                onBegin()
                            }
                        }
                        if (started && centroid.isSpecified) {
                            onTransform(
                                accumulatedZoom, accumulatedPan.x / size.width.coerceAtLeast(1),
                                accumulatedPan.y / size.height.coerceAtLeast(1),
                                centroid.x / size.width.coerceAtLeast(1), centroid.y / size.height.coerceAtLeast(1),
                            )
                            accumulatedZoom = 1f
                            accumulatedPan = Offset.Zero
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    if (started) onEnd()
                }
            }
        } else Modifier
        fun move(x: Float, y: Float): Boolean {
            onBegin()
            onTransform(1f, x, y, 0.5f, 0.5f)
            onEnd()
            return true
        }
        Box(
            Modifier.size(viewportWidth, viewportHeight).clipToBounds().then(gestures).testTag("canvas_viewport")
                .semantics {
                    contentDescription = "Photo canvas"
                    stateDescription = params.ratio.displayName
                    if (editing) customActions = listOf(
                        CustomAccessibilityAction("Move photo left") { move(-0.1f, 0f) },
                        CustomAccessibilityAction("Move photo right") { move(0.1f, 0f) },
                        CustomAccessibilityAction("Move photo up") { move(0f, -0.1f) },
                        CustomAccessibilityAction("Move photo down") { move(0f, 0.1f) },
                    )
                },
        ) {
            Layout(
                content = {
                    Box(Modifier.fillMaxSize().drawWithContent {
                        drawContext.canvas.save()
                        try {
                            drawIntoCanvas { it.nativeCanvas.concat(matrix) }
                            drawContent()
                        } finally { drawContext.canvas.restore() }
                    }) { content() }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val image = measurables.single().measure(Constraints.fixed(sourceWidth, sourceHeight))
                layout(constraints.maxWidth, constraints.maxHeight) { image.place(0, 0) }
            }
            if (editing) Canvas(Modifier.fillMaxSize()) {
                val color = Color.White.copy(alpha = 0.5f)
                for (i in 1..2) {
                    val x = size.width * i / 3
                    val y = size.height * i / 3
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
                }
                drawRect(color, style = Stroke(2f))
            }
        }
    }
}

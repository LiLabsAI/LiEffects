package com.aliahad.photoeffects.effect.gpu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.Matrix
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.Log
import com.aliahad.photoeffects.effect.GpuProgram
import com.aliahad.photoeffects.canvas.CanvasGeometry

/**
 * Offscreen GPU readback used by the in-app diagnostics harness (test APK installation is blocked
 * on the target device). This is intentionally not on the preview/export path: export stays on
 * the deterministic CPU renderer.
 */
object GpuReadback {
    private const val TAG = "PhotoEffectsGpu"

    fun render(
        context: Context,
        source: Bitmap,
        program: GpuProgram,
        width: Int,
        height: Int,
        seed: Long,
        intensity: Float,
        geometry: CanvasGeometry? = null,
    ): IntArray? {
        val graph = GpuRenderGraph.build(context, source, GpuRenderGraph.programs(program), width, height)
            ?: return null
        val (outputWidth, outputHeight) = geometry?.outputSize(width, height) ?: (width to height)
        val canvasNode = geometry?.let {
            RenderNode("Canvas readback").apply {
                setPosition(0, 0, outputWidth, outputHeight)
                val canvas = beginRecording()
                canvas.clipRect(0, 0, outputWidth, outputHeight)
                canvas.concat(Matrix().apply {
                    setValues(it.matrixValues(width, height, outputWidth.toDouble(), outputHeight.toDouble()))
                })
                canvas.drawRenderNode(graph.root)
                endRecording()
            }
        }
        val reader = ImageReader.newInstance(
            outputWidth,
            outputHeight,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        return try {
            renderer.setOpaque(false)
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(canvasNode ?: graph.root)
            graph.update(GpuRenderGraph.programs(program), seed, intensity)
            val status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            if (status != HardwareRenderer.SYNC_OK) {
                Log.w(TAG, "GPU readback sync failed: $status")
                return null
            }
            val image = reader.acquireNextImage() ?: return null
            image.use {
                it.fence.use { fence -> check(fence.awaitForever()) { "GPU fence failed" } }
                val plane = it.planes.single()
                val bytes = plane.buffer
                IntArray(outputWidth * outputHeight) { i ->
                    val offset = (i / outputWidth) * plane.rowStride + (i % outputWidth) * plane.pixelStride
                    val r = bytes.get(offset).toInt() and 255
                    val g = bytes.get(offset + 1).toInt() and 255
                    val b = bytes.get(offset + 2).toInt() and 255
                    val a = bytes.get(offset + 3).toInt() and 255
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "GPU readback failed", t)
            null
        } finally {
            graph.release()
            canvasNode?.discardDisplayList()
            renderer.destroy()
            reader.close()
        }
    }
}

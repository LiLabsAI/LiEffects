package com.aliahad.photoeffects

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aliahad.photoeffects.effect.cpu.BlurMaskData
import com.aliahad.photoeffects.effect.cpu.BlurMaskStore
import com.aliahad.photoeffects.effect.cpu.FocalLutData
import com.aliahad.photoeffects.effect.cpu.FocalLutStore
import com.aliahad.photoeffects.effect.cpu.KujiFrameData
import com.aliahad.photoeffects.effect.cpu.KujiFrameStore
import com.aliahad.photoeffects.effect.FocalFamily
import com.aliahad.photoeffects.effect.KujiFamily
import com.aliahad.photoeffects.diagnostics.RenderDiagnostics
import com.aliahad.photoeffects.ui.editor.EditorRoute
import com.aliahad.photoeffects.ui.theme.PhotoEffectsTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake while editing so long editing sessions do not dim the preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // All look assets are needed by the very first thumbnail pass. Decode them before the UI
        // so CPU thumbnails and the GPU preview see the same data (historical async mask load
        // raced the first render and produced mismatched Sensual thumbnails).
        loadBlurMask()
        loadKujiFrames()
        loadFocalLuts()
        setContent {
            PhotoEffectsTheme {
                EditorRoute()
            }
        }
        if (intent.getBooleanExtra("render_diagnostics", false)) {
            Thread { RenderDiagnostics.run(applicationContext) }
                .apply { isDaemon = true }
                .start()
        }
    }

    /**
     * Decode the bundled PREQUEL adj-Blur mask (`assets/blurmask.jpg`) into the process-wide
     * [BlurMaskStore] used by the SENSUAL CPU pipeline (thumbnails + export). The GPU preview
     * binds the same asset directly as a shader input.
     */
    private fun loadBlurMask() {
        runCatching {
            val bitmap = applicationContext.assets.open("blurmask.jpg").use {
                BitmapFactory.decodeStream(it)
            } ?: return
            val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            val pixels = IntArray(256 * 256)
            small.getPixels(pixels, 0, 256, 0, 0, 256, 256)
            val values = FloatArray(pixels.size) { i ->
                ((pixels[i] shr 16) and 0xFF) / 255f
            }
            BlurMaskStore.current = BlurMaskData(values, 256, 256)
            if (small !== bitmap) small.recycle()
            bitmap.recycle()
        }
    }

    /**
     * Decode the five bundled KUJI frame overlays (`assets/kuji_frame_N.png`) into the
     * process-wide [KujiFrameStore]. They are downscaled to a 1080-px long edge (the preview cap)
     * to keep memory bounded; the GPU preview binds the full-resolution PNG directly. Decoded
     * before `setContent` because the first Effects-panel thumbnail pass needs them.
     */
    private fun loadKujiFrames() {
        val assets = applicationContext.assets
        for (params in KujiFamily.presets) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = assets.open(params.frameAsset).use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching
                val longest = maxOf(bitmap.width, bitmap.height)
                val scaled = if (longest > 1080) {
                    val s = 1080f / longest
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * s).roundToInt().coerceAtLeast(1),
                        (bitmap.height * s).roundToInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    bitmap
                }
                val pixels = IntArray(scaled.width * scaled.height)
                scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                KujiFrameStore.put(
                    params.frameAsset,
                    KujiFrameData(pixels, scaled.width, scaled.height),
                )
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }
    }

    /**
     * Decode the bundled FOCAL LUT atlas (`assets/focal_luts.png`, six 198x198 blocks) into the
     * process-wide [FocalLutStore]. Decoded before `setContent` because the Effects-panel
     * thumbnail pass needs the CPU LUTs immediately; the GPU preview binds the same PNG directly.
     */
    private fun loadFocalLuts() {
        runCatching {
            val bitmap = applicationContext.assets.open("focal_luts.png").use {
                BitmapFactory.decodeStream(it)
            } ?: return
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (params in FocalFamily.presets) {
                FocalLutStore.put(
                    params.id,
                    FocalLutData.fromAtlas(pixels, bitmap.width, params.lutIndex),
                )
            }
            bitmap.recycle()
        }
    }
}

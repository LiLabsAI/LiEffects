package com.aliahad.photoeffects

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aliahad.photoeffects.effect.cpu.BlurMaskData
import com.aliahad.photoeffects.effect.cpu.BlurMaskStore
import com.aliahad.photoeffects.ui.editor.EditorRoute
import com.aliahad.photoeffects.ui.theme.PhotoEffectsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake while editing so long editing sessions do not dim the preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadBlurMaskAsync()
        setContent {
            PhotoEffectsTheme {
                EditorRoute()
            }
        }
    }

    /**
     * Decode the bundled PREQUEL adj-Blur mask (`assets/blurmask.jpg`) into the process-wide
     * [BlurMaskStore] used by the SENSUAL CPU pipeline (thumbnails + export). The GPU preview
     * binds the same asset directly as a shader input.
     */
    private fun loadBlurMaskAsync() {
        val appContext = applicationContext
        Thread {
            runCatching {
                val bitmap = appContext.assets.open("blurmask.jpg").use {
                    BitmapFactory.decodeStream(it)
                } ?: return@Thread
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
        }.apply { isDaemon = true }.start()
    }
}

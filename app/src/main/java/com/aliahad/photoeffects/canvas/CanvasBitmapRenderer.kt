package com.aliahad.photoeffects.canvas

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException

/** Final geometry pass after Filter → Adjust → Effect, including effect frames/date overlays. */
object CanvasBitmapRenderer {
    fun render(
        source: Bitmap,
        geometry: CanvasGeometry,
        cancel: () -> Boolean = { false },
    ): Bitmap {
        if (cancel()) throw CancellationException("Canvas export cancelled")
        val (width, height) = geometry.outputSize(source.width, source.height)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val output = CanvasPixels.render(pixels, source.width, source.height, geometry, cancel)
        if (cancel()) throw CancellationException("Canvas export cancelled")
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(source.hasAlpha())
        }
    }
}

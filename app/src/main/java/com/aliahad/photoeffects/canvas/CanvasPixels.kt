package com.aliahad.photoeffects.canvas

import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.effect.cpu.PixelRunner
import kotlinx.coroutines.CancellationException

/** Pure, row-parallel affine sampler. Pixel centers and premultiplied interpolation match GPU. */
internal object CanvasPixels {
    fun render(
        source: IntArray, width: Int, height: Int, geometry: CanvasGeometry,
        cancel: () -> Boolean = { false },
    ): IntArray {
        require(width > 0 && height > 0 && source.size.toLong() == width.toLong() * height)
        val (outputWidth, outputHeight) = geometry.outputSize(width, height)
        val output = IntArray(outputWidth * outputHeight)
        val m = geometry.inverseMatrixValues(width, height, outputWidth, outputHeight)
        PixelRunner.forEachRow(outputHeight) { y ->
            if (cancel()) throw CancellationException("Canvas export cancelled")
            val py = y + 0.5f
            val startX = m[1] * py + m[2]
            val startY = m[4] * py + m[5]
            for (x in 0 until outputWidth) {
                val px = x + 0.5f
                output[y * outputWidth + x] = PixelMath.sampleArgb(
                    source, width, height, m[0] * px + startX, m[3] * px + startY,
                )
            }
        }
        if (cancel()) throw CancellationException("Canvas export cancelled")
        return output
    }
}

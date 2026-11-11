package com.aliahad.photoeffects.canvas

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CanvasPixelsTest {
    private val pixels = IntArray(8) { 0xff000000.toInt() or (it * 20 shl 16) }

    @Test fun centerSquareSelectsTheMiddleColumnsWithoutResampling() {
        val before = pixels.copyOf()
        val result = CanvasPixels.render(pixels, 4, 2, CanvasParams(CanvasRatio.SQUARE).geometry(4, 2))
        assertArrayEquals(intArrayOf(pixels[1], pixels[2], pixels[5], pixels[6]), result)
        assertArrayEquals(before, pixels)
    }

    @Test fun quarterTurnRotatesPixelsClockwiseWithoutChangingTheirValues() {
        val result = CanvasPixels.render(pixels, 4, 2, CanvasParams(quarterTurns = 1).geometry(4, 2))
        assertArrayEquals(intArrayOf(pixels[4], pixels[0], pixels[5], pixels[1], pixels[6], pixels[2], pixels[7], pixels[3]), result)
    }

    @Test fun flipReversesEachRow() {
        val result = CanvasPixels.render(pixels, 4, 2, CanvasParams(mirrored = true).geometry(4, 2))
        assertArrayEquals(intArrayOf(pixels[3], pixels[2], pixels[1], pixels[0], pixels[7], pixels[6], pixels[5], pixels[4]), result)
    }

    @Test fun halfTransparentEdgeInterpolatesCoverageWithoutHiddenBlue() {
        val result = CanvasPixels.render(intArrayOf(0xffff0000.toInt(), 0x000000ff), 2, 1,
            CanvasParams(CanvasRatio.SQUARE).geometry(2, 1))
        assertEquals(0x80ff0000.toInt(), result.single())
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagatesFromParallelRows() {
        CanvasPixels.render(IntArray(160 * 160), 160, 160, CanvasParams().geometry(160, 160)) { true }
    }
}

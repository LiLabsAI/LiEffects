package com.aliahad.photoeffects.canvas

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CanvasGeometryTest {
    @Test fun formatsUseExactWidthToHeightRatios() {
        val expected = listOf(1.0, 9.0 / 16, 16.0 / 9, 4.0 / 5, 5.0 / 4, 3.0 / 4, 4.0 / 3, 2.0 / 3, 3.0 / 2)
        CanvasRatio.entries.drop(1).zip(expected).forEach { (ratio, aspect) ->
            val g = CanvasParams(ratio).geometry(4000, 3000)
            assertEquals(ratio.name, aspect, g.cropWidth / g.cropHeight, 1e-10)
        }
    }

    @Test fun originalAndQuarterTurnsKeepAllSourcePixels() {
        assertEquals(3501 to 4503, CanvasParams().geometry(3501, 4503).outputSize())
        assertEquals(4503 to 3501, CanvasParams(quarterTurns = 1).geometry(3501, 4503).outputSize())
        assertEquals(3501 to 4503, CanvasParams(quarterTurns = 2).geometry(3501, 4503).outputSize())
        for (ratio in CanvasRatio.entries) assertEquals(ratio, ratio.rotated().rotated())
    }

    @Test fun everyCropCornerStaysInsideTheSourceAfterPanZoomRotationAndFlip() {
        for ((w, h) in listOf(4000 to 3000, 301 to 803, 1 to 1, 1 to 200)) {
            for (ratio in CanvasRatio.entries) for (turns in 0..3) for (angle in listOf(-45.0, -11.7, 0.0, 23.4, 45.0)) {
                for (zoom in listOf(1.0, 2.3, 8.0)) for (mirror in listOf(false, true)) {
                    val g = CanvasParams(ratio, -10.0, 10.0, zoom, turns, angle, mirror).geometry(w, h)
                    for ((x, y) in listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0)) {
                        val p = g.sourcePoint(x, y)
                        assertTrue("$ratio $turns $angle: $p outside $w,$h", p.first in -1e-7..(w + 1e-7) && p.second in -1e-7..(h + 1e-7))
                    }
                }
            }
        }
    }

    @Test fun previewAndExportUseTheSameAffineMapAtDifferentRasterSizes() {
        for (ratio in CanvasRatio.entries) for (turns in 0..3) for (flip in listOf(false, true)) {
            val g = CanvasParams(ratio, 0.4, 0.6, 1.8, turns, 12.3, flip).geometry(4000, 3000)
            for ((iw, ih) in listOf(1600 to 1200, 733 to 550, 4000 to 3000)) {
                val matrix = g.matrixValues(iw, ih, 800.0, 600.0)
                for ((x, y) in listOf(0.0 to 0.0, 0.3 to 0.8, 0.5 to 0.5, 1.0 to 1.0)) {
                    val p = g.sourcePoint(x, y)
                    val px = p.first * iw / 4000
                    val py = p.second * ih / 3000
                    assertEquals(x * 800, matrix[0] * px + matrix[1] * py + matrix[2], 0.002)
                    assertEquals(y * 600, matrix[3] * px + matrix[4] * py + matrix[5], 0.002)
                }
            }
        }
    }

    @Test fun clockwiseQuarterTurnMapsTopLeftToTopRight() {
        val g = CanvasParams(quarterTurns = 1).geometry(4, 3)
        val m = g.matrixValues(4, 3, 3.0, 4.0)
        assertEquals(3f, m[2], 1e-6f)
        assertEquals(0f, m[5], 1e-6f)
        val mirrored = CanvasParams(mirrored = true).geometry(4, 3).matrixValues(4, 3, 4.0, 3.0)
        assertEquals(-1f, mirrored[0], 1e-6f)
        assertEquals(4f, mirrored[2], 1e-6f)
    }

    @Test fun pinchKeepsTheSourcePointUnderTheCentroid() {
        for (turns in 0..3) for (mirror in listOf(false, true)) {
            val p = CanvasParams(CanvasRatio.SQUARE, zoom = 2.0, quarterTurns = turns, mirrored = mirror)
            val oldPoint = p.geometry(4000, 3000).sourcePoint(0.7, 0.6)
            val next = p.transformed(4000, 3000, 1.25, 0.0, 0.0, 0.7, 0.6)
            val newPoint = next.geometry(4000, 3000).sourcePoint(0.7, 0.6)
            assertEquals(oldPoint.first, newPoint.first, 1e-7)
            assertEquals(oldPoint.second, newPoint.second, 1e-7)
        }
    }

    @Test fun panningBackFromAnEdgeHasNoAccumulatedOvershoot() {
        val p = CanvasParams(CanvasRatio.SQUARE, zoom = 2.0)
        val atEdge = p.transformed(4000, 3000, 1.0, 100.0, 100.0)
        val back = atEdge.transformed(4000, 3000, 1.0, -0.1, -0.1)
        assertTrue(back.centerX > atEdge.centerX)
        assertTrue(back.centerY > atEdge.centerY)
    }

    @Test fun zoomAndOutputSizesNeverUpscaleTheAvailableCrop() {
        val g = CanvasParams(CanvasRatio.STORY, zoom = 8.0, straighten = 17.0).geometry(4000, 3000)
        val full = g.outputSize()
        val small = g.outputSize(1000, 750)
        assertTrue(small.first <= full.first / 4 + 1)
        assertTrue(small.second <= full.second / 4 + 1)
        assertEquals(full, g.outputSize(8000, 6000))
        assertTrue(abs(full.first - full.second * 9.0 / 16) <= 1)
    }

    @Test fun invalidRestoredNumbersAreSanitized() {
        val p = CanvasParams(centerX = Double.NaN, centerY = Double.POSITIVE_INFINITY,
            zoom = Double.NaN, quarterTurns = -5, straighten = Double.NEGATIVE_INFINITY).sanitized()
        assertEquals(0.5, p.centerX, 0.0)
        assertEquals(0.5, p.centerY, 0.0)
        assertEquals(1.0, p.zoom, 0.0)
        assertEquals(0.0, p.straighten, 0.0)
        assertEquals(3, p.quarterTurns)
    }
}

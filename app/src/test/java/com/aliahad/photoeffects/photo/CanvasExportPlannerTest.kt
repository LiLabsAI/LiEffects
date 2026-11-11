package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.effect.EffectRegistry
import org.junit.Assert.*
import org.junit.Test

class CanvasExportPlannerTest {
    @Test fun storyHdKeepsIts720By1280DimensionsWhenMemoryAllows() {
        val plan = ExportPlanner.plan(4000, 3000, ExportOptions(ExportResolution.HD), null,
            512L * 1024 * 1024, CanvasParams(CanvasRatio.STORY))
        assertEquals(720, plan.width)
        assertEquals(1280, plan.height)
        assertTrue(plan.renderWidth >= 1707)
        assertEquals(1280, plan.renderHeight)
    }

    @Test fun cropAvailabilityAndDimensionsUseTheCropRatherThanTheUncroppedSource() {
        val canvas = CanvasParams(CanvasRatio.STORY)
        val options = ExportPlanner.resolutionOptions(4000, 3000, null, 1024L * 1024 * 1024, canvas)
        assertFalse(options.single { it.resolution == ExportResolution.FOUR_K }.enabled)
        val top = options.single { it.resolution == ExportResolution.FULL_HD }
        assertEquals(1687, top.width)
        assertEquals(3000, top.height)
        assertEquals(4000, top.renderWidth)
        assertEquals(3000, top.renderHeight)
    }

    @Test fun zoomedHeavyExportBudgetsTheWholeLookPlusTheDestination() {
        val effect = requireNotNull(EffectRegistry.byId("velour_1"))
        val memory = 512L * 1024 * 1024
        val canvas = CanvasParams(CanvasRatio.SQUARE, zoom = 8.0, straighten = 23.0)
        val plan = ExportPlanner.plan(12000, 9000, ExportOptions(), effect, memory, canvas)
        val maxPixels = ExportBudget.maxPixels(effect, memory, 12)
        assertTrue(plan.renderWidth.toLong() * plan.renderHeight <= maxPixels)
        assertTrue(plan.width.toLong() * plan.height < maxPixels / 20)
        assertEquals(canvas.geometry(12000, 9000).outputSize(plan.renderWidth, plan.renderHeight), plan.width to plan.height)
        assertEquals(ExportPlanner.estimateBytes(plan.width, plan.height, plan.quality), plan.estimatedBytes)
    }

    @Test fun smallRotatedSourcesRemainSaveableWithoutUpscaling() {
        val canvas = CanvasParams(quarterTurns = 1)
        val plan = ExportPlanner.plan(101, 57, ExportOptions(), null, canvas = canvas)
        assertEquals(57, plan.width)
        assertEquals(101, plan.height)
        assertEquals(101, plan.renderWidth)
        assertEquals(57, plan.renderHeight)
    }
}

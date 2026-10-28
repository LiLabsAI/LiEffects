package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.effect.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PREQUEL's export rules (recovered from `km7` / `dn7` / `il7`): tiers are only offered when
 * the source can fill them, the highest offered tier exports at source size, lower tiers use
 * their nominal long edge, quality maps to JPEG 95 / 75 / 40, and the estimate is
 * pixels × 0.35 / 0.18 / 0.09 bytes.
 */
class ExportPlannerTest {

    private val heap = 512L * 1024 * 1024

    @Test
    fun `portrait photo offers source-size full hd and nominal hd`() {
        val options = ExportPlanner.resolutionOptions(1440, 1920, null, heap)

        assertEquals(
            listOf(ExportResolution.HD, ExportResolution.FULL_HD, ExportResolution.FOUR_K),
            options.map { it.resolution },
        )
        val hd = options.single { it.resolution == ExportResolution.HD }
        assertEquals(960, hd.width)
        assertEquals(1280, hd.height)
        assertTrue(hd.enabled)

        val fullHd = options.single { it.resolution == ExportResolution.FULL_HD }
        assertEquals(1440, fullHd.width)
        assertEquals(1920, fullHd.height)
        assertTrue(fullHd.enabled)

        val fourK = options.single { it.resolution == ExportResolution.FOUR_K }
        assertFalse(fourK.enabled)
    }

    @Test
    fun `twelve megapixel photo offers 4k at source size`() {
        val options = ExportPlanner.resolutionOptions(4000, 3000, null, heap)
        val fourK = options.single { it.resolution == ExportResolution.FOUR_K }
        assertTrue(fourK.enabled)
        assertEquals(4000, fourK.width)
        assertEquals(3000, fourK.height)

        val fullHd = options.single { it.resolution == ExportResolution.FULL_HD }
        assertEquals(1920, fullHd.width)
        assertEquals(1440, fullHd.height)

        val hd = options.single { it.resolution == ExportResolution.HD }
        assertEquals(1280, hd.width)
        assertEquals(960, hd.height)
    }

    @Test
    fun `lower tier dimensions are even and follow the source aspect`() {
        val options = ExportPlanner.resolutionOptions(1600, 2398, null, heap)
        val hd = options.single { it.resolution == ExportResolution.HD }
        assertEquals(854, hd.width)
        assertEquals(1280, hd.height)

        val fullHd = options.single { it.resolution == ExportResolution.FULL_HD }
        assertEquals(1600, fullHd.width)
        assertEquals(2398, fullHd.height)
    }

    @Test
    fun `four k requires a full four k sensor`() {
        val justEnough = ExportPlanner.resolutionOptions(3840, 2160, null, heap)
        assertTrue(justEnough.single { it.resolution == ExportResolution.FOUR_K }.enabled)

        val tooSmall = ExportPlanner.resolutionOptions(3839, 2160, null, heap)
        assertFalse(tooSmall.single { it.resolution == ExportResolution.FOUR_K }.enabled)
    }

    @Test
    fun `full hd needs a 1080 short edge`() {
        val options = ExportPlanner.resolutionOptions(2000, 1000, null, heap)
        val fullHd = options.single { it.resolution == ExportResolution.FULL_HD }
        assertFalse(fullHd.enabled)

        val plan = ExportPlanner.plan(2000, 1000, ExportOptions(resolution = ExportResolution.FULL_HD), null, heap)
        assertEquals(ExportResolution.HD, plan.resolution)
        assertEquals(2000, plan.width)
        assertEquals(1000, plan.height)
    }

    @Test
    fun `source below hd exports at source size via full hd row`() {
        val options = ExportPlanner.resolutionOptions(640, 480, null, heap)
        assertTrue(options.single { it.resolution == ExportResolution.FULL_HD }.enabled)
        assertFalse(options.single { it.resolution == ExportResolution.HD }.enabled)
        assertFalse(options.single { it.resolution == ExportResolution.FOUR_K }.enabled)

        val plan = ExportPlanner.plan(640, 480, ExportOptions(), null, heap)
        assertEquals(ExportResolution.FULL_HD, plan.resolution)
        assertEquals(640, plan.width)
        assertEquals(480, plan.height)
    }

    @Test
    fun `requested tier unavailable falls back like prequel`() {
        val plan = ExportPlanner.plan(1440, 1920, ExportOptions(resolution = ExportResolution.FOUR_K), null, heap)
        assertEquals(ExportResolution.FULL_HD, plan.resolution)
        assertEquals(1440, plan.width)
        assertEquals(1920, plan.height)

        assertEquals(
            ExportResolution.FULL_HD,
            ExportPlanner.effectiveResolution(1440, 1920, ExportResolution.FOUR_K, null, heap),
        )
    }

    @Test
    fun `default export keeps the full twelve megapixels`() {
        val plan = ExportPlanner.plan(4000, 3000, ExportOptions(), null, heap)
        assertEquals(ExportResolution.FOUR_K, plan.resolution)
        assertEquals(4000, plan.width)
        assertEquals(3000, plan.height)
        assertEquals(ExportQuality.HIGH, plan.quality)
        assertEquals(ExportMetadataMode.OVERRIDE, plan.metadata)
    }

    @Test
    fun `budget caps the planned pixels for heavy effects`() {
        val sensual = requireNotNull(EffectRegistry.byId("sensual_1"))
        val maxPixels = ExportBudget.maxPixels(sensual, heap)
        val plan = ExportPlanner.plan(4000, 3000, ExportOptions(), sensual, heap)
        assertTrue(plan.width < 4000)
        assertTrue(
            "plan ${plan.width}x${plan.height} exceeds budget $maxPixels",
            plan.width.toLong() * plan.height <= (maxPixels * 1.01).toLong(),
        )

        val hd = ExportPlanner.resolutionOptions(4000, 3000, sensual, heap)
            .single { it.resolution == ExportResolution.HD }
        assertEquals(1280, hd.width)
        assertEquals(960, hd.height)
    }

    @Test
    fun `quality values match prequel jpeg qualities`() {
        assertEquals(40, ExportQuality.LOW.jpegQuality)
        assertEquals(75, ExportQuality.MEDIUM.jpegQuality)
        assertEquals(95, ExportQuality.HIGH.jpegQuality)
    }

    @Test
    fun `estimates use prequel coefficients and whole megabytes`() {
        assertEquals(967_680L, ExportPlanner.estimateBytes(1440, 1920, ExportQuality.HIGH))
        assertEquals(497_664L, ExportPlanner.estimateBytes(1440, 1920, ExportQuality.MEDIUM))
        assertEquals(248_832L, ExportPlanner.estimateBytes(1440, 1920, ExportQuality.LOW))

        assertEquals("~1 MB", ExportPlanner.formatEstimate(967_680L))
        assertEquals("~4 MB", ExportPlanner.formatEstimate(4_200_000L))
        assertEquals("~1 MB", ExportPlanner.formatEstimate(100L))
    }

    @Test
    fun `plan carries the chosen quality and metadata`() {
        val options = ExportOptions(
            resolution = ExportResolution.HD,
            quality = ExportQuality.LOW,
            metadata = ExportMetadataMode.KEEP,
        )
        val plan = ExportPlanner.plan(4000, 3000, options, null, heap)
        assertEquals(ExportResolution.HD, plan.resolution)
        assertEquals(1280, plan.width)
        assertEquals(960, plan.height)
        assertEquals(ExportQuality.LOW, plan.quality)
        assertEquals(ExportMetadataMode.KEEP, plan.metadata)
        assertEquals(ExportPlanner.estimateBytes(1280, 960, ExportQuality.LOW), plan.estimatedBytes)
    }
}

package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.PrismLutData
import com.aliahad.photoeffects.effect.cpu.PrismLutStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * JVM tests for the PRISM family (6 presets).
 *
 * The fitted targets come from full-resolution calibration fixtures of the bundled demo
 * photo, downscaled to the test size. Tests cover:
 *  - six fitted rows + measured Filter/Blur tool values
 *  - CPU contract: intensity-0 identity, determinism, no mutation
 *  - means (full image and 12%-inset interior) close to the reference exports
 *  - the six LUT atlas blocks are distinct and addressable
 *  - variant direction checks (#2 coolest, #4/#5 warmer than #3)
 *  - variant #1's ellipse soft focus blurs the frame edges but keeps the centre sharp
 */
class PrismEffectPipelineTest {

    private val fullTargets = listOf(
        Triple(89.9f, 101.7f, 106.4f),
        Triple(81.8f, 107.9f, 113.3f),
        Triple(90.2f, 104.3f, 108.2f),
        Triple(95.8f, 104.6f, 105.5f),
        Triple(99.3f, 108.7f, 109.0f),
        Triple(92.3f, 111.1f, 117.8f),
    )
    private val interiorTargets = listOf(
        Triple(95.6f, 97.8f, 99.0f),
        Triple(90.5f, 102.1f, 102.6f),
        Triple(95.9f, 98.6f, 99.0f),
        Triple(99.4f, 98.7f, 96.5f),
        Triple(104.5f, 104.2f, 101.3f),
        Triple(98.7f, 104.8f, 107.2f),
    )

    private var width = 0
    private var height = 0
    private var source = IntArray(0)

    @Before
    fun loadAtlasAndDemo() {
        PrismLutStore.clear()
        val atlasFile = File("src/main/assets/prism_luts.png")
        assertTrue("missing LUT atlas ${atlasFile.path}", atlasFile.exists())
        val atlas = ImageIO.read(atlasFile)
        assertEquals(PrismLutData.ATLAS_WIDTH, atlas.width)
        assertEquals(PrismLutData.ATLAS_HEIGHT, atlas.height)
        val atlasPixels = IntArray(atlas.width * atlas.height)
        atlas.getRGB(0, 0, atlas.width, atlas.height, atlasPixels, 0, atlas.width)
        for (params in PrismFamily.presets) {
            PrismLutStore.put(
                params.id,
                PrismLutData.fromAtlas(atlasPixels, atlas.width, params.lutIndex),
            )
        }

        val stream = javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg")
            ?: error("demo_photo.jpg missing from test resources")
        val original = ImageIO.read(stream)
        val scale = 360f / max(original.width, original.height)
        width = (original.width * scale).roundToInt()
        height = (original.height * scale).roundToInt()
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            drawImage(original, 0, 0, width, height, null)
            dispose()
        }
        source = IntArray(width * height)
        scaled.getRGB(0, 0, width, height, source, 0, width)
    }

    private fun render(effect: PhotoEffect, intensity: Float = 1f, seed: Long = 987654321L): IntArray =
        effect.cpu.apply(source, width, height, intensity, seed)

    private fun mean(pixels: IntArray): Triple<Float, Float, Float> {
        var r = 0L; var g = 0L; var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size.toFloat()
        return Triple(r / n, g / n, b / n)
    }

    private fun interiorMean(pixels: IntArray): Triple<Float, Float, Float> {
        val m = (width * 0.12f).toInt()
        val m2 = (height * 0.12f).toInt()
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (y in m2 until height - m2) {
            for (x in m until width - m) {
                val p = pixels[y * width + x]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        return Triple(r / n.toFloat(), g / n.toFloat(), b / n.toFloat())
    }

    private fun assertNear(
        expected: Triple<Float, Float, Float>,
        actual: Triple<Float, Float, Float>,
        tol: Float,
        label: String,
    ) {
        assertTrue("$label R expected $expected got $actual", abs(expected.first - actual.first) <= tol)
        assertTrue("$label G expected $expected got $actual", abs(expected.second - actual.second) <= tol)
        assertTrue("$label B expected $expected got $actual", abs(expected.third - actual.third) <= tol)
    }

    // ---------------------------------------------------------------- fitted rows

    @Test
    fun prismIsSixFittedRows() {
        assertEquals(6, EffectRegistry.prismPresetsCount)
        assertEquals(
            listOf("prism_1", "prism_2", "prism_3", "prism_4", "prism_5", "prism_6"),
            PrismFamily.effects.map { it.id },
        )
        assertEquals(
            listOf("Prism #1", "Prism #2", "Prism #3", "Prism #4", "Prism #5", "Prism #6"),
            PrismFamily.effects.map { it.displayName },
        )
        val p = PrismFamily.presets
        assertEquals(listOf(0, 1, 2, 3, 4, 5), p.map { it.lutIndex })
        assertEquals(listOf(true, false, false, false, false, false), p.map { it.ellipseBlur })
        assertEquals(listOf(100, 100, 100, 100, 100, 100), p.map { it.filter })
        assertEquals(listOf(100, 100, 100, 100, 100, 100), p.map { it.blur })
        assertEquals(listOf(100, 100, 100, 100, 100, 100), p.map { it.defaultIntensity })
        for (effect in PrismFamily.effects) {
            assertEquals(PrismFamily.ID, effect.spec.familyId)
            assertTrue("PRISM presets belong on FOR YOU", EffectCategory.ForYou in effect.spec.categories)
        }
    }

    // ---------------------------------------------------------------- CPU contract

    @Test
    fun intensityZeroIsExactCopy() {
        for (effect in PrismFamily.effects) {
            assertArrayEquals(
                "intensity 0 must copy ${effect.id}",
                source,
                render(effect, intensity = 0f),
            )
        }
    }

    @Test
    fun renderingIsDeterministicAndDoesNotMutateInput() {
        val before = source.copyOf()
        for (effect in PrismFamily.effects) {
            val a = render(effect)
            val b = render(effect)
            assertArrayEquals("determinism ${effect.id}", a, b)
        }
        assertArrayEquals("input must not be mutated", before, source)
    }

    // ---------------------------------------------------------------- calibration baselines

    @Test
    fun meansMatchReferenceExports() {
        PrismFamily.effects.forEachIndexed { i, effect ->
            val pixels = render(effect)
            assertNear(fullTargets[i], mean(pixels), 3f, "${effect.id} full mean")
            assertNear(interiorTargets[i], interiorMean(pixels), 3f, "${effect.id} interior mean")
        }
    }

    @Test
    fun variantsAreVisuallyDistinct() {
        val means = PrismFamily.effects.map { mean(render(it)) }
        // #2 is the cool one (blue over red); #4/#5 are warm relative to #3.
        assertTrue(
            "#2 should stay cooler than #1",
            means[1].third - means[1].first > means[0].third - means[0].first,
        )
        assertTrue(
            "#4 should be warmer than #3",
            means[3].first - means[3].third > means[2].first - means[2].third,
        )
        assertTrue(
            "#5 should be warmer than #3",
            means[4].first - means[4].third > means[2].first - means[2].third,
        )
        for (i in means.indices) {
            for (j in i + 1 until means.size) {
                val delta = abs(means[i].first - means[j].first) +
                    abs(means[i].second - means[j].second) +
                    abs(means[i].third - means[j].third)
                assertTrue("variants $i/$j should differ (delta $delta)", delta > 2f)
            }
        }
    }

    // ---------------------------------------------------------------- LUT atlas

    @Test
    fun lutAtlasHasSixDistinctVariantBlocks() {
        assertEquals(594, PrismLutData.ATLAS_WIDTH)
        assertEquals(396, PrismLutData.ATLAS_HEIGHT)
        val luts = PrismFamily.presets.map { assertNotNull(PrismLutStore.get(it.id)); PrismLutStore.get(it.id)!! }
        val probes = listOf(
            floatArrayOf(0.10f, 0.20f, 0.80f),
            floatArrayOf(0.50f, 0.50f, 0.50f),
            floatArrayOf(0.90f, 0.30f, 0.20f),
        )
        val a = FloatArray(3)
        val b = FloatArray(3)
        for (i in luts.indices) {
            for (j in i + 1 until luts.size) {
                var maxDelta = 0f
                for (probe in probes) {
                    luts[i].sample(probe[0], probe[1], probe[2], a)
                    luts[j].sample(probe[0], probe[1], probe[2], b)
                    for (c in 0..2) maxDelta = max(maxDelta, abs(a[c] - b[c]))
                }
                assertTrue("LUT blocks $i/$j should differ (delta $maxDelta)", maxDelta > 0.01f)
            }
        }
        // Identity fallback must be a no-op mapping.
        val identity = PrismLutData.identity()
        identity.sample(0.25f, 0.5f, 0.75f, a)
        assertEquals(0.25f, a[0], 0.005f)
        assertEquals(0.50f, a[1], 0.005f)
        assertEquals(0.75f, a[2], 0.005f)
    }

    // ---------------------------------------------------------------- #1 soft focus

    @Test
    fun softFocusBlursOutsideTheEllipseOnly() {
        // High-frequency checkerboard: lerp/blur attenuation is measurable per region.
        val w = 240
        val h = 360
        val checker = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val v = if (((x / 4) + (y / 4)) % 2 == 0) 225 else 25
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        fun inside(x: Int, y: Int): Boolean {
            val u = (x + 0.5f) / w
            val v = (y + 0.5f) / h
            val dx = (u - PrismParams.ELLIPSE_CX) / PrismParams.ELLIPSE_RX
            val dy = (v - PrismParams.ELLIPSE_CY) / PrismParams.ELLIPSE_RY
            return dx * dx + dy * dy < 0.8f * 0.8f
        }
        fun corner(x: Int, y: Int): Boolean = x < 0.15f * w && y < 0.10f * h
        fun hfEnergy(pixels: IntArray, region: (Int, Int) -> Boolean): Double {
            var sum = 0.0
            var n = 0
            for (y in 0 until h - 1) {
                for (x in 0 until w - 1) {
                    if (!region(x, y)) continue
                    val p = pixels[y * w + x]
                    val right = pixels[y * w + x + 1]
                    val down = pixels[(y + 1) * w + x]
                    sum += (kotlin.math.abs(lum(p) - lum(right)) + kotlin.math.abs(lum(p) - lum(down))).toDouble()
                    n++
                }
            }
            return sum / n
        }
        val out1 = PrismFamily.effects[0].cpu.apply(checker, w, h, 1f, 42L)
        val out2 = PrismFamily.effects[1].cpu.apply(checker, w, h, 1f, 42L)
        val center1 = hfEnergy(out1, ::inside)
        val corner1 = hfEnergy(out1, ::corner)
        val center2 = hfEnergy(out2, ::inside)
        val corner2 = hfEnergy(out2, ::corner)
        assertTrue(
            "#1 must soften the frame corners (corner/centre $corner1/$center1)",
            corner1 < center1 * 0.85,
        )
        assertTrue(
            "#1 must blur the edges more than the unblurred #2 (${corner1 / center1} vs ${corner2 / center2})",
            corner1 / center1 < (corner2 / center2) * 0.85,
        )
        assertTrue(
            "#1 must keep the ellipse centre sharp (centre $center1 vs #2 $center2)",
            abs(center1 - center2) / center2 < 0.15,
        )
    }

    private fun lum(p: Int): Int =
        ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
}

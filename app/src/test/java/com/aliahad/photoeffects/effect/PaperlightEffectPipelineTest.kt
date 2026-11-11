package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.CpuPaperlightRenderer
import com.aliahad.photoeffects.effect.cpu.PaperlightFrameData
import com.aliahad.photoeffects.effect.cpu.PaperlightFrameStore
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
 * JVM tests for the PAPERLIGHT family (5 presets).
 *
 * The fitted targets come from full-resolution calibration fixtures of the bundled demo
 * photo, downscaled to the test size. Tests cover:
 *  - five fitted rows + measured Filter/Grain/Frame tool values
 *  - CPU contract: intensity-0 identity, determinism, no mutation
 *  - means (full image and 12%-inset interior) close to the reference exports
 *  - the recovered frame overlays are present and cover the calibrated borders (white paper corner)
 *  - variant direction checks (cool #2 vs warm #4/#5)
 */
class PaperlightEffectPipelineTest {

    private val fullTargets = listOf(
        Triple(107.1f, 111.0f, 108.4f),
        Triple(104.5f, 110.0f, 121.8f),
        Triple(99.8f, 108.2f, 112.3f),
        Triple(112.8f, 116.8f, 109.3f),
        Triple(111.1f, 110.2f, 106.9f),
    )
    private val interiorTargets = listOf(
        Triple(108.9f, 106.3f, 101.5f),
        Triple(101.8f, 100.8f, 110.8f),
        Triple(98.4f, 97.4f, 98.5f),
        Triple(115.3f, 113.0f, 103.5f),
        Triple(114.8f, 107.0f, 101.8f),
    )

    private var width = 0
    private var height = 0
    private var source = IntArray(0)

    @Before
    fun loadFramesAndDemo() {
        PaperlightFrameStore.clear()
        for (params in PaperlightFamily.presets) {
            val file = File("src/main/assets/${params.frameAsset}")
            assertTrue("missing frame asset ${params.frameAsset}", file.exists())
            val img = ImageIO.read(file)
            val scale = 720f / max(img.width, img.height)
            val w = (img.width * scale).roundToInt()
            val h = (img.height * scale).roundToInt()
            val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            scaled.createGraphics().apply {
                drawImage(img, 0, 0, w, h, null)
                dispose()
            }
            val pixels = IntArray(w * h)
            scaled.getRGB(0, 0, w, h, pixels, 0, w)
            PaperlightFrameStore.put(params.frameAsset, PaperlightFrameData(pixels, w, h))
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

    private fun assertNear(expected: Triple<Float, Float, Float>, actual: Triple<Float, Float, Float>, tol: Float, label: String) {
        assertTrue("$label R expected $expected got $actual", abs(expected.first - actual.first) <= tol)
        assertTrue("$label G expected $expected got $actual", abs(expected.second - actual.second) <= tol)
        assertTrue("$label B expected $expected got $actual", abs(expected.third - actual.third) <= tol)
    }

    // ---------------------------------------------------------------- fitted rows

    @Test
    fun paperlightIsFiveFittedRows() {
        assertEquals(5, EffectRegistry.paperlightPresetsCount)
        assertEquals(
            listOf("paperlight_1", "paperlight_2", "paperlight_3", "paperlight_4", "paperlight_5"),
            PaperlightFamily.effects.map { it.id },
        )
        assertEquals(
            listOf("Paperlight #1", "Paperlight #2", "Paperlight #3", "Paperlight #4", "Paperlight #5"),
            PaperlightFamily.effects.map { it.displayName },
        )
        val p = PaperlightFamily.presets
        assertEquals(listOf(100, 100, 100, 100, 100), p.map { it.filter })
        assertEquals(listOf(100, 100, 100, 100, 80), p.map { it.grain })
        assertEquals(listOf(true, true, true, true, true), p.map { it.frame })
        assertEquals(
            listOf("paperlight_frame_1.png", "paperlight_frame_2.png", "paperlight_frame_3.png", "paperlight_frame_4.png", "paperlight_frame_5.png"),
            p.map { it.frameAsset },
        )
        assertEquals(setOf(501, 502, 503, 504, 505), p.map { it.seed }.toSet())
    }

    // ---------------------------------------------------------------- CPU contract

    @Test
    fun intensityZeroIsExactCopy() {
        for (effect in PaperlightFamily.effects) {
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
        for (effect in PaperlightFamily.effects) {
            val a = render(effect)
            val b = render(effect)
            assertArrayEquals("determinism ${effect.id}", a, b)
        }
        assertArrayEquals("input must not be mutated", before, source)
    }

    // ---------------------------------------------------------------- calibration baselines

    @Test
    fun meansMatchReferenceExports() {
        PaperlightFamily.effects.forEachIndexed { i, effect ->
            val pixels = render(effect)
            assertNear(fullTargets[i], mean(pixels), 5f, "${effect.id} full mean")
            assertNear(interiorTargets[i], interiorMean(pixels), 5f, "${effect.id} interior mean")
        }
    }

    @Test
    fun variantsAreVisuallyDistinct() {
        val means = PaperlightFamily.effects.map { mean(render(it)) }
        // #2 is the cool one (blue-heavy), #4/#5 are the warm ones (red-heavy).
        assertTrue("#2 should stay cooler than #1", means[1].third - means[1].first > means[0].third - means[0].first)
        assertTrue("#4 should be warmer than #3", means[3].first - means[3].third > means[2].first - means[2].third)
        assertTrue("#5 should be warmer than #3", means[4].first - means[4].third > means[2].first - means[2].third)
        for (i in means.indices) {
            for (j in i + 1 until means.size) {
                val delta = abs(means[i].first - means[j].first) +
                    abs(means[i].second - means[j].second) +
                    abs(means[i].third - means[j].third)
                assertTrue("variants $i/$j should differ (delta $delta)", delta > 2f)
            }
        }
    }

    // ---------------------------------------------------------------- frame overlay

    @Test
    fun framesCoverTheSameBordersAsReference() {
        for (params in PaperlightFamily.presets) {
            val frame = PaperlightFrameStore.get(params.frameAsset)
            assertNotNull("frame ${params.frameAsset} must be loaded", frame)
            var opaque = 0
            for (p in frame!!.pixels) if ((p ushr 24) and 0xFF == 255) opaque++
            val coverage = opaque.toFloat() / frame.pixels.size
            assertTrue("frame ${params.frameAsset} coverage $coverage", coverage in 0.03f..0.15f)
            // Interior must stay transparent; only the border band is covered.
            val centre = frame.pixels[(frame.height / 2) * frame.width + frame.width / 2]
            assertEquals("frame ${params.frameAsset} centre must be transparent", 0, centre)
        }
    }

    @Test
    fun framePaintsTheCornerLikeReference() {
        val rendered = render(PaperlightFamily.effects[0])
        val corner = rendered[2 * width + 2]
        val r = (corner shr 16) and 0xFF
        val g = (corner shr 8) and 0xFF
        val b = corner and 0xFF
        val white = r > 225 && g > 225 && b > 220
        val ink = r < 40 && g < 40 && b < 40
        assertTrue("top-left corner should be paper or frame ink, got ($r,$g,$b)", white || ink)
        val origin = rendered[0]
        assertTrue(
            "pixel (0,0) should be the white paper edge",
            (origin shr 16) and 0xFF > 225 && (origin shr 8) and 0xFF > 225 && origin and 0xFF > 220,
        )
        // And the very interior must not be a frame pixel.
        val centre = rendered[(height / 2) * width + width / 2]
        assertTrue("centre should not be pure paper", centre and 0xFFFFFF != 0xFFFFFF)
    }
}

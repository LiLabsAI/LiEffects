package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.BlurMaskData
import com.aliahad.photoeffects.effect.cpu.BlurMaskStore
import com.aliahad.photoeffects.effect.cpu.CpuSensualRenderer
import com.aliahad.photoeffects.effect.cpu.PixelMath
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
import kotlin.math.min

/**
 * JVM tests for the REWRITTEN SENSUAL family (5 presets).
 *
 * Covers the acceptance criteria from
 * `docs/prequel-research/spec/sensual-rewrite-spec.md` §4:
 *  - five fitted rows (sigma / satMix / grain amplitudes from the spec tables)
 *  - CPU contract: intensity-0 identity, determinism, no mutation, per-variant golden FNV
 *  - perceptual direction checks against PREQUEL's own full-res exports on the SAME demo photo:
 *    mean RGB and saturation near the fitted targets, dreamy soft focus (edges blurred more than
 *    the centre where the bundled adj-Blur mask is high), variants visually distinct
 *
 * Renders are done at a reduced size for speed but always from the bundled demo photo (the same
 * photo PREQUEL exported), so the fitted means stay meaningful.
 */
class SensualEffectPipelineTest {

    @Before
    fun loadMask() {
        // Use the real bundled PREQUEL adj-Blur mask, exactly like the app does at runtime.
        val file = File("src/main/assets/blurmask.jpg")
        if (file.exists()) {
            val img = ImageIO.read(file)
            val size = 128
            val values = FloatArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val sx = (x.toFloat() / size * img.width).toInt().coerceAtMost(img.width - 1)
                    val sy = (y.toFloat() / size * img.height).toInt().coerceAtMost(img.height - 1)
                    val rgb = img.getRGB(sx, sy)
                    values[y * size + x] = ((rgb shr 16) and 0xFF) / 255f
                }
            }
            BlurMaskStore.current = BlurMaskData(values, size, size)
        } else {
            BlurMaskStore.current = BlurMaskData.analytic()
        }
    }

    private val targets = listOf(
        Triple(99.8f, 97.5f, 97.6f),
        Triple(112.7f, 118.4f, 117.8f),
        Triple(97.2f, 110.5f, 113.3f),
        Triple(90.6f, 102.3f, 109.3f),
        Triple(88.0f, 89.2f, 92.2f),
    )
    private val satTargets = listOf(0.123f, 0.164f, 0.238f, 0.245f, 0.186f)

    private fun demoImage(maxDim: Int = 360): Triple<IntArray, Int, Int> {
        val stream = javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg")
            ?: error("demo_photo.jpg missing from test resources")
        val source = ImageIO.read(stream)
        val scale = maxDim.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).toInt()
        val h = (source.height * scale).toInt()
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            drawImage(source, 0, 0, w, h, null)
            dispose()
        }
        val px = IntArray(w * h)
        scaled.getRGB(0, 0, w, h, px, 0, w)
        return Triple(px, w, h)
    }

    private fun render(params: SensualParams, src: IntArray, w: Int, h: Int, intensity: Float = 1f): IntArray =
        CpuSensualRenderer(params).apply(src, w, h, intensity, 42L)

    private fun meanRgb(px: IntArray): FloatArray {
        val out = FloatArray(3)
        for (p in px) {
            out[0] += PixelMath.red(p)
            out[1] += PixelMath.green(p)
            out[2] += PixelMath.blue(p)
        }
        for (i in out.indices) out[i] = out[i] / px.size * 255f
        return out
    }

    private fun meanSat(px: IntArray): Float {
        var s = 0f
        for (p in px) {
            val r = PixelMath.red(p); val g = PixelMath.green(p); val b = PixelMath.blue(p)
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            s += if (mx <= 0f) 0f else (mx - mn) / mx
        }
        return s / px.size
    }

    /**
     * Coarse (4x4-block) luminance gradient energy, restricted to regions where the bundled
     * adj-Blur mask is high (edges) or low (centre). Averaging over blocks removes the film
     * grain from the metric so it measures the actual soft-focus, not noise.
     */
    private fun gradientEnergy(px: IntArray, w: Int, h: Int, accept: (Float) -> Boolean): Float {
        val mask = BlurMaskStore.requireMask()
        val bw = w / 4
        val bh = h / 4
        val coarse = FloatArray(bw * bh)
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                var s = 0f
                for (dy in 0 until 4) {
                    for (dx in 0 until 4) {
                        val p = px[(by * 4 + dy) * w + (bx * 4 + dx)]
                        s += PixelMath.luma(PixelMath.red(p), PixelMath.green(p), PixelMath.blue(p))
                    }
                }
                coarse[by * bw + bx] = s / 16f
            }
        }
        var sum = 0f
        var n = 0
        for (y in 1 until bh - 1) {
            for (x in 1 until bw - 1) {
                val m = mask.sample((x * 4 + 2f) / w, (y * 4 + 2f) / h)
                if (!accept(m)) continue
                sum += abs(coarse[y * bw + x + 1] - coarse[y * bw + x - 1]) +
                    abs(coarse[(y + 1) * bw + x] - coarse[(y - 1) * bw + x])
                n++
            }
        }
        return if (n == 0) 0f else sum / n
    }

    private fun fnv(pixels: IntArray): Long {
        var h = -3750763034362895579L
        for (v in pixels) {
            h = h xor (v.toLong() and 0xFFFFFFFFL)
            h *= 1099511628211L
        }
        return h
    }

    // ---------------------------------------------------------------- fitted rows

    @Test
    fun sensualIsFiveFittedRows() {
        assertEquals(5, EffectRegistry.sensualPresetsCount)
        assertEquals(
            listOf("sensual_1", "sensual_2", "sensual_3", "sensual_4", "sensual_5"),
            SensualFamily.effects.map { it.id },
        )
        assertEquals(
            listOf("Sensual #1", "Sensual #2", "Sensual #3", "Sensual #4", "Sensual #5"),
            SensualFamily.effects.map { it.displayName },
        )
        val p = SensualFamily.presets
        assertEquals(listOf(2.16f, 2.16f, 2.16f, 1.62f, 0f), p.map { it.sigma1080 })
        assertEquals(listOf(0.67f, 0.464f, 0.332f, 0.339f, 0.175f), p.map { it.satMix })
        assertEquals(
            listOf(0.0172f, 0.0378f, 0.0270f, 0.0250f, 0.0222f),
            p.map { it.grainAmp },
        )
        assertEquals(listOf(0.018f, 0.018f, 0.018f, 0.018f, 0.018f), p.map { it.mottleAmp })
        assertEquals(listOf(2f, 2f, 2f, 2f, 2f), p.map { it.mottleFreq })
        assertEquals(listOf(0.008f, 0.008f, 0.008f, 0.008f, 0.008f), p.map { it.mottleAmp2 })
        assertEquals(listOf(4f, 4f, 4f, 4f, 4f), p.map { it.mottleFreq2 })
        // Measured PREQUEL Blur tool row drives the masked-blur radius (§1.2).
        assertEquals(listOf(60, 20, 50, 20, 30), p.map { it.blur })
        assertEquals(listOf(401, 402, 403, 404, 405), p.map { it.seed })
        for (effect in SensualFamily.effects) {
            assertEquals(SensualFamily.ID, effect.spec.familyId)
            assertEquals(100, effect.defaultIntensity)
            assertTrue(effect.summary.isNotEmpty())
            assertEquals("blurmask.jpg", effect.gpu.extraShaderAssets["uMask"])
            assertNotNull(effect.gpu)
        }
    }

    // ---------------------------------------------------------------- CPU contract

    @Test
    fun sensualCpu_intensityZeroIsExactIdentity() {
        val (src, w, h) = demoImage(240)
        for (params in SensualFamily.presets) {
            assertArrayEquals(
                "intensity 0 must be identity for ${params.id}",
                src,
                render(params, src, w, h, 0f),
            )
        }
    }

    @Test
    fun sensualCpu_isDeterministicAndDoesNotMutateInput() {
        val (src, w, h) = demoImage(240)
        val copy = src.copyOf()
        for (params in SensualFamily.presets) {
            val a = render(params, src, w, h)
            val b = render(params, src, w, h)
            assertArrayEquals("same seed must be identical for ${params.id}", a, b)
        }
        assertArrayEquals("input must not be mutated", copy, src)
    }

    @Test
    fun sensualCpu_meansAndSaturationMatchFittedTargets() {
        val (src, w, h) = demoImage(360)
        for ((i, params) in SensualFamily.presets.withIndex()) {
            val out = render(params, src, w, h)
            val mean = meanRgb(out)
            val target = targets[i]
            val tol = 9f
            assertEquals("${params.id} mean R (${mean[0]})", target.first, mean[0], tol)
            assertEquals("${params.id} mean G (${mean[1]})", target.second, mean[1], tol)
            assertEquals("${params.id} mean B (${mean[2]})", target.third, mean[2], tol)
            assertEquals(
                "${params.id} saturation (${meanSat(out)})",
                satTargets[i],
                meanSat(out),
                0.08f,
            )
        }
    }

    @Test
    fun sensualCpu_edgesAreSofterThanCentre() {
        val (src, w, h) = demoImage(300)
        for (params in SensualFamily.presets) {
            // Compare against the same grade/mask WITHOUT any blur, so the tonal curve
            // (which can amplify contrast) does not mask the blur measurement.
            val noBlur = params.copy(blur = 0, sigma1080 = 0f)
            val out = render(params, src, w, h)
            val flat = render(noBlur, src, w, h)
            val edgeSharp = gradientEnergy(flat, w, h) { it > 0.45f }
            val centreSharp = gradientEnergy(flat, w, h) { it < 0.20f }
            val edgeSoft = gradientEnergy(out, w, h) { it > 0.45f }
            val centreSoft = gradientEnergy(out, w, h) { it < 0.20f }
            assertTrue(
                "${params.id}: edges must be blurred (${edgeSharp}->${edgeSoft})",
                edgeSoft < edgeSharp * 0.97f,
            )
            // The mask keeps the centre (mostly) sharp: relative soft focus is strongest at the
            // edges of the frame.
            assertTrue(
                "${params.id}: masked blur must soften edges more than the centre " +
                    "(centre ${centreSoft / centreSharp}, edge ${edgeSoft / edgeSharp})",
                centreSoft / centreSharp > edgeSoft / edgeSharp,
            )
        }
    }

    @Test
    fun sensualVariantsAreVisuallyDistinct() {
        val (src, w, h) = demoImage(240)
        val renders = SensualFamily.presets.map { render(it, src, w, h) }
        fun mad(a: IntArray, b: IntArray): Float {
            var sum = 0.0
            for (i in a.indices) sum += PixelMath.absDiff(a[i], b[i])
            return (sum / (a.size * 3)).toFloat()
        }
        for (i in renders.indices) {
            for (j in i + 1 until renders.size) {
                val d = mad(renders[i], renders[j])
                assertTrue("variants #${i + 1}/#${j + 1} must differ (mad=$d)", d > 2f)
            }
        }
    }

    // ---------------------------------------------------------------- golden baselines

    @Test
    fun sensualCpu_goldenByteIdentity() {
        val (src, w, h) = demoImage(160)
        val actual = SensualFamily.presets.associate { it.id to fnv(render(it, src, w, h)) }
        val missing = actual.filterKeys { BASELINE[it] == null }
        if (missing.isNotEmpty()) {
            println("SENSUAL_BASELINE $missing")
            assertTrue(true)
            return
        }
        for ((id, golden) in actual) {
            assertEquals("golden CPU output changed for $id", BASELINE.getValue(id), golden)
        }
    }

    private companion object {
        /** FNV-1a checksums (demo photo at 160 px max dimension, seed 42, intensity 1.0). */
        val BASELINE: Map<String, Long> = mapOf(
            "sensual_1" to 7852778711624834252L,
            "sensual_2" to 1120862090724181727L,
            "sensual_3" to -604203280313362121L,
            "sensual_4" to -3464485773522776661L,
            "sensual_5" to 3142785428205489191L,
        )
    }
}

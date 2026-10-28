package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.FocalLutData
import com.aliahad.photoeffects.effect.cpu.FocalLutStore
import com.aliahad.photoeffects.effect.cpu.PixelMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * JVM tests for the deterministic effect pipelines (no Android runtime required).
 *
 * Covers both families:
 *  - intensity 0 is a pixel-exact identity (CPU)
 *  - same inputs + seed => identical output (determinism, incl. row-parallel executor)
 *  - more intensity => larger mean absolute difference from the original
 *  - alpha is preserved; input is never mutated; noise helpers are deterministic and bounded
 *  - dusty preset table guards + golden byte-identity regression (refactors must not change pixels)
 *  - family/shelf wiring (DUSTY 5 presets, HALATION 3 presets, category shelves non-empty)
 */
class DustyEffectPipelineTest {

    private val width = 96
    private val height = 64

    /** FOCAL presets render through their bundled LUT atlas; inject it for every test. */
    @Before
    fun loadFocalLuts() {
        FocalLutStore.clear()
        val atlas = ImageIO.read(File("src/main/assets/focal_luts.png"))
        val pixels = IntArray(atlas.width * atlas.height)
        atlas.getRGB(0, 0, atlas.width, atlas.height, pixels, 0, atlas.width)
        for (params in FocalFamily.presets) {
            FocalLutStore.put(
                params.id,
                FocalLutData.fromAtlas(pixels, atlas.width, params.lutIndex),
            )
        }
    }

    /** Deterministic synthetic "photo": gradients + saturated patches + skin-tone-ish block. */
    private fun testImage(): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val r = (x * 255 / (width - 1))
        val g = (y * 255 / (height - 1))
        val b = ((x + y) * 255 / (width + height - 2))
        val block = if (x in 30..60 && y in 20..44) 1 else 0
        val rr = if (block == 1) (200 + x / 4).coerceAtMost(255) else r
        val gg = if (block == 1) (150 + y / 4).coerceAtMost(255) else g
        val bb = if (block == 1) (120 + (x + y) / 8).coerceAtMost(255) else b
        (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun meanAbsDiff(a: IntArray, b: IntArray): Float {
        var sum = 0L
        for (i in a.indices) sum += PixelMath.absDiff(a[i], b[i])
        return sum.toFloat() / (a.size * 3)
    }

    private fun fnv(pixels: IntArray): Long {
        var h = -3750763034362895579L
        for (v in pixels) {
            h = h xor (v.toLong() and 0xFFFFFFFFL)
            h *= 1099511628211L
        }
        return h
    }

    @Test
    fun everyPreset_isExactlyIdentityAtIntensityZero() {
        val src = testImage()
        for (effect in EffectRegistry.all) {
            val out = effect.cpu.apply(src, width, height, 0f, seed = 42L)
            assertArrayEquals("intensity 0 must be a pixel-exact copy for ${effect.id}", src, out)
        }
    }

    @Test
    fun cpuRenderer_isDeterministic() {
        val src = testImage()
        for (effect in EffectRegistry.all) {
            val a = effect.cpu.apply(src, width, height, 1f, seed = 7L)
            val b = effect.cpu.apply(src, width, height, 1f, seed = 7L)
            assertArrayEquals("same seed must produce identical output for ${effect.id}", a, b)
        }
    }

    @Test
    fun differentSeeds_changeTheNoisePattern() {
        val src = testImage()
        val dustiest = EffectRegistry.byId("dusty_5")!!
        val a = dustiest.cpu.apply(src, width, height, 1f, seed = 1L)
        val b = dustiest.cpu.apply(src, width, height, 1f, seed = 999999L)
        assertNotEquals("dust/grain must depend on the seed", meanAbsDiff(a, b), 0f)

        val halation = EffectRegistry.byId("halation_2")!!
        val c = halation.cpu.apply(src, width, height, 1f, seed = 1L)
        val d = halation.cpu.apply(src, width, height, 1f, seed = 999999L)
        assertNotEquals("halation grain must depend on the seed", meanAbsDiff(c, d), 0f)
    }

    @Test
    fun moreIntensity_meansLargerDivergence() {
        val src = testImage()
        for (effect in EffectRegistry.all) {
            val d0 = meanAbsDiff(src, effect.cpu.apply(src, width, height, 0f, seed = 3L))
            val d25 = meanAbsDiff(src, effect.cpu.apply(src, width, height, 0.25f, seed = 3L))
            val d50 = meanAbsDiff(src, effect.cpu.apply(src, width, height, 0.5f, seed = 3L))
            val d100 = meanAbsDiff(src, effect.cpu.apply(src, width, height, 1f, seed = 3L))
            assertEquals("intensity 0 must not change pixels for ${effect.id}", 0f, d0, 0.0001f)
            assertTrue(
                "divergence should grow with intensity for ${effect.id}: $d25 < $d50 < $d100",
                d25 < d50 && d50 < d100,
            )
        }
    }

    @Test
    fun alphaIsPreserved() {
        val src = testImage().copyOf()
        for (i in src.indices step 7) {
            src[i] = (src[i] and 0x00FFFFFF) or (0x80 shl 24)
        }
        for (effect in EffectRegistry.all) {
            val out = effect.cpu.apply(src, width, height, 1f, seed = 11L)
            for (i in src.indices) {
                assertEquals(
                    "alpha channel must be preserved for ${effect.id}",
                    (src[i] ushr 24) and 0xFF,
                    (out[i] ushr 24) and 0xFF,
                )
            }
        }
    }

    @Test
    fun inputBufferIsNotMutated() {
        val src = testImage()
        val copy = src.copyOf()
        for (effect in EffectRegistry.all) {
            effect.cpu.apply(src, width, height, 1f, seed = 5L)
        }
        assertArrayEquals("CPU renderers must not mutate their input", copy, src)
    }

    @Test
    fun noiseHelpers_areDeterministicAndBounded() {
        for (i in 0 until 500) {
            val x = (i * 0.6137f) - 40f
            val y = (i * 0.2711f) - 20f
            val a = PixelMath.fbm4(x, y, 123)
            val b = PixelMath.fbm4(x, y, 123)
            assertEquals("fbm4 must be deterministic", a, b, 0f)
            assertTrue("fbm4 must stay within 0..1 (got $a)", a in 0f..1f)
            val h1 = PixelMath.hashF(i, i * 3, 7)
            val h2 = PixelMath.hashF(i, i * 3, 7)
            assertEquals("hashF must be deterministic", h1, h2, 0f)
            assertTrue("hashF must stay within 0..1", h1 in 0f..1f)
            // Adjacent per-variant seeds (101..105 / 201..203) must not collapse.
            assertNotEquals(
                "integer seeds must change the pattern",
                PixelMath.hashF(17, 23, 101),
                PixelMath.hashF(17, 23, 102),
            )
        }
        assertNotEquals(
            "different seeds must produce different noise",
            PixelMath.fbm2(10.5f, 20.5f, 1),
            PixelMath.fbm2(10.5f, 20.5f, 2),
        )
    }

    @Test
    fun latticeHash_isVariedAtDevicePixelCoordinates() {
        // Large lattice indices (device pixel scale) must stay decorrelated - guards the
        // float-precision banding bug that was fixed by the sin-hash + scaled lattice coords.
        val values = (0 until 256).map { i ->
            PixelMath.hashF(700 + (i % 16), 1200 + (i / 16), 105)
        }
        assertTrue("lattice hash must produce varied values", values.toSet().size > 200)
        val a = PixelMath.hashF(500, 900, 105)
        val b = PixelMath.hashF(700, 900, 105)
        assertNotEquals("adjacent lattice cells must differ", a, b)
        assertTrue("hash must stay within 0..1", a in 0f..1f && b in 0f..1f)
    }

    @Test
    fun dustyPresets_haveDistinctParametersAndMetadata() {
        assertEquals(5, EffectRegistry.dustyPresetsCount)
        assertEquals(5, DustyFamily.presets.size)
        val ids = DustyFamily.effects.map { it.id }
        assertEquals(listOf("dusty_1", "dusty_2", "dusty_3", "dusty_4", "dusty_5"), ids)
        assertEquals(
            listOf("Dusty #1", "Dusty #2", "Dusty #3", "Dusty #4", "Dusty #5"),
            DustyFamily.effects.map { it.displayName },
        )
        for (effect in DustyFamily.effects) {
            assertEquals(100, effect.defaultIntensity)
            assertEquals(DustyFamily.ID, effect.spec.familyId)
            assertTrue(effect.summary.isNotEmpty())
        }
        // All five look directions must be visibly distinct: no two presets share a parameter row.
        val signatures = DustyFamily.presets.map { it.copy(description = "", displayName = "") }
        assertEquals("presets must differ in parameters", 5, signatures.toSet().size)
    }

    @Test
    fun dustyPresetTable_matchesFittedGradeDirections() {
        // Guards the fitted PREQUEL grade: a neutral gray must take each variant's measured cast.
        fun neutralGrade(p: DustyParams): FloatArray {
            val g = p.grade
            assertEquals("grade must be a 3x4 matrix", 12, g.size)
            val v = 0.5f
            return floatArrayOf(
                v * g[0] + v * g[1] + v * g[2] + g[3],
                v * g[4] + v * g[5] + v * g[6] + g[7],
                v * g[8] + v * g[9] + v * g[10] + g[11],
            )
        }
        val p1 = DustyFamily.presets[0]
        val p2 = DustyFamily.presets[1]
        val p3 = DustyFamily.presets[2]
        val p4 = DustyFamily.presets[3]
        val p5 = DustyFamily.presets[4]
        val n1 = neutralGrade(p1)
        val n2 = neutralGrade(p2)
        val n3 = neutralGrade(p3)
        val n4 = neutralGrade(p4)
        val n5 = neutralGrade(p5)
        assertTrue("Dusty #1 should be teal (B > G > R), got ${n1.joinToString()}", n1[2] > n1[1] && n1[1] > n1[0])
        assertTrue("Dusty #2 should be strongly blue (B > G, R), got ${n2.joinToString()}", n2[2] - maxOf(n2[0], n2[1]) > 0.05f)
        assertTrue("Dusty #3 should be green/olive (G > B > R), got ${n3.joinToString()}", n3[1] > n3[2] && n3[2] > n3[0])
        assertTrue("Dusty #4 should be warm (R > G > B), got ${n4.joinToString()}", n4[0] > n4[1] && n4[1] > n4[2])
        assertTrue("Dusty #5 should be warm-brown (R > G > B), got ${n5.joinToString()}", n5[0] > n5[1] && n5[1] > n5[2])
        assertTrue("Dusty #2 should show RGB fringing", p2.aberration >= 0.3f)
        assertTrue("Dusty #4 should have strong halation", p4.halation > 0.5f)
        assertTrue("Dusty #5 should have heavy grain", p5.grain > 0.7f)
        assertTrue("Dusty #5 should vignette more than Dusty #1", p5.vignette > p1.vignette)
        assertTrue("Dusty #1 should have white dust", p1.dustWhite)
        assertEquals("dusty preset seeds are fixed", setOf(101, 102, 103, 104, 105), DustyFamily.presets.map { it.seed }.toSet())
        assertTrue(abs(p1.seed - 101) == 0 && abs(p5.seed - 105) == 0)
    }

    @Test
    fun halationFamily_isWiredWithMeasuredRows() {
        assertEquals(3, EffectRegistry.halationPresetsCount)
        assertEquals(3, HalationFamily.presets.size)
        assertEquals(
            listOf("halation_1", "halation_2", "halation_3"),
            HalationFamily.effects.map { it.id },
        )
        assertEquals(
            listOf("Halation #1", "Halation #2", "Halation #3"),
            HalationFamily.effects.map { it.displayName },
        )
        for (effect in HalationFamily.effects) {
            assertEquals(HalationFamily.ID, effect.spec.familyId)
            assertTrue("halation default intensity must be a valid slider value", effect.defaultIntensity in 0..100)
            assertTrue("halation summary must not be empty", effect.summary.isNotEmpty())
            assertNotNull("halation must expose a GPU program", effect.gpu)
        }
        // Measured rows must stay distinct parameter rows (guards the tuning baseline).
        val signatures = HalationFamily.presets.map { it.copy(description = "", displayName = "") }
        assertEquals(3, signatures.toSet().size)
        // Exact spec §3 rows: measured PREQUEL tool values.
        val h1 = HalationFamily.presets[0]
        val h2 = HalationFamily.presets[1]
        val h3 = HalationFamily.presets[2]
        assertEquals(listOf(0.00f, 0.50f, 0.70f), listOf(h1.hue, h2.hue, h3.hue))
        assertEquals(listOf(65, 60, 65), listOf(h1.defaultIntensity, h2.defaultIntensity, h3.defaultIntensity))
        assertEquals(listOf(0.80f, 0.80f, 0.80f), listOf(h1.opacity, h2.opacity, h3.opacity))
        assertEquals(listOf(0.30f, 0.30f, 0.40f), listOf(h1.blur, h2.blur, h3.blur))
        assertEquals(listOf(0.35f, 0.35f, 0.35f), listOf(h1.grain, h2.grain, h3.grain))
        assertEquals(listOf(0.82f, 0.86f, 0.80f), listOf(h1.brightThreshold, h2.brightThreshold, h3.brightThreshold))
        assertEquals(listOf(0.10f, 0.12f, 0.10f), listOf(h1.softKnee, h2.softKnee, h3.softKnee))
        assertEquals(listOf(0.03f, 0.015f, 0.03f), listOf(h1.haze, h2.haze, h3.haze))
        assertEquals(listOf(0.06f, 0.02f, -0.03f), listOf(h1.warmth, h2.warmth, h3.warmth))
        assertEquals(listOf(0.10f, 0.08f, 0.12f), listOf(h1.vignette, h2.vignette, h3.vignette))
        assertEquals(
            "per-variant halo gain must stay at the fitted values",
            listOf(7f, 11f, 12f),
            listOf(h1.haloGain, h2.haloGain, h3.haloGain),
        )
        assertEquals(
            "halation preset seeds are fixed",
            setOf(201, 202, 203),
            HalationFamily.presets.map { it.seed }.toSet(),
        )
    }

    @Test
    fun shelves_areFamilyFirstThenCategories() {
        assertEquals(
            listOf(
                "Dusty", "Halation", "Yesterday", "Sensual", "Kuji", "Focal",
                "ForYou", "Trends", "New", "Films",
            ),
            EffectRegistry.shelves.map { it.id },
        )
        assertEquals("DUSTY", EffectRegistry.defaultShelf.title)
        assertEquals(5, EffectRegistry.defaultShelf.effects.size)
        assertEquals(3, EffectRegistry.shelves[1].effects.size)
        assertEquals(1, EffectRegistry.shelves[2].effects.size) // YESTERDAY = single preset
        assertEquals(5, EffectRegistry.shelves[3].effects.size)
        assertEquals(5, EffectRegistry.shelves[4].effects.size) // KUJI
        assertEquals(6, EffectRegistry.shelves[5].effects.size) // FOCAL
        assertEquals(25, EffectRegistry.all.size)
        for (shelf in EffectRegistry.shelves) {
            assertTrue("shelf ${shelf.id} should not be empty", shelf.effects.isNotEmpty())
            for (effect in shelf.effects) {
                assertEquals("effect ${effect.id} must belong to its shelf family", true, effect in EffectRegistry.all)
            }
        }
        for (category in EffectCategory.entries) {
            assertTrue(
                "category shelf ${category.label} should not be empty",
                EffectRegistry.forCategory(category).isNotEmpty(),
            )
        }
        assertNotNull(EffectRegistry.shelfById("Dusty"))
        assertNotNull(EffectRegistry.shelfById("Halation"))
        assertNotNull(EffectRegistry.byId("dusty_1"))
        assertNotNull(EffectRegistry.byId("halation_1"))
    }

    @Test
    fun intensityAboveOne_isClamped() {
        val src = testImage()
        val effect = EffectRegistry.defaultEffect
        val at100 = effect.cpu.apply(src, width, height, 1f, seed = 8L)
        val above = effect.cpu.apply(src, width, height, 2.5f, seed = 8L)
        assertArrayEquals("intensity must clamp to 1", at100, above)
        val below = effect.cpu.apply(src, width, height, -3f, seed = 8L)
        assertArrayEquals("negative intensity must clamp to 0", src, below)
    }

    @Test
    fun thumbnailsSizedRender_staysFastAndDeterministic() {
        // 160-200 px thumbs use the same 1080-scale coordinate convention.
        val tw = 160
        val th = 120
        val src = IntArray(tw * th) { i ->
            val x = i % tw
            val y = i / tw
            (0xFF shl 24) or ((x * 255 / tw) shl 16) or ((y * 255 / th) shl 8) or 128
        }
        for (effect in EffectRegistry.all) {
            val a = effect.cpu.apply(src, tw, th, 1f, seed = 21L)
            val b = effect.cpu.apply(src, tw, th, 1f, seed = 21L)
            assertArrayEquals(a, b)
            assertTrue("thumbnail render should change pixels for ${effect.id}", meanAbsDiff(src, a) > 0f)
        }
    }

    @Test
    fun rendererIsDeterministicAcrossThreadCounts() {
        // Large enough to trigger the row-parallel executor; repeated renders must be identical.
        val w = 200
        val h = 160
        val src = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            (0xFF shl 24) or ((x * 7 % 256) shl 16) or ((y * 5 % 256) shl 8) or ((x + y) % 256)
        }
        for (id in listOf("dusty_5", "halation_2")) {
            val effect = EffectRegistry.byId(id)!!
            val first = effect.cpu.apply(src, w, h, 0.8f, seed = 1234L)
            repeat(3) {
                val again = effect.cpu.apply(src, w, h, 0.8f, seed = 1234L)
                assertArrayEquals("parallel execution must not affect determinism for $id", first, again)
            }
        }
    }

    @Test
    fun halationHueMapping_matchesMeasuredFringeColours() {
        // Measured PREQUEL fringes: #1 red-orange, #2 cool cyan, #3 blue-violet.
        assertArrayEquals(floatArrayOf(1.00f, 0.20f, 0.01f), HalationHue.outerColor(0f), 0.001f)
        assertArrayEquals(floatArrayOf(0.01f, 0.79f, 1.00f), HalationHue.outerColor(0.5f), 0.001f)
        assertArrayEquals(floatArrayOf(0.37f, 0.27f, 1.00f), HalationHue.outerColor(0.7f), 0.001f)
        assertArrayEquals(floatArrayOf(0.60f, 0.25f, 1.00f), HalationHue.outerColor(1f), 0.001f)
        // #2's cyan must not read as purple/neutral: green and blue clearly above red.
        val cyan = HalationHue.outerColor(0.5f)
        assertTrue("hue 0.50 must be cyan, not purple", cyan[1] - cyan[0] > 0.4f && cyan[2] - cyan[0] > 0.7f)
        // #3's blue-violet must keep green lowest (violet side of blue).
        val h3 = HalationHue.outerColor(0.70f)
        assertTrue("hue 0.70 must be blue-violet (B > R > G)", h3[2] > h3[0] && h3[0] > h3[1])
        // Inner halo anchors: warm white / cyan white / blue white / violet white.
        assertArrayEquals(floatArrayOf(1.00f, 0.96f, 0.86f), HalationHue.innerColor(0f), 0.001f)
        assertArrayEquals(floatArrayOf(0.86f, 0.95f, 1.00f), HalationHue.innerColor(0.5f), 0.001f)
        assertArrayEquals(floatArrayOf(0.90f, 0.94f, 1.00f), HalationHue.innerColor(0.7f), 0.001f)
        assertArrayEquals(floatArrayOf(0.94f, 0.90f, 1.00f), HalationHue.innerColor(1f), 0.001f)
    }

    @Test
    fun halationVariants_addColourAppropriateHalo() {
        // Bright block on a dark field: the additive halo must tint edge spill red for #1,
        // cyan for #2 and blue-violet for #3 (end-to-end check of bright-pass + hue + gain).
        val w = 160
        val h = 120
        val src = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val inBlock = x in 40..119 && y in 30..89
            if (inBlock) 0xFFFFFFFF.toInt() else 0xFF101010.toInt()
        }
        val dark = BooleanArray(src.size) { i ->
            maxOf((src[i] shr 16) and 0xFF, (src[i] shr 8) and 0xFF, src[i] and 0xFF) < 60
        }
        fun darkAddedMeans(effect: PhotoEffect): FloatArray {
            val out = effect.cpu.apply(src, w, h, 1f, seed = 99L)
            var ar = 0.0
            var ag = 0.0
            var ab = 0.0
            var count = 0
            for (i in src.indices) {
                if (!dark[i]) continue
                ar += PixelMath.red(out[i]) - PixelMath.red(src[i])
                ag += PixelMath.green(out[i]) - PixelMath.green(src[i])
                ab += PixelMath.blue(out[i]) - PixelMath.blue(src[i])
                count++
            }
            val inv = 1.0 / count.coerceAtLeast(1)
            return floatArrayOf((ar * inv).toFloat(), (ag * inv).toFloat(), (ab * inv).toFloat())
        }
        val d1 = darkAddedMeans(HalationFamily.effects[0])
        val d2 = darkAddedMeans(HalationFamily.effects[1])
        val d3 = darkAddedMeans(HalationFamily.effects[2])
        assertTrue("Halation #1 spill must be red-orange (R > B), got ${d1.joinToString()}", d1[0] > d1[2])
        assertTrue("Halation #2 spill must be cyan (G > R, B > R), got ${d2.joinToString()}", d2[1] > d2[0] && d2[2] > d2[0])
        assertTrue("Halation #3 spill must be blue-violet (B > G), got ${d3.joinToString()}", d3[2] > d3[1])
        assertTrue(
            "Halation #2 must be greener than #3 relative to red (got #2=${d2.joinToString()} #3=${d3.joinToString()})",
            (d2[1] - d2[0]) > (d3[1] - d3[0]),
        )
        assertTrue(
            "Halation #3 must be bluer than #2 relative to green (got #3=${d3.joinToString()} #2=${d2.joinToString()})",
            (d3[2] - d3[1]) > (d2[2] - d2[1]),
        )
        assertTrue(
            "Halation #1 must be redder than #2 (got #1=${d1.joinToString()} #2=${d2.joinToString()})",
            (d1[0] - d1[2]) > (d2[0] - d2[2]),
        )
    }

    @Test
    fun dustyCpuOutput_isByteIdenticalToGoldenBaseline() {
        // Regression guard for future refactors: the five dusty CPU renders must stay
        // byte-identical. Baseline captured after the per-variant affine-grade retune that
        // reproduces the PREQUEL same-photo exports (96x64 test image, seed 42).
        val golden = mapOf(
            "dusty_1@0.0" to 6060674154198956319L,
            "dusty_1@0.5" to 690651029295879308L,
            "dusty_1@1.0" to 7780928977527100030L,
            "dusty_2@0.5" to -7211912901048790281L,
            "dusty_2@1.0" to 4316561552115412018L,
            "dusty_3@0.5" to 3359228399950617451L,
            "dusty_3@1.0" to -3161402972839658788L,
            "dusty_4@0.5" to -4276053863305750844L,
            "dusty_4@1.0" to 5133461954186000661L,
            "dusty_5@0.5" to -178652141572115190L,
            "dusty_5@1.0" to 7400739688563541036L,
        )
        val src = testImage()
        val mismatches = mutableListOf<String>()
        for ((key, expected) in golden) {
            val (id, intensityText) = key.split("@")
            val effect = EffectRegistry.byId(id)!!
            val out = effect.cpu.apply(src, width, height, intensityText.toFloat(), seed = 42L)
            val got = fnv(out)
            if (got != expected) mismatches += "\"$key\" to ${got}L, // was $expected"
        }
        assertTrue(
            "golden CPU output changed (dusty grade retune must be intentional):\n" +
                mismatches.joinToString("\n"),
            mismatches.isEmpty(),
        )
        // Intensity 0 is identity for both families (checksum of the source image).
        for (effect in EffectRegistry.all) {
            val out = effect.cpu.apply(src, width, height, 0f, seed = 42L)
            assertEquals(6060674154198956319L, fnv(out))
        }
    }
}

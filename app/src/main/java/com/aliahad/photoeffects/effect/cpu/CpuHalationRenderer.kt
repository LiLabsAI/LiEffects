package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.HalationParams
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic CPU mirror of the HALATION AGSL shader.
 *
 * Pipeline (recovered PREQUEL recipe): soft-knee max-channel bright-pass -> 3-scale halo field
 * (radii 8/28/64 × multiplier, box-blur approximation of PREQUEL's separable gaussian) ->
 * luma-edge gate (keeps the fringe at highlight boundaries instead of washing skin) ->
 * additive hue-tinted halo (inner white-yellow, outer red-orange … blue-violet) -> warm haze
 * screen -> base colour grade -> film grain -> vignette -> intensity blend with the original.
 *
 * Same contract as the dusty renderer: pure Kotlin, deterministic, row-parallel final pass,
 * intensity 0 returns an exact copy. Used for card thumbnails, CPU preview fallback and export.
 */
class CpuHalationRenderer(private val params: HalationParams) : CpuEffect {

    override fun apply(
        input: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
        seed: Long,
    ): IntArray {
        require(input.size == width * height) { "pixel buffer does not match $width x $height" }
        val t = intensity.coerceIn(0f, 1f)
        if (t <= 0f || width <= 0 || height <= 0) return input.copyOf()

        val n = input.size
        val seedI = EffectSeed.of(seed, params.seed)
        val minDim = min(width, height)
        val sc = minDim / 1080f
        val aspect = width.toFloat() / height.toFloat()

        // ---------------------------------------------------------------- constants
        val hue = params.hue.coerceIn(0f, 1f)
        val brightThreshold = params.brightThreshold.coerceIn(0f, 1f)
        val softKnee = params.softKnee.coerceIn(0.001f, 1f)
        val opacity = params.opacity.coerceIn(0f, 1f)
        val blurScale = 0.5f + 1.0f * params.blur.coerceIn(0f, 1f)
        val haze = params.haze.coerceIn(0f, 1f)
        val grain = params.grain.coerceIn(0f, 1f)
        val warmth = params.warmth.coerceIn(-1f, 1f)
        val tint = params.tint.coerceIn(-1f, 1f)
        val contrastK = 1f + params.contrast.coerceIn(-1f, 1f) * 0.8f
        val fade = params.fade.coerceIn(0f, 1f)
        val satK = 1f + params.saturation.coerceIn(-1f, 1f)
        val vignette = params.vignette.coerceIn(0f, 1f)
        val grainCell = max(1.2f * sc, 1.0f)

        // Piecewise hue mapping (measured PREQUEL fringes), shared with the AGSL shader via
        // HalationHue. Per-variant additive gain is fitted against the export fringe energy.
        val outer = com.aliahad.photoeffects.effect.HalationHue.outerColor(hue)
        val inner = com.aliahad.photoeffects.effect.HalationHue.innerColor(hue)
        val outerR = outer[0]
        val outerG = outer[1]
        val outerB = outer[2]
        val innerR = inner[0]
        val innerG = inner[1]
        val innerB = inner[2]

        // ---------------------------------------------------------------- 1-2. halo field
        // Exact recovered PREQUEL soft-knee max-channel formula (static report §6):
        //   f = 1 - t; f2 = s*f; br = max(r,g,b)
        //   soft = clamp(br-(f-f2),0,2*f2); soft = soft^2*0.25/(f2+1e-5)
        //   contrib = max(soft, br-f) / max(br,1e-5)
        // brightThreshold is the highlight floor (spec table); the recovered formula's "f = 1 - t"
        // refers to PREQUEL's internal darkness-space threshold, hence floor = t here.
        // Blur pyramid: radii 8/28/64 · m (m = min(W,H)/1080), Blur control scales them;
        // weights 0.40/0.34/0.26. CPU uses O(n) box-blur approximation of the separable gaussian.
        // The weighted field is accumulated in one plane inside scoped blocks so the bright-pass
        // and per-scale planes can be collected before the next stage (full-resolution export).
        val floorLvl = brightThreshold.coerceIn(0f, 1f)
        val kneeWidth = max(softKnee * floorLvl, 1e-4f)
        val r1 = max(2, (8f * sc * blurScale + 0.5f).toInt())
        val r2 = max(4, (28f * sc * blurScale + 0.5f).toInt())
        val r3 = max(8, (64f * sc * blurScale + 0.5f).toInt())
        val field = run {
            val bright = FloatArray(n) { i ->
                val r = PixelMath.red(input[i])
                val g = PixelMath.green(input[i])
                val b = PixelMath.blue(input[i])
                val br = max(r, max(g, b))
                var soft = (br - (floorLvl - kneeWidth)).coerceIn(0f, 2f * kneeWidth)
                soft = soft * soft * 0.25f / (kneeWidth + 1e-5f)
                // Normalise by the available headroom above the floor => usable 0..1 halo mask.
                val norm = max(1f - floorLvl, 0.05f)
                (max(soft, br - floorLvl) / norm).coerceIn(0f, 1f)
            }
            // Accumulating in scale order keeps k = f1*0.40 + f2*0.34 + f3*0.26 bit-identical.
            val acc = BoxBlur.blurFloatFast(bright, width, height, r1)
            for (i in 0 until n) acc[i] *= 0.40f
            run {
                val f2 = BoxBlur.blurFloatFast(bright, width, height, r2)
                for (i in 0 until n) acc[i] += f2[i] * 0.34f
            }
            run {
                val f3 = BoxBlur.blurFloatFast(bright, width, height, r3)
                for (i in 0 until n) acc[i] += f3[i] * 0.26f
            }
            acc
        }

        // Edge gate: |luma - blur(luma)| isolates highlight boundaries. The plain blurred field
        // spills a broad wash across skin (measured against PREQUEL exports), so the halo is
        // restricted to the edge band where the reference fringe actually lives. The luma plane
        // is gated in place; the blur result is scoped so it can be collected right after.
        val edgeRadius = max(2, (16f * sc + 0.5f).toInt())
        val edgeGate = FloatArray(n) { i ->
            PixelMath.luma(
                PixelMath.red(input[i]),
                PixelMath.green(input[i]),
                PixelMath.blue(input[i]),
            )
        }
        run {
            val lumaBlur = BoxBlur.blurFloatFast(edgeGate, width, height, edgeRadius)
            for (i in 0 until n) {
                edgeGate[i] = PixelMath.smoothstep(
                    0.09f,
                    0.24f,
                    kotlin.math.abs(edgeGate[i] - lumaBlur[i]),
                )
            }
        }

        // ---------------------------------------------------------------- 3. haze constants
        val hazeR = 0.843f * haze * 0.6f
        val hazeG = 0.667f * haze * 0.6f
        val hazeB = 0.502f * haze * 0.6f
        // Additive halo gain on top of the tool "Opacity" value; fitted per variant (mirrors AGSL
        // uHaloGain). Kept in sync with HalationFamily and architecture-halation.md.
        val opacityScale = opacity * params.haloGain.coerceIn(0f, 32f)

        val out = IntArray(n)

        PixelRunner.forEachRow(height) { y ->
            val fy = y + 0.5f
            val uy = fy / height

            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.red(srcPx)
                val sG = PixelMath.green(srcPx)
                val sB = PixelMath.blue(srcPx)

                // ------------------------------------------------ additive tinted halo
                // Low halo energy (fringe) = hue-tinted outer colour; high energy (core next to
                // highlights) fades to the white-warm inner colour (PREQUEL look).
                val haloField = field[i]
                val core = PixelMath.smoothstep(0.90f, 1.0f, haloField) * 0.35f
                val haloColR = outerR + (innerR - outerR) * core
                val haloColG = outerG + (innerG - outerG) * core
                val haloColB = outerB + (innerB - outerB) * core
                val gatedField = haloField * edgeGate[i]
                val haloR = haloColR * gatedField
                val haloG = haloColG * gatedField
                val haloB = haloColB * gatedField
                // Protect only near-clipped whites (0.75..0.98 luma): the edge gate already keeps
                // the halo off skin, and PREQUEL's fringe is visible on the bright collar itself.
                val srcLuma = PixelMath.luma(sR, sG, sB)
                val atten = 1f - PixelMath.smoothstep(0.75f, 0.98f, srcLuma)
                var cR = sR + haloR * opacityScale * atten
                var cG = sG + haloG * opacityScale * atten
                var cB = sB + haloB * opacityScale * atten

                // ------------------------------------------------ warm haze screen
                if (haze > 0.001f) {
                    cR = 1f - (1f - cR) * (1f - hazeR)
                    cG = 1f - (1f - cG) * (1f - hazeG)
                    cB = 1f - (1f - cB) * (1f - hazeB)
                }

                // ------------------------------------------------ base colour grade
                cR += warmth * 0.12f + tint * -0.04f
                cG += warmth * 0.04f + tint * 0.06f
                cB += warmth * -0.10f + tint * -0.04f
                val lum = PixelMath.luma(cR, cG, cB)
                cR = PixelMath.mix(lum, cR, satK)
                cG = PixelMath.mix(lum, cG, satK)
                cB = PixelMath.mix(lum, cB, satK)
                cR = (cR - 0.5f) * contrastK + 0.5f
                cG = (cG - 0.5f) * contrastK + 0.5f
                cB = (cB - 0.5f) * contrastK + 0.5f
                if (fade > 0f) {
                    cR = PixelMath.mix(cR, 0.10f + cR * 0.90f, fade)
                    cG = PixelMath.mix(cG, 0.10f + cG * 0.90f, fade)
                    cB = PixelMath.mix(cB, 0.10f + cB * 0.90f, fade)
                }
                cR = cR.coerceIn(0f, 1f)
                cG = cG.coerceIn(0f, 1f)
                cB = cB.coerceIn(0f, 1f)

                // ------------------------------------------------ film grain
                if (grain > 0.001f) {
                    val gix = kotlin.math.floor((x + 0.5f) / grainCell).toInt()
                    val giy = kotlin.math.floor(fy / grainCell).toInt()
                    val gv = PixelMath.hashF(gix, giy, seedI + 11)
                    val gb = 0.5f + (gv - 0.5f) * (grain * 0.06f)
                    cR = PixelMath.softLightFilm(cR, gb).coerceIn(0f, 1f)
                    cG = PixelMath.softLightFilm(cG, gb).coerceIn(0f, 1f)
                    cB = PixelMath.softLightFilm(cB, gb).coerceIn(0f, 1f)
                    cR = PixelMath.softLightFilm(cR, gb).coerceIn(0f, 1f)
                    cG = PixelMath.softLightFilm(cG, gb).coerceIn(0f, 1f)
                    cB = PixelMath.softLightFilm(cB, gb).coerceIn(0f, 1f)
                }

                // ------------------------------------------------ vignette
                if (vignette > 0.001f) {
                    val dx = ((x + 0.5f) / width - 0.5f) * aspect
                    val dy = uy - 0.5f
                    val d = sqrt(dx * dx + dy * dy)
                    val vig = 1f - vignette * PixelMath.smoothstep(0.35f, 0.95f, d * 0.9f)
                    cR *= vig
                    cG *= vig
                    cB *= vig
                }

                // ------------------------------------------------ intensity blend
                val rr = PixelMath.mix(sR, cR.coerceIn(0f, 1f), t)
                val gg = PixelMath.mix(sG, cG.coerceIn(0f, 1f), t)
                val bb = PixelMath.mix(sB, cB.coerceIn(0f, 1f), t)
                out[i] = PixelMath.pack(rr, gg, bb, alpha)
            }
        }
        return out
    }
}

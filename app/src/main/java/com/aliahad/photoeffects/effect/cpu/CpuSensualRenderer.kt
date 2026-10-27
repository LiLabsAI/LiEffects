package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.SensualParams
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic CPU mirror of the SENSUAL AGSL shader.
 *
 * Pipeline order mirrors `docs/prequel-research/spec/sensual-rewrite-spec.md` §1 exactly:
 * small global soft-focus gaussian -> masked variable blur (bundled `adj-Blur` mask, radius
 * R = 30*(Blur/100)*m*1.25) -> per-channel cubic tone curves -> saturation mix + gain/offset ->
 * monochrome grain -> intensity blend.
 *
 * The previous (wrong) implementation rendered sharp/grey because it only mixed a single small
 * blur at a low amount; here the soft focus is a true separable gaussian and the masked blur is
 * radius-fitted from PREQUEL's owned blur recipe.
 *
 * Same contract as the other CPU renderers: pure Kotlin, deterministic, row-parallel final
 * pass, intensity 0 returns an exact copy.
 */
class CpuSensualRenderer(private val params: SensualParams) : CpuEffect {

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

        // ---------------------------------------------------------------- constants
        val sigma = params.sigma1080 * sc
        val blurRadius = params.blurRadius1080 * sc
        val grainCell = max(params.grainCell1080 * sc, 1f)
        val grainAmp = params.grainAmp / 0.2887f
        val mottleAmp = params.mottleAmp
        val mottleFreq = params.mottleFreq
        val mottleAmp2 = params.mottleAmp2
        val mottleFreq2 = params.mottleFreq2
        val toneR = params.toneR
        val toneG = params.toneG
        val toneB = params.toneB
        val satMix = params.satMix.coerceIn(0f, 1f)
        val gainR = params.gainR
        val gainG = params.gainG
        val gainB = params.gainB

        // ---------------------------------------------------------------- planes
        val baseR = FloatArray(n) { PixelMath.red(input[it]) }
        val baseG = FloatArray(n) { PixelMath.green(input[it]) }
        val baseB = FloatArray(n) { PixelMath.blue(input[it]) }

        // 1. small global soft-focus gaussian
        var (r1, g1, b1) = FastGaussian.blurPlanes(baseR, baseG, baseB, width, height, sigma)

        // 2. masked variable blur: B = B1*(1-mask) + B2*mask
        if (blurRadius > 0.05f) {
            val (r2, g2, b2) = FastGaussian.blurPlanes(r1, g1, b1, width, height, blurRadius)
            val mask = BlurMaskStore.requireMask()
            for (y in 0 until height) {
                val v = (y + 0.5f) / height
                for (x in 0 until width) {
                    val i = y * width + x
                    val m = mask.sample((x + 0.5f) / width, v)
                    r1[i] = PixelMath.mix(r1[i], r2[i], m)
                    g1[i] = PixelMath.mix(g1[i], g2[i], m)
                    b1[i] = PixelMath.mix(b1[i], b2[i], m)
                }
            }
        }

        val out = IntArray(n)
        PixelRunner.forEachRow(height) { y ->
            val fy = y + 0.5f
            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.red(srcPx)
                val sG = PixelMath.green(srcPx)
                val sB = PixelMath.blue(srcPx)

                // 3. per-channel cubic tone curves
                var cR = toneR.eval(r1[i])
                var cG = toneG.eval(g1[i])
                var cB = toneB.eval(b1[i])

                // 4. saturation mix + per-channel gain/offset
                val gray = PixelMath.luma(cR, cG, cB)
                val keep = 1f - satMix
                cR = gainR.eval(PixelMath.mix(gray, cR, keep))
                cG = gainG.eval(PixelMath.mix(gray, cG, keep))
                cB = gainB.eval(PixelMath.mix(gray, cB, keep))

                // 5a. Texture mottling, two luma-neutral octaves (PREQUEL's visible film texture)
                val mottle1 = (PixelMath.fbm2(
                    (x + 0.5f) / minDim * mottleFreq,
                    fy / minDim * mottleFreq,
                    seedI + 53,
                ) - 0.5f) * 2f * mottleAmp
                val mottle2 = (PixelMath.fbm2(
                    (x + 0.5f) / minDim * mottleFreq2,
                    fy / minDim * mottleFreq2,
                    seedI + 71,
                ) - 0.5f) * 2f * mottleAmp2
                val textureK = (1f + mottle1 + mottle2).coerceIn(0.8f, 1.2f)
                cR *= textureK
                cG *= textureK
                cB *= textureK

                // 5. monochrome film grain
                val gix = floor((x + 0.5f) / grainCell).toInt()
                val giy = floor(fy / grainCell).toInt()
                val gv = PixelMath.hashF(gix, giy, seedI + 37)
                val grain = (gv - 0.5f) * grainAmp
                cR = (cR + grain).coerceIn(0f, 1f)
                cG = (cG + grain).coerceIn(0f, 1f)
                cB = (cB + grain).coerceIn(0f, 1f)

                // 6. intensity blend
                val rr = PixelMath.mix(sR, cR, t)
                val gg = PixelMath.mix(sG, cG, t)
                val bb = PixelMath.mix(sB, cB, t)
                out[i] = PixelMath.pack(rr, gg, bb, alpha)
            }
        }
        return out
    }
}

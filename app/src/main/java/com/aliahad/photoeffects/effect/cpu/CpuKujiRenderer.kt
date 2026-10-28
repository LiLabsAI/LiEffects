package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.KujiParams
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic CPU mirror of the KUJI AGSL shader.
 *
 * Pipeline (identical to [com.aliahad.photoeffects.effect.KUJI_AGSL]):
 * per-channel cubic tone -> saturation mix + gain/offset -> monochrome grain -> premultiplied
 * paper-frame overlay -> intensity blend.
 *
 * The frame comes from [KujiFrameStore] (populated from the bundled PNG assets at app start);
 * when a frame is unavailable (plain JVM tests that did not inject one) the graded look is
 * rendered without it, so the rest of the pipeline stays testable in isolation.
 */
class CpuKujiRenderer(private val params: KujiParams) : CpuEffect {

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

        val grainCell = max(params.grainCell1080 * sc, 1f)
        val grainAmp = params.grainAmp / 0.2887f
        val toneR = params.toneR
        val toneG = params.toneG
        val toneB = params.toneB
        val satMix = params.satMix.coerceIn(0f, 1f)
        val gainR = params.gainR
        val gainG = params.gainG
        val gainB = params.gainB
        val frame = KujiFrameStore.get(params.frameAsset)

        val out = IntArray(n)
        PixelRunner.forEachRow(height) { y ->
            val fy = y + 0.5f
            val v = fy / height
            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.red(srcPx)
                val sG = PixelMath.green(srcPx)
                val sB = PixelMath.blue(srcPx)

                // 1. per-channel cubic tone curves
                var cR = toneR.eval(sR)
                var cG = toneG.eval(sG)
                var cB = toneB.eval(sB)

                // 2. saturation mix + per-channel gain/offset
                val gray = PixelMath.luma(cR, cG, cB)
                val keep = 1f - satMix
                cR = gainR.eval(PixelMath.mix(gray, cR, keep))
                cG = gainG.eval(PixelMath.mix(gray, cG, keep))
                cB = gainB.eval(PixelMath.mix(gray, cB, keep))

                // 3. monochrome film grain
                val gix = floor((x + 0.5f) / grainCell).toInt()
                val giy = floor(fy / grainCell).toInt()
                val gv = PixelMath.hashF(gix, giy, seedI + 37)
                val grain = (gv - 0.5f) * grainAmp
                cR = (cR + grain).coerceIn(0f, 1f)
                cG = (cG + grain).coerceIn(0f, 1f)
                cB = (cB + grain).coerceIn(0f, 1f)

                // 4. paper frame overlay (premultiplied over the graded photo)
                if (frame != null) {
                    val pm = frame.samplePremultiplied((x + 0.5f) / width, v)
                    if (pm != 0) {
                        val fa = ((pm ushr 24) and 0xFF) / 255f
                        if (fa > 0f) {
                            val inv = 1f - fa
                            cR = ((pm ushr 16) and 0xFF) / 255f + cR * inv
                            cG = ((pm ushr 8) and 0xFF) / 255f + cG * inv
                            cB = (pm and 0xFF) / 255f + cB * inv
                        }
                    }
                }

                // 5. intensity blend
                val rr = PixelMath.mix(sR, cR, t)
                val gg = PixelMath.mix(sG, cG, t)
                val bb = PixelMath.mix(sB, cB, t)
                out[i] = PixelMath.pack(rr, gg, bb, alpha)
            }
        }
        return out
    }
}

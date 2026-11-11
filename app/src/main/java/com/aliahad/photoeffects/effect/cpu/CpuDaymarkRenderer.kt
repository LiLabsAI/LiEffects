package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.DotMatrixDate
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.DaymarkParams
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic Daymark renderer: pre-blur, tone curves, saturation and gain,
 * vignette, grain and mottling, then intensity blending. DotMatrixDate supplies
 * the date overlay after this colour pipeline.
 */
class CpuDaymarkRenderer(
    private val params: DaymarkParams,
    private val dateProvider: () -> DotMatrixDate.DateParts = DotMatrixDate.systemDate,
) : CpuEffect {

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
        val sigma = params.blurSigma1080 * sc
        val grainCell = max(params.grainCell1080 * sc, 1f)
        val vigC0 = params.vignetteC0
        val vigC1 = params.vignetteC1
        val vigC2 = params.vignetteC2
        val toneR = params.toneR
        val toneG = params.toneG
        val toneB = params.toneB
        val satMix = params.satMix.coerceIn(0f, 1f)
        val gainR = params.gainR
        val gainG = params.gainG
        val gainB = params.gainB
        val grainAmp = params.grainAmp / 0.2887f
        val mottleAmp = params.mottleAmp / 0.2f
        val mottleFreq = params.mottleFreq

        // ---------------------------------------------------------------- pre-blur planes
        val baseR = FloatArray(n) { PixelMath.visibleRed(input[it]) }
        val baseG = FloatArray(n) { PixelMath.visibleGreen(input[it]) }
        val baseB = FloatArray(n) { PixelMath.visibleBlue(input[it]) }
        val (br0, bg0, bb0) = FastGaussian.blurPlanes(baseR, baseG, baseB, width, height, sigma)
        val midX = 0.5f * width
        val midY = 0.5f * height
        val halfW = max(0.5f * width, 1f)
        val halfH = max(0.5f * height, 1f)

        // ---------------------------------------------------------------- stamp coverage
        // Rasterised once into a per-pixel coverage buffer; dots only touch their own boxes.
        val dots = DotMatrixDate.layout(dateProvider(), width, height)
        val stampCov = if (dots.isEmpty()) null else FloatArray(n)
        if (stampCov != null) {
            for (dot in dots) {
                val minX = (dot.cx - dot.r - 1f).toInt().coerceAtLeast(0)
                val maxX = (dot.cx + dot.r + 1f).toInt().coerceAtMost(width - 1)
                val minY = (dot.cy - dot.r - 1f).toInt().coerceAtLeast(0)
                val maxY = (dot.cy + dot.r + 1f).toInt().coerceAtMost(height - 1)
                for (yy in minY..maxY) {
                    val dy = (yy + 0.5f) - dot.cy
                    for (xx in minX..maxX) {
                        val dx = (xx + 0.5f) - dot.cx
                        val dist = sqrt(dx * dx + dy * dy)
                        // Thin dots with a soft ~1 px edge so the stamp reads as amber bloom
                        // (the dots are soft) instead of a bolder hard-edged matrix.
                        val soft = 0.8f
                        val cov = ((dot.r - dist) / soft + 0.5f).coerceIn(0f, 1f)
                        if (cov > 0f) {
                            val idx = yy * width + xx
                            if (cov > stampCov[idx]) stampCov[idx] = cov
                        }
                    }
                }
            }
        }

        val stampR = ((DotMatrixDate.STAMP_COLOR_RGB shr 16) and 0xFF) / 255f
        val stampG = ((DotMatrixDate.STAMP_COLOR_RGB shr 8) and 0xFF) / 255f
        val stampB = (DotMatrixDate.STAMP_COLOR_RGB and 0xFF) / 255f

        val out = IntArray(n)
        PixelRunner.forEachRow(height) { y ->
            val fy = y + 0.5f
            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.visibleRed(srcPx)
                val sG = PixelMath.visibleGreen(srcPx)
                val sB = PixelMath.visibleBlue(srcPx)

                // ------------------------------------------------ 1. pre-blurred source
                val bR = br0[i]
                val bG = bg0[i]
                val bB = bb0[i]

                // ------------------------------------------------ 2. per-channel cubic tone
                var cR = toneR.eval(bR)
                var cG = toneG.eval(bG)
                var cB = toneB.eval(bB)

                // ------------------------------------------------ 3. saturation mix
                val gray = PixelMath.luma(cR, cG, cB)
                val keep = 1f - satMix
                cR = PixelMath.mix(gray, cR, keep)
                cG = PixelMath.mix(gray, cG, keep)
                cB = PixelMath.mix(gray, cB, keep)

                // ------------------------------------------------ 4. per-channel gain/offset
                cR = gainR.eval(cR)
                cG = gainG.eval(cG)
                cB = gainB.eval(cB)

                // ------------------------------------------------ 5. vignette
                val dx = (x + 0.5f - midX) / halfW
                val dy = (fy - midY) / halfH
                val r = sqrt(dx * dx + dy * dy)
                val vig = (vigC0 + vigC1 * r + vigC2 * r * r).coerceIn(0f, 2f)
                cR *= vig
                cG *= vig
                cB *= vig

                // ------------------------------------------------ 6. grain + low-frequency mottle
                val gix = floor((x + 0.5f) / grainCell).toInt()
                val giy = floor(fy / grainCell).toInt()
                val gv = PixelMath.hashF(gix, giy, seedI + 11)
                val grain = (gv - 0.5f) * grainAmp
                val mottle = (
                    PixelMath.fbm2(
                        (x + 0.5f) / minDim * mottleFreq * 2f,
                        fy / minDim * mottleFreq * 2f,
                        seedI + 29,
                    ) - 0.5f
                    ) * mottleAmp
                val grainDelta = grain + mottle
                cR = (cR + grainDelta).coerceIn(0f, 1f)
                cG = (cG + grainDelta).coerceIn(0f, 1f)
                cB = (cB + grainDelta).coerceIn(0f, 1f)

                // ------------------------------------------------ 7. intensity blend
                var rr = PixelMath.mix(sR, cR, t)
                var gg = PixelMath.mix(sG, cG, t)
                var bb = PixelMath.mix(sB, cB, t)

                // ------------------------------------------------ 8. date stamp, drawn last
                if (stampCov != null) {
                    val cov = stampCov[i] * t
                    if (cov > 0f) {
                        rr = PixelMath.mix(rr, stampR, cov)
                        gg = PixelMath.mix(gg, stampG, cov)
                        bb = PixelMath.mix(bb, stampB, cov)
                    }
                }

                out[i] = PixelMath.pack(rr, gg, bb, alpha)
            }
        }
        return out
    }
}

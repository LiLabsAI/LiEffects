package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.NocturneParams
import com.aliahad.photoeffects.filter.FilterGrain
import kotlin.math.abs

internal class CpuNocturneRenderer(private val p: NocturneParams) : CpuEffect {
    override fun apply(input: IntArray, width: Int, height: Int, intensity: Float, seed: Long): IntArray {
        require(input.size == width * height)
        val t = intensity.coerceIn(0f, 1f)
        if (t == 0f) return input.copyOf()
        val output = IntArray(input.size)
        val noiseSeed = EffectSeed.of(seed, p.seed)
        PixelRunner.forEachRow(height) { y ->
            val uy = (y + 0.5f) / height - 0.5f
            for (x in 0 until width) {
                val i = y * width + x
                val pixel = input[i]
                val alpha = pixel ushr 24
                if (alpha == 0) {
                    output[i] = pixel
                    continue
                }
                val sr = PixelMath.red(pixel)
                val sg = PixelMath.green(pixel)
                val sb = PixelMath.blue(pixel)
                val lum = PixelMath.luma(sr, sg, sb)
                var r = PixelMath.mix(lum, sr, p.saturation) * p.exposure
                var g = PixelMath.mix(lum, sg, p.saturation) * p.exposure
                var b = PixelMath.mix(lum, sb, p.saturation) * p.exposure
                r *= 0.9f + 0.1f * r
                g *= 0.9f + 0.1f * g
                b *= 0.9f + 0.1f * b

                val ux = (x + 0.5f) / width - 0.5f
                val edge = PixelMath.smoothstep(0.12f, 0.48f, ux * ux + uy * uy)
                val shadow = (1f - PixelMath.smoothstep(0.12f, 0.60f, lum)) * lum
                val lightMask = PixelMath.smoothstep(0.02f, 0.38f, lum) *
                    (1f - PixelMath.smoothstep(0.72f, 1f, lum))
                val field: Float
                val shade: Float
                val lightR: Float
                val lightG: Float
                val lightB: Float
                if (p.gilded) {
                    val diagonal = ux + 0.55f * uy + 0.10f
                    field = 1f - PixelMath.smoothstep(0.015f, 0.32f,
                        diagonal * diagonal * 2.8f + uy * uy * 0.65f)
                    shade = 1f - p.edgeShade * edge
                    lightR = 1f; lightG = 0.72f; lightB = 0.38f
                    r += shadow * 0.035f
                    g += shadow * 0.010f
                    b -= shadow * 0.020f
                } else {
                    val window = ux - 0.26f + 0.18f * uy
                    val rim = 1f - PixelMath.smoothstep(0.012f, 0.21f, abs(window))
                    val reflection = 1f - PixelMath.smoothstep(0.025f, 0.13f, abs(ux + 0.36f))
                    field = (rim + reflection * 0.28f) *
                        (1f - PixelMath.smoothstep(0.18f, 0.50f, abs(uy)))
                    val cinema = PixelMath.smoothstep(0.20f, 0.50f, abs(uy))
                    shade = 1f - p.edgeShade * (edge * 0.55f + cinema * 0.45f)
                    lightR = 0.48f; lightG = 0.72f; lightB = 1f
                    r -= shadow * 0.025f
                    g += shadow * 0.015f
                    b += shadow * 0.060f
                }
                r = (r * shade).coerceIn(0f, 1f)
                g = (g * shade).coerceIn(0f, 1f)
                b = (b * shade).coerceIn(0f, 1f)
                val illumination = p.light * field * lightMask
                r += (1f - r) * illumination * lightR
                g += (1f - g) * illumination * lightG
                b += (1f - b) * illumination * lightB
                val grain = FilterGrain.noise(x, y, width, height, noiseSeed, 1.15f) *
                    p.grain * PixelMath.smoothstep(0f, 0.12f, lum) *
                    (1f - PixelMath.smoothstep(0.70f, 1f, lum))
                output[i] = PixelMath.pack(
                    PixelMath.mix(sr, (r + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sg, (g + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sb, (b + grain).coerceIn(0f, 1f), t),
                    alpha,
                )
            }
        }
        return output
    }
}

package com.aliahad.photoeffects.adjust.cpu

import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.adjust.AdjustPlan
import com.aliahad.photoeffects.adjust.HslBand
import com.aliahad.photoeffects.adjust.HslComponent
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.effect.cpu.PixelRunner
import com.aliahad.photoeffects.filter.FilterGrain
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic CPU implementation of the Adjust inventory.
 *
 * Order: Light → Color → HSL → Details → Blur/Refocus → Optical → Stylize. Spatial stages are
 * skipped unless their plan bit is set and at least one control is non-neutral. Noise uses the
 * bounded [FilterGrain] hash, never per-pixel trigonometry.
 */
class CpuAdjustRenderer(
    private val params: AdjustParams,
    private val plan: AdjustPlan,
) : CpuEffect {

    override fun apply(
        input: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
        seed: Long,
    ): IntArray {
        require(input.size == width * height)
        val t = intensity.coerceIn(0f, 1f)
        if (t <= 0f || params.isIdentity || width <= 0 || height <= 0) return input.copyOf()
        var pixels = input
        if (hasColorWork()) pixels = colorPass(pixels, width, height)
        if (plan.detail && hasDetailWork()) pixels = detailPass(pixels, width, height)
        if (plan.blur && hasBlurWork()) pixels = blurPass(pixels, width, height)
        if (hasFinishWork()) pixels = finishPass(pixels, width, height, seed)
        if (t >= 1f) return if (pixels === input) input.copyOf() else pixels
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val src = input[i]
            val dst = pixels[i]
            val a = src ushr 24
            out[i] = PixelMath.pack(
                PixelMath.mix(PixelMath.red(src), PixelMath.red(dst), t),
                PixelMath.mix(PixelMath.green(src), PixelMath.green(dst), t),
                PixelMath.mix(PixelMath.blue(src), PixelMath.blue(dst), t),
                a,
            )
        }
        return out
    }

    private fun hasColorWork(): Boolean = with(params) {
        light.exposure != 0 || light.brightness != 0 || light.contrast != 0 ||
            light.highlights != 0 || light.shadows != 0 || light.blackPoint != 0 ||
            light.fade != 0 || light.brilliance != 0 || light.pushPull != 0 ||
            color.saturation != 0 || color.vibrance != 0 || color.colorDensity != 0 ||
            color.temperature != 0 || color.tint != 0 || color.skintone != 0 ||
            AdjustControl.entries.any { it.category == com.aliahad.photoeffects.adjust.AdjustCategory.Hsl && value(it) != 0 }
    }

    private fun hasDetailWork(): Boolean = with(params) {
        light.cleanup != 0 || color.hdr != 0 || details.sharpen != 0 ||
            details.ultrasharp != 0 || details.clarity != 0 ||
            details.dehaze != 0 || details.midtoneDetails != 0
    }

    private fun hasBlurWork(): Boolean = with(params) {
        blur.mist != 0 || blur.glow != 0 || blur.gaussian != 0 || blur.vignette != 0 ||
            blur.zoom != 0 || blur.radial != 0 || blur.directional != 0 ||
            refocus.amount != 0
    }

    private fun hasFinishWork(): Boolean = with(params) {
        stylize.grain != 0 || stylize.dust != 0 || stylize.vignette != 0 ||
            stylize.vintage != 0 || optical.fisheye != 0 || optical.rgbSplit != 0 ||
            optical.aberration != 0
    }

    // ---------------------------------------------------------------- colour pass
    private fun colorPass(input: IntArray, width: Int, height: Int): IntArray {
        val l = params.light
        val cParams = params.color
        val exposure = 2.0.pow(0.5 * l.exposure / 100.0).toFloat()
        val brightness = l.brightness / 100f
        val contrast = l.contrast / 100f
        val highlights = l.highlights / 100f
        val shadows = l.shadows / 100f
        val blackPoint = l.blackPoint / 100f
        val fade = l.fade / 100f
        val brilliance = l.brilliance / 100f
        val pushPull = l.pushPull / 100f
        val saturation = cParams.saturation / 100f
        val vibrance = cParams.vibrance / 100f
        val colorDensity = cParams.colorDensity / 100f
        val temperature = cParams.temperature / 100f
        val tint = cParams.tint / 100f
        val skintone = cParams.skintone / 100f
        val hslActive = params.hsl.let {
            it.red != com.aliahad.photoeffects.adjust.HslBandParams() ||
                it.orange != com.aliahad.photoeffects.adjust.HslBandParams() ||
                it.yellow != com.aliahad.photoeffects.adjust.HslBandParams() ||
                it.green != com.aliahad.photoeffects.adjust.HslBandParams() ||
                it.blue != com.aliahad.photoeffects.adjust.HslBandParams() ||
                it.purple != com.aliahad.photoeffects.adjust.HslBandParams()
        }
        val hslBands = HslBand.entries.map { band ->
            val p = params.hsl.band(band)
            FloatArray(3) { index ->
                when (index) {
                    0 -> p.hue / 100f
                    1 -> p.saturation / 100f
                    else -> p.lightness / 100f
                }
            }
        }
        val out = IntArray(input.size)
        PixelRunner.forEachRow(height) { y ->
            val hsl = FloatArray(3)
            val rgb = FloatArray(3)
            for (x in 0 until width) {
                val i = y * width + x
                val src = input[i]
                var r = PixelMath.red(src)
                var g = PixelMath.green(src)
                var b = PixelMath.blue(src)

                if (exposure != 1f) { r *= exposure; g *= exposure; b *= exposure }
                if (brightness != 0f) {
                    if (brightness > 0f) {
                        val k = 0.15f * brightness
                        r += k * (1f - r); g += k * (1f - g); b += k * (1f - b)
                    } else {
                        val k = 0.15f * brightness
                        r += k * r; g += k * g; b += k * b
                    }
                }
                if (contrast != 0f) {
                    val k = 1f + 0.6f * contrast
                    r = 0.5f + (r - 0.5f) * k
                    g = 0.5f + (g - 0.5f) * k
                    b = 0.5f + (b - 0.5f) * k
                }
                var luma = PixelMath.luma(r, g, b)
                if (highlights != 0f) {
                    val m = PixelMath.smoothstep(0.55f, 1f, luma)
                    if (highlights > 0f) {
                        val k = 0.35f * highlights * m
                        r += k * (1f - r); g += k * (1f - g); b += k * (1f - b)
                    } else {
                        val k = 1f + 0.35f * highlights * m
                        r *= k; g *= k; b *= k
                    }
                }
                luma = PixelMath.luma(r, g, b)
                if (shadows != 0f) {
                    val m = 1f - PixelMath.smoothstep(0f, 0.45f, luma)
                    if (shadows > 0f) {
                        val k = 0.30f * shadows * m
                        r += k * (1f - r); g += k * (1f - g); b += k * (1f - b)
                    } else {
                        val k = 1f + 0.30f * shadows * m
                        r *= k; g *= k; b *= k
                    }
                }
                if (blackPoint != 0f) {
                    if (blackPoint > 0f) {
                        val bp = 0.12f * blackPoint
                        r = max(0f, (r - bp) / (1f - bp))
                        g = max(0f, (g - bp) / (1f - bp))
                        b = max(0f, (b - bp) / (1f - bp))
                    } else {
                        val k = -0.10f * blackPoint
                        r += k * (1f - r); g += k * (1f - g); b += k * (1f - b)
                    }
                }
                if (fade != 0f) {
                    val f = 0.14f * fade
                    r = f + r * (1f - f); g = f + g * (1f - f); b = f + b * (1f - f)
                }
                luma = PixelMath.luma(r, g, b)
                if (brilliance != 0f) {
                    val w = 1f - abs(2f * luma - 1f)
                    val k = 1f + 0.35f * brilliance * w
                    r = PixelMath.mix(luma, r, k)
                    g = PixelMath.mix(luma, g, k)
                    b = PixelMath.mix(luma, b, k)
                }
                if (pushPull != 0f) {
                    val k = 1f + 0.45f * pushPull
                    r = 0.5f + (r - 0.5f) * k
                    g = 0.5f + (g - 0.5f) * k
                    b = 0.5f + (b - 0.5f) * k
                }

                luma = PixelMath.luma(r, g, b)
                if (saturation != 0f) {
                    val k = 1f + saturation
                    r = PixelMath.mix(luma, r, k); g = PixelMath.mix(luma, g, k); b = PixelMath.mix(luma, b, k)
                }
                if (vibrance != 0f) {
                    val mx = max(r, max(g, b))
                    val mn = min(r, min(g, b))
                    val sat = if (mx > 0.0001f) (mx - mn) / mx else 0f
                    val k = 1f + 0.8f * vibrance * (1f - sat)
                    r = PixelMath.mix(luma, r, k); g = PixelMath.mix(luma, g, k); b = PixelMath.mix(luma, b, k)
                }
                if (colorDensity != 0f) {
                    val k = 1f + 0.5f * colorDensity
                    r = luma + (r - luma) * k; g = luma + (g - luma) * k; b = luma + (b - luma) * k
                }
                if (temperature != 0f) {
                    r *= 1f + 0.12f * temperature
                    g *= 1f + 0.02f * temperature
                    b *= 1f - 0.12f * temperature
                }
                if (tint != 0f) {
                    r *= 1f + 0.04f * tint
                    g *= 1f - 0.10f * tint
                    b *= 1f + 0.04f * tint
                }
                r = r.coerceIn(0f, 1f); g = g.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)

                if (skintone != 0f) {
                    rgbToHsl(r, g, b, hsl)
                    val hue = hsl[0]
                    if (hue in 10f..55f) {
                        val weight = 1f - abs(hue - 32f) / 23f
                        val k = 1f - 0.20f * skintone * weight
                        hsl[1] = (hsl[1] * k).coerceIn(0f, 1f)
                        hslToRgb(hsl[0], hsl[1], hsl[2], rgb)
                        r = rgb[0]; g = rgb[1]; b = rgb[2]
                    }
                }

                if (hslActive) {
                    rgbToHsl(r, g, b, hsl)
                    var hue = hsl[0]
                    var sat = hsl[1]
                    var light = hsl[2]
                    for ((index, band) in HslBand.entries.withIndex()) {
                        val values = hslBands[index]
                        if (values[0] == 0f && values[1] == 0f && values[2] == 0f) continue
                        val distance = angularDistance(hue, band.centerDegrees)
                        val w = (1f - distance / band.halfWidthDegrees).coerceIn(0f, 1f)
                        if (w <= 0f) continue
                        hue = (hue + 0.4f * values[0] * w * 180f / PI.toFloat()) % 360f
                        if (hue < 0f) hue += 360f
                        sat = (sat * (1f + values[1] * w)).coerceIn(0f, 1f)
                        light = (light + 0.25f * values[2] * w).coerceIn(0f, 1f)
                    }
                    hslToRgb(hue, sat, light, rgb)
                    r = rgb[0]; g = rgb[1]; b = rgb[2]
                }

                out[i] = PixelMath.pack(
                    r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f),
                    src ushr 24,
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------- detail pass
    private fun detailPass(input: IntArray, width: Int, height: Int): IntArray {
        val d = params.details
        val cleanup = params.light.cleanup / 100f
        val hdr = params.color.hdr / 100f
        val sharpen = d.sharpen / 100f
        val ultra = d.ultrasharp / 100f
        val clarity = d.clarity / 100f
        val dehaze = d.dehaze / 100f
        val midtone = d.midtoneDetails / 100f
        val minDim = min(width, height)
        val wideRadius = max(minDim * 0.03f, 2f)
        // Same 9-tap support as ADJUST_DETAIL_AGSL; a box blur here caused visible CPU/GPU drift.
        val wide = FloatArray(input.size)
        PixelRunner.forEachRow(height) { y ->
            val scratch = FloatArray(3)
            for (x in 0 until width) {
                val i = y * width + x
                val src = input[i]
                var acc = PixelMath.luma(PixelMath.red(src), PixelMath.green(src), PixelMath.blue(src)) * 0.2f
                sampleArgb(input, width, height, x + 0.5f + wideRadius, y + 0.5f, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f - wideRadius, y + 0.5f, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f, y + 0.5f + wideRadius, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f, y + 0.5f - wideRadius, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f + wideRadius * 0.7f, y + 0.5f + wideRadius * 0.7f, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f - wideRadius * 0.7f, y + 0.5f + wideRadius * 0.7f, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f + wideRadius * 0.7f, y + 0.5f - wideRadius * 0.7f, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                sampleArgb(input, width, height, x + 0.5f - wideRadius * 0.7f, y + 0.5f - wideRadius * 0.7f, scratch)
                acc += PixelMath.luma(scratch[0], scratch[1], scratch[2]) * 0.1f
                wide[i] = acc
            }
        }
        val out = IntArray(input.size)
        PixelRunner.forEachRow(height) { y ->
            val blurScratch = FloatArray(3)
            val blur3 = FloatArray(3)
            for (x in 0 until width) {
                val i = y * width + x
                val src = input[i]
                var r = PixelMath.red(src)
                var g = PixelMath.green(src)
                var b = PixelMath.blue(src)
                val luma = PixelMath.luma(r, g, b)
                val wideL = wide[i]

                if (cleanup != 0f) {
                    colorBlur3(input, width, height, x, y, 2f, blur3, blurScratch)
                    val detail = abs(luma - wideL)
                    val lowDetail = 1f - PixelMath.smoothstep(0.02f, 0.12f, detail)
                    val k = cleanup * 0.55f * lowDetail
                    r = PixelMath.mix(r, blur3[0], k)
                    g = PixelMath.mix(g, blur3[1], k)
                    b = PixelMath.mix(b, blur3[2], k)
                }
                if (hdr != 0f) {
                    val lift = 0.25f * hdr * (1f - luma)
                    val compress = 1f / (1f + 0.20f * hdr * luma)
                    r = (r + lift * (1f - r)) * compress
                    g = (g + lift * (1f - g)) * compress
                    b = (b + lift * (1f - b)) * compress
                }
                if (sharpen != 0f) {
                    colorBlur3(input, width, height, x, y, 1f, blur3, blurScratch)
                    val k = sharpen * 1.2f
                    r = (r + (r - blur3[0]) * k).coerceIn(0f, 1f)
                    g = (g + (g - blur3[1]) * k).coerceIn(0f, 1f)
                    b = (b + (b - blur3[2]) * k).coerceIn(0f, 1f)
                }
                if (ultra != 0f) {
                    val delta = (luma - wideL) * ultra * 1.5f
                    r += delta; g += delta; b += delta
                }
                if (clarity != 0f) {
                    val mask = 1f - abs(2f * luma - 1f)
                    val delta = (luma - wideL) * clarity * 1.5f * mask
                    r += delta; g += delta; b += delta
                }
                if (midtone != 0f) {
                    val mask = 1f - abs(2f * luma - 1f)
                    val delta = (luma - wideL) * midtone * 1.2f * mask
                    r += delta; g += delta; b += delta
                }
                if (dehaze != 0f) {
                    if (dehaze > 0f) {
                        val veil = wideL * 0.5f * dehaze
                        val transmission = max(1f - veil, 0.2f)
                        r = (r - veil * 0.6f) / transmission
                        g = (g - veil * 0.6f) / transmission
                        b = (b - veil * 0.6f) / transmission
                    } else {
                        val k = -dehaze
                        r = r * (1f - 0.15f * k) + 0.10f * k
                        g = g * (1f - 0.15f * k) + 0.10f * k
                        b = b * (1f - 0.15f * k) + 0.10f * k
                    }
                }
                out[i] = PixelMath.pack(
                    r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f),
                    src ushr 24,
                )
            }
        }
        return out
    }

    private fun colorBlur3(
        input: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Float,
        out: FloatArray,
        scratch: FloatArray,
    ) {
        var r = 0f; var g = 0f; var b = 0f
        for (dy in -1..1) for (dx in -1..1) {
            val w = (if (dx == 0) 2f else 1f) * (if (dy == 0) 2f else 1f) / 16f
            sampleArgb(
                input, width, height,
                x + 0.5f + dx * radius, y + 0.5f + dy * radius, scratch,
            )
            r += scratch[0] * w; g += scratch[1] * w; b += scratch[2] * w
        }
        out[0] = r; out[1] = g; out[2] = b
    }

    // ---------------------------------------------------------------- blur/refocus pass
    private fun blurPass(input: IntArray, width: Int, height: Int): IntArray {
        val b = params.blur
        val gaussian = b.gaussian / 100f
        val mist = b.mist / 100f
        val glow = b.glow / 100f
        val vignette = b.vignette / 100f
        val zoom = b.zoom / 100f
        val radial = b.radial / 100f
        val directional = b.directional / 100f
        val refocusAmount = params.refocus.amount / 100f
        val focusPoint = params.refocus.focusPoint / 100f
        val focusRange = params.refocus.focusRange / 100f
        val minDim = min(width, height)
        // Keep these radii/taps aligned with ADJUST_BLUR_AGSL.
        val gaussianRadius = max((0.002f + 0.03f * gaussian) * minDim, 1f)
        val refocusRadius = max(0.02f * minDim * refocusAmount, 1f)
        val wideRadius = max(minDim * 0.04f, 2f)
        val out = IntArray(input.size)
        PixelRunner.forEachRow(height) { y ->
            val v = (y + 0.5f) / height
            val sample = FloatArray(3)
            val brightScratch = FloatArray(3)
            for (x in 0 until width) {
                val i = y * width + x
                val src = input[i]
                var r = PixelMath.red(src)
                var g = PixelMath.green(src)
                var bl = PixelMath.blue(src)
                val alpha = src ushr 24

                if (gaussian != 0f) {
                    blur9(input, width, height, x, y, gaussianRadius, sample)
                    r = PixelMath.mix(r, sample[0], gaussian)
                    g = PixelMath.mix(g, sample[1], gaussian)
                    bl = PixelMath.mix(bl, sample[2], gaussian)
                }
                if (mist != 0f || glow != 0f) {
                    val bright = brightBlur9(input, width, height, x, y, wideRadius, brightScratch)
                    if (mist != 0f) {
                        r = screen(r, bright * mist)
                        g = screen(g, bright * 0.82f * mist)
                        bl = screen(bl, bright * 0.65f * mist)
                    }
                    if (glow != 0f) {
                        val k = bright * glow
                        r = screen(r, k); g = screen(g, k); bl = screen(bl, k)
                    }
                }
                if (vignette != 0f) {
                    val dx = (x + 0.5f) / width - 0.5f
                    val dy = v - 0.5f
                    val mask = PixelMath.smoothstep(0.35f, 0.95f, sqrt(dx * dx + dy * dy) * 1.2f)
                    blur9(input, width, height, x, y, wideRadius, sample)
                    val k = vignette * mask
                    r = PixelMath.mix(r, sample[0], k)
                    g = PixelMath.mix(g, sample[1], k)
                    bl = PixelMath.mix(bl, sample[2], k)
                }
                if (refocusAmount != 0f) {
                    val band = 1f - PixelMath.smoothstep(
                        focusRange * 0.25f,
                        focusRange * 0.25f + 0.08f,
                        abs(v - focusPoint),
                    )
                    blur9(input, width, height, x, y, refocusRadius, sample)
                    val k = (1f - band) * refocusAmount
                    r = PixelMath.mix(r, sample[0], k)
                    g = PixelMath.mix(g, sample[1], k)
                    bl = PixelMath.mix(bl, sample[2], k)
                }
                if (zoom != 0f) {
                    sampleRadial(input, width, height, x, y, zoom * 0.06f, sample)
                    r = PixelMath.mix(r, sample[0], 0.75f)
                    g = PixelMath.mix(g, sample[1], 0.75f)
                    bl = PixelMath.mix(bl, sample[2], 0.75f)
                }
                if (radial != 0f) {
                    sampleRotated(input, width, height, x, y, radial * 0.08f, sample)
                    r = PixelMath.mix(r, sample[0], 0.75f)
                    g = PixelMath.mix(g, sample[1], 0.75f)
                    bl = PixelMath.mix(bl, sample[2], 0.75f)
                }
                if (directional != 0f) {
                    sampleDirectional(input, width, height, x, y, directional * 0.05f * minDim, sample)
                    r = PixelMath.mix(r, sample[0], 0.75f)
                    g = PixelMath.mix(g, sample[1], 0.75f)
                    bl = PixelMath.mix(bl, sample[2], 0.75f)
                }
                out[i] = PixelMath.pack(
                    r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), bl.coerceIn(0f, 1f), alpha,
                )
            }
        }
        return out
    }

    private fun blur9(
        input: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Float,
        out: FloatArray,
    ) {
        sampleArgb(input, width, height, x + 0.5f, y + 0.5f, out)
        var r = out[0] * 0.2f; var g = out[1] * 0.2f; var b = out[2] * 0.2f
        val offsets = floatArrayOf(
            radius, 0f, -radius, 0f, 0f, radius, 0f, -radius,
            radius * 0.7f, radius * 0.7f, -radius * 0.7f, radius * 0.7f,
            radius * 0.7f, -radius * 0.7f, -radius * 0.7f, -radius * 0.7f,
        )
        var k = 0
        while (k < offsets.size) {
            sampleArgb(
                input, width, height,
                x + 0.5f + offsets[k], y + 0.5f + offsets[k + 1], out,
            )
            r += out[0] * 0.1f; g += out[1] * 0.1f; b += out[2] * 0.1f
            k += 2
        }
        out[0] = r; out[1] = g; out[2] = b
    }

    private fun brightAt(
        input: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        scratch: FloatArray,
    ): Float {
        sampleArgb(input, width, height, x, y, scratch)
        val luma = PixelMath.luma(scratch[0], scratch[1], scratch[2])
        return ((luma - 0.55f) / 0.45f).coerceIn(0f, 1f)
    }

    private fun brightBlur9(
        input: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Float,
        scratch: FloatArray,
    ): Float {
        var acc = brightAt(input, width, height, x + 0.5f, y + 0.5f, scratch) * 0.2f
        val offsets = floatArrayOf(
            radius, 0f, -radius, 0f, 0f, radius, 0f, -radius,
            radius * 0.7f, radius * 0.7f, -radius * 0.7f, radius * 0.7f,
            radius * 0.7f, -radius * 0.7f, -radius * 0.7f, -radius * 0.7f,
        )
        var k = 0
        while (k < offsets.size) {
            acc += brightAt(
                input, width, height,
                x + 0.5f + offsets[k], y + 0.5f + offsets[k + 1], scratch,
            ) * 0.1f
            k += 2
        }
        return acc
    }

    // ---------------------------------------------------------------- finish pass
    private fun finishPass(input: IntArray, width: Int, height: Int, seed: Long): IntArray {
        val st = params.stylize
        val opt = params.optical
        val fisheye = opt.fisheye / 100f
        val rgbSplit = opt.rgbSplit / 100f
        val aberration = opt.aberration / 100f
        val grain = st.grain / 100f
        val dust = st.dust / 100f
        val vignette = st.vignette / 100f
        val vintage = st.vintage / 100f
        val grainSeed = params.grainSeed(seed)
        val minDim = min(width, height)
        val out = IntArray(input.size)
        val sample = FloatArray(3)
        PixelRunner.forEachRow(height) { y ->
            val v = (y + 0.5f) / height
            for (x in 0 until width) {
                val i = y * width + x
                val src = input[i]
                var r = PixelMath.red(src)
                var g = PixelMath.green(src)
                var b = PixelMath.blue(src)

                if (fisheye != 0f) {
                    val u = (x + 0.5f) / width - 0.5f
                    val w = v - 0.5f
                    val r2 = u * u + w * w
                    val f = 1f + 0.5f * fisheye * r2
                    val sx = (0.5f + u * f) * width
                    val sy = (0.5f + w * f) * height
                    sampleArgb(input, width, height, sx, sy, sample)
                    r = sample[0]; g = sample[1]; b = sample[2]
                }
                if (rgbSplit != 0f) {
                    val dx = ((x + 0.5f) / width - 0.5f) * 0.012f * rgbSplit
                    val dy = (v - 0.5f) * 0.012f * rgbSplit
                    sampleArgb(input, width, height, x + 0.5f + dx * width, y + 0.5f + dy * height, sample)
                    r = sample[0]
                    sampleArgb(input, width, height, x + 0.5f - dx * width, y + 0.5f - dy * height, sample)
                    b = sample[2]
                }
                if (aberration != 0f) {
                    val dx = ((x + 0.5f) / width - 0.5f)
                    val dy = (v - 0.5f)
                    val scale = 0.006f * aberration
                    sampleArgb(input, width, height, x + 0.5f + dx * width * scale, y + 0.5f + dy * height * scale, sample)
                    r = sample[0]
                    sampleArgb(input, width, height, x + 0.5f - dx * width * scale, y + 0.5f - dy * height * scale, sample)
                    b = sample[2]
                }
                if (vintage != 0f) {
                    val k = vintage
                    val warm = PixelMath.smoothstep(0.35f, 1f, PixelMath.luma(r, g, b))
                    r = r * (1f - 0.10f * k) + 0.10f * k + 0.04f * k * warm
                    g = g * (1f - 0.08f * k) + 0.08f * k
                    b = b * (1f - 0.06f * k) + 0.10f * k
                    val luma = PixelMath.luma(r, g, b)
                    val shadow = 1f - PixelMath.smoothstep(0f, 0.55f, luma)
                    r = PixelMath.mix(luma, r, 1f - 0.22f * k * shadow)
                    g = PixelMath.mix(luma, g, 1f - 0.22f * k * shadow)
                    b = PixelMath.mix(luma, b, 1f - 0.22f * k * shadow)
                }
                if (grain != 0f) {
                    val luma = PixelMath.luma(r, g, b).coerceIn(0f, 1f)
                    val response = 0.35f + 0.65f * sqrt(4f * luma * (1f - luma))
                    val noise = FilterGrain.noise(x, y, width, height, grainSeed, 1.7f) *
                        grain * 0.14f * response
                    r = (r + noise).coerceIn(0f, 1f)
                    g = (g + noise).coerceIn(0f, 1f)
                    b = (b + noise).coerceIn(0f, 1f)
                }
                if (dust != 0f) {
                    val n = FilterGrain.noise(x, y, width, height, grainSeed + 53, 1.0f)
                    if (abs(n) > 0.90f - 0.03f * abs(dust)) {
                        val ink = if (dust > 0f) 0.96f else 0.04f
                        val k = (abs(dust) * 0.85f).coerceIn(0f, 1f)
                        r = PixelMath.mix(r, ink, k)
                        g = PixelMath.mix(g, ink, k)
                        b = PixelMath.mix(b, ink, k)
                    }
                }
                if (vignette != 0f) {
                    val dx = (x + 0.5f) / width - 0.5f
                    val dy = v - 0.5f
                    val mask = PixelMath.smoothstep(0.35f, 0.95f, sqrt(dx * dx + dy * dy) * 1.2f)
                    if (vignette > 0f) {
                        val k = 1f - 0.55f * vignette * mask
                        r *= k; g *= k; b *= k
                    } else {
                        val k = 1f + 0.45f * (-vignette) * mask
                        r = 1f - (1f - r) * k
                        g = 1f - (1f - g) * k
                        b = 1f - (1f - b) * k
                    }
                }
                out[i] = PixelMath.pack(
                    r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), src ushr 24,
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------- sampling helpers
    private fun sampleArgb(input: IntArray, width: Int, height: Int, x: Float, y: Float, out: FloatArray) {
        // Inputs are continuous output coordinates (pixel centres at x + 0.5); the sampler's
        // integer coordinates address texel centres, matching BitmapShader LINEAR sampling.
        val px = (x - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val py = (y - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = px.toInt().coerceIn(0, width - 1)
        val y0 = py.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = px - x0
        val fy = py - y0
        val p00 = input[y0 * width + x0]
        val p10 = input[y0 * width + x1]
        val p01 = input[y1 * width + x0]
        val p11 = input[y1 * width + x1]
        for (channel in 0..2) {
            val shift = 16 - channel * 8
            val a = ((p00 ushr shift) and 255) / 255f
            val b = ((p10 ushr shift) and 255) / 255f
            val c = ((p01 ushr shift) and 255) / 255f
            val d = ((p11 ushr shift) and 255) / 255f
            val top = a + (b - a) * fx
            val bottom = c + (d - c) * fx
            out[channel] = top + (bottom - top) * fy
        }
    }

    private fun sampleRadial(input: IntArray, width: Int, height: Int, x: Int, y: Int, amount: Float, out: FloatArray) {
        var r = 0f; var g = 0f; var b = 0f
        val cx = width * 0.5f
        val cy = height * 0.5f
        for (tap in 0 until 8) {
            val t = (tap + 1) / 8f * amount
            val sx = (x + 0.5f) + (x + 0.5f - cx) * t
            val sy = (y + 0.5f) + (y + 0.5f - cy) * t
            sampleArgb(input, width, height, sx, sy, out)
            r += out[0]; g += out[1]; b += out[2]
        }
        out[0] = r / 8f; out[1] = g / 8f; out[2] = b / 8f
    }

    private fun sampleRotated(input: IntArray, width: Int, height: Int, x: Int, y: Int, amount: Float, out: FloatArray) {
        var r = 0f; var g = 0f; var b = 0f
        val cx = width * 0.5f
        val cy = height * 0.5f
        val dx = x + 0.5f - cx
        val dy = y + 0.5f - cy
        for (tap in 0 until 8) {
            val angle = (tap + 1) / 8f * amount
            val cosA = cos(angle)
            val sinA = sin(angle)
            val sx = cx + dx * cosA - dy * sinA
            val sy = cy + dx * sinA + dy * cosA
            sampleArgb(input, width, height, sx, sy, out)
            r += out[0]; g += out[1]; b += out[2]
        }
        out[0] = r / 8f; out[1] = g / 8f; out[2] = b / 8f
    }

    private fun sampleDirectional(input: IntArray, width: Int, height: Int, x: Int, y: Int, length: Float, out: FloatArray) {
        var r = 0f; var g = 0f; var b = 0f
        val angle = 35.0 * PI / 180.0
        val dx = cos(angle).toFloat() * length
        val dy = sin(angle).toFloat() * length
        for (tap in 0 until 8) {
            val t = (tap + 1) / 8f - 0.5f
            sampleArgb(input, width, height, x + 0.5f + dx * t, y + 0.5f + dy * t, out)
            r += out[0]; g += out[1]; b += out[2]
        }
        out[0] = r / 8f; out[1] = g / 8f; out[2] = b / 8f
    }

    private fun screen(a: Float, b: Float): Float = 1f - (1f - a) * (1f - b)

    private fun angularDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    private fun rgbToHsl(r: Float, g: Float, b: Float, out: FloatArray) {
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val l = (maxV + minV) * 0.5f
        if (maxV - minV < 0.00001f) {
            out[0] = 0f; out[1] = 0f; out[2] = l
            return
        }
        val d = maxV - minV
        val s = if (l > 0.5f) d / (2f - maxV - minV) else d / (maxV + minV)
        val h = when (maxV) {
            r -> ((g - b) / d + if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        out[0] = h; out[1] = s; out[2] = l
    }

    private fun hslToRgb(h: Float, s: Float, l: Float, out: FloatArray) {
        if (s <= 0.00001f) {
            out[0] = l; out[1] = l; out[2] = l
            return
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        val hk = ((h % 360f) + 360f) % 360f / 360f
        out[0] = hueToRgb(p, q, hk + 1f / 3f)
        out[1] = hueToRgb(p, q, hk)
        out[2] = hueToRgb(p, q, hk - 1f / 3f)
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var x = t
        if (x < 0f) x += 1f
        if (x > 1f) x -= 1f
        return when {
            x < 1f / 6f -> p + (q - p) * 6f * x
            x < 1f / 2f -> q
            x < 2f / 3f -> p + (q - p) * (2f / 3f - x) * 6f
            else -> p
        }
    }
}

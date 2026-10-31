package com.aliahad.photoeffects.adjust

import com.aliahad.photoeffects.effect.EffectSeed

data class AdjustLight(
    val exposure: Int = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val blackPoint: Int = 0,
    val fade: Int = 0,
    val brilliance: Int = 0,
    val pushPull: Int = 0,
    val cleanup: Int = 0,
)

data class AdjustColor(
    val saturation: Int = 0,
    val vibrance: Int = 0,
    val colorDensity: Int = 0,
    val temperature: Int = 0,
    val tint: Int = 0,
    val skintone: Int = 0,
    val hdr: Int = 0,
)

data class HslBandParams(
    val hue: Int = 0,
    val saturation: Int = 0,
    val lightness: Int = 0,
) {
    fun value(component: HslComponent): Int = when (component) {
        HslComponent.Hue -> hue
        HslComponent.Saturation -> saturation
        HslComponent.Lightness -> lightness
    }

    fun with(component: HslComponent, value: Int): HslBandParams = when (component) {
        HslComponent.Hue -> copy(hue = value)
        HslComponent.Saturation -> copy(saturation = value)
        HslComponent.Lightness -> copy(lightness = value)
    }
}

data class AdjustHsl(
    val red: HslBandParams = HslBandParams(),
    val orange: HslBandParams = HslBandParams(),
    val yellow: HslBandParams = HslBandParams(),
    val green: HslBandParams = HslBandParams(),
    val blue: HslBandParams = HslBandParams(),
    val purple: HslBandParams = HslBandParams(),
) {
    fun band(band: HslBand): HslBandParams = when (band) {
        HslBand.Red -> red
        HslBand.Orange -> orange
        HslBand.Yellow -> yellow
        HslBand.Green -> green
        HslBand.Blue -> blue
        HslBand.Purple -> purple
    }

    fun with(band: HslBand, component: HslComponent, value: Int): AdjustHsl = when (band) {
        HslBand.Red -> copy(red = red.with(component, value))
        HslBand.Orange -> copy(orange = orange.with(component, value))
        HslBand.Yellow -> copy(yellow = yellow.with(component, value))
        HslBand.Green -> copy(green = green.with(component, value))
        HslBand.Blue -> copy(blue = blue.with(component, value))
        HslBand.Purple -> copy(purple = purple.with(component, value))
    }
}

data class AdjustDetails(
    val sharpen: Int = 0,
    val ultrasharp: Int = 0,
    val clarity: Int = 0,
    val dehaze: Int = 0,
    val midtoneDetails: Int = 0,
)

data class AdjustStylize(
    val grain: Int = 0,
    val dust: Int = 0,
    val vignette: Int = 0,
    val vintage: Int = 0,
)

data class AdjustRefocus(
    val amount: Int = 0,
    val focusPoint: Int = 50,
    val focusRange: Int = 50,
)

data class AdjustOptical(
    val fisheye: Int = 0,
    val rgbSplit: Int = 0,
    val aberration: Int = 0,
)

data class AdjustBlur(
    val mist: Int = 0,
    val glow: Int = 0,
    val gaussian: Int = 0,
    val vignette: Int = 0,
    val zoom: Int = 0,
    val radial: Int = 0,
    val directional: Int = 0,
)

/** Sticky pass bits so first use of a spatial control rebuilds topology at most once. */
data class AdjustLayers(
    val detail: Boolean = false,
    val blur: Boolean = false,
) {
    fun with(pass: AdjustPass, active: Boolean): AdjustLayers = when (pass) {
        AdjustPass.Detail -> copy(detail = detail || active)
        AdjustPass.Blur -> copy(blur = blur || active)
        else -> this
    }
}

/**
 * One immutable Adjust value set. All 57 controls are grouped for readability; [value] and
 * [with] are the only generic accessors used by the UI and renderers.
 */
data class AdjustParams(
    val light: AdjustLight = AdjustLight(),
    val color: AdjustColor = AdjustColor(),
    val hsl: AdjustHsl = AdjustHsl(),
    val details: AdjustDetails = AdjustDetails(),
    val stylize: AdjustStylize = AdjustStylize(),
    val refocus: AdjustRefocus = AdjustRefocus(),
    val optical: AdjustOptical = AdjustOptical(),
    val blur: AdjustBlur = AdjustBlur(),
    val layers: AdjustLayers = AdjustLayers(),
) {
    val isIdentity: Boolean
        get() = AdjustControl.entries.all { value(it) == it.neutral }

    val editedControlCount: Int
        get() = AdjustControl.entries.count { value(it) != it.neutral }

    fun value(control: AdjustControl): Int = when (control) {
        AdjustControl.Exposure -> light.exposure
        AdjustControl.Brightness -> light.brightness
        AdjustControl.Contrast -> light.contrast
        AdjustControl.Highlights -> light.highlights
        AdjustControl.Shadows -> light.shadows
        AdjustControl.BlackPoint -> light.blackPoint
        AdjustControl.Fade -> light.fade
        AdjustControl.Brilliance -> light.brilliance
        AdjustControl.PushPull -> light.pushPull
        AdjustControl.Cleanup -> light.cleanup
        AdjustControl.Saturation -> color.saturation
        AdjustControl.Vibrance -> color.vibrance
        AdjustControl.ColorDensity -> color.colorDensity
        AdjustControl.Temperature -> color.temperature
        AdjustControl.Tint -> color.tint
        AdjustControl.Skintone -> color.skintone
        AdjustControl.Hdr -> color.hdr
        AdjustControl.Sharpen -> details.sharpen
        AdjustControl.Ultrasharp -> details.ultrasharp
        AdjustControl.Clarity -> details.clarity
        AdjustControl.Dehaze -> details.dehaze
        AdjustControl.MidtoneDetails -> details.midtoneDetails
        AdjustControl.Grain -> stylize.grain
        AdjustControl.Dust -> stylize.dust
        AdjustControl.Vignette -> stylize.vignette
        AdjustControl.Vintage -> stylize.vintage
        AdjustControl.RefocusAmount -> refocus.amount
        AdjustControl.FocusPoint -> refocus.focusPoint
        AdjustControl.FocusRange -> refocus.focusRange
        AdjustControl.Fisheye -> optical.fisheye
        AdjustControl.RgbSplit -> optical.rgbSplit
        AdjustControl.Aberration -> optical.aberration
        AdjustControl.Mist -> blur.mist
        AdjustControl.Glow -> blur.glow
        AdjustControl.GaussianBlur -> blur.gaussian
        AdjustControl.VignetteBlur -> blur.vignette
        AdjustControl.ZoomBlur -> blur.zoom
        AdjustControl.RadialBlur -> blur.radial
        AdjustControl.DirectionalBlur -> blur.directional
        else -> {
            val band = AdjustControl.hslBandOf(control)
            val component = AdjustControl.hslComponentOf(control)
            if (band == null || component == null) 0 else hsl.band(band).value(component)
        }
    }

    fun with(control: AdjustControl, value: Int): AdjustParams {
        val v = value.coerceIn(control.range)
        val next = when (control) {
            AdjustControl.Exposure -> copy(light = light.copy(exposure = v))
            AdjustControl.Brightness -> copy(light = light.copy(brightness = v))
            AdjustControl.Contrast -> copy(light = light.copy(contrast = v))
            AdjustControl.Highlights -> copy(light = light.copy(highlights = v))
            AdjustControl.Shadows -> copy(light = light.copy(shadows = v))
            AdjustControl.BlackPoint -> copy(light = light.copy(blackPoint = v))
            AdjustControl.Fade -> copy(light = light.copy(fade = v))
            AdjustControl.Brilliance -> copy(light = light.copy(brilliance = v))
            AdjustControl.PushPull -> copy(light = light.copy(pushPull = v))
            AdjustControl.Cleanup -> copy(light = light.copy(cleanup = v))
            AdjustControl.Saturation -> copy(color = color.copy(saturation = v))
            AdjustControl.Vibrance -> copy(color = color.copy(vibrance = v))
            AdjustControl.ColorDensity -> copy(color = color.copy(colorDensity = v))
            AdjustControl.Temperature -> copy(color = color.copy(temperature = v))
            AdjustControl.Tint -> copy(color = color.copy(tint = v))
            AdjustControl.Skintone -> copy(color = color.copy(skintone = v))
            AdjustControl.Hdr -> copy(color = color.copy(hdr = v))
            AdjustControl.Sharpen -> copy(details = details.copy(sharpen = v))
            AdjustControl.Ultrasharp -> copy(details = details.copy(ultrasharp = v))
            AdjustControl.Clarity -> copy(details = details.copy(clarity = v))
            AdjustControl.Dehaze -> copy(details = details.copy(dehaze = v))
            AdjustControl.MidtoneDetails -> copy(details = details.copy(midtoneDetails = v))
            AdjustControl.Grain -> copy(stylize = stylize.copy(grain = v))
            AdjustControl.Dust -> copy(stylize = stylize.copy(dust = v))
            AdjustControl.Vignette -> copy(stylize = stylize.copy(vignette = v))
            AdjustControl.Vintage -> copy(stylize = stylize.copy(vintage = v))
            AdjustControl.RefocusAmount -> copy(refocus = refocus.copy(amount = v))
            AdjustControl.FocusPoint -> copy(refocus = refocus.copy(focusPoint = v))
            AdjustControl.FocusRange -> copy(refocus = refocus.copy(focusRange = v))
            AdjustControl.Fisheye -> copy(optical = optical.copy(fisheye = v))
            AdjustControl.RgbSplit -> copy(optical = optical.copy(rgbSplit = v))
            AdjustControl.Aberration -> copy(optical = optical.copy(aberration = v))
            AdjustControl.Mist -> copy(blur = blur.copy(mist = v))
            AdjustControl.Glow -> copy(blur = blur.copy(glow = v))
            AdjustControl.GaussianBlur -> copy(blur = blur.copy(gaussian = v))
            AdjustControl.VignetteBlur -> copy(blur = blur.copy(vignette = v))
            AdjustControl.ZoomBlur -> copy(blur = blur.copy(zoom = v))
            AdjustControl.RadialBlur -> copy(blur = blur.copy(radial = v))
            AdjustControl.DirectionalBlur -> copy(blur = blur.copy(directional = v))
            else -> {
                val band = AdjustControl.hslBandOf(control)
                val component = AdjustControl.hslComponentOf(control)
                if (band == null || component == null) this
                else copy(hsl = hsl.with(band, component, v))
            }
        }
        return next.copy(layers = next.layers.with(control.pass, v != control.neutral))
    }

    /** Reset one category, clearing spatial layer bits when no controls in that layer remain. */
    fun reset(category: AdjustCategory?): AdjustParams {
        val base = if (category == null) {
            AdjustParams()
        } else {
            var next = this
            for (control in AdjustControl.byCategory(category)) {
                next = next.with(control, control.neutral)
            }
            next
        }
        val detailActive = AdjustControl.entries.any {
            it.pass == AdjustPass.Detail && base.value(it) != it.neutral
        }
        val blurActive = AdjustControl.entries.any {
            it.pass == AdjustPass.Blur && base.value(it) != it.neutral
        }
        return base.copy(layers = AdjustLayers(detailActive, blurActive))
    }

    fun scalarUniforms(): Map<String, Float> = mapOf(
        "uExposure" to light.exposure.norm(),
        "uBrightness" to light.brightness.norm(),
        "uContrast" to light.contrast.norm(),
        "uHighlights" to light.highlights.norm(),
        "uShadows" to light.shadows.norm(),
        "uBlackPoint" to light.blackPoint.norm(),
        "uFade" to light.fade.norm(),
        "uBrilliance" to light.brilliance.norm(),
        "uPushPull" to light.pushPull.norm(),
        "uCleanup" to light.cleanup.norm(),
        "uSaturation" to color.saturation.norm(),
        "uVibrance" to color.vibrance.norm(),
        "uColorDensity" to color.colorDensity.norm(),
        "uTemperature" to color.temperature.norm(),
        "uTint" to color.tint.norm(),
        "uSkintone" to color.skintone.norm(),
        "uHdr" to color.hdr.norm(),
        "uSharpen" to details.sharpen.norm(),
        "uUltrasharp" to details.ultrasharp.norm(),
        "uClarity" to details.clarity.norm(),
        "uDehaze" to details.dehaze.norm(),
        "uMidtoneDetails" to details.midtoneDetails.norm(),
        "uGrain" to stylize.grain.norm(),
        "uGrainSize" to 1.7f,
        "uDust" to stylize.dust.norm(),
        "uVignette" to stylize.vignette.norm(),
        "uVintage" to stylize.vintage.norm(),
        "uRefocus" to refocus.amount.norm(),
        "uFocusPoint" to refocus.focusPoint.norm(),
        "uFocusRange" to refocus.focusRange.norm(),
        "uFisheye" to optical.fisheye.norm(),
        "uRgbSplit" to optical.rgbSplit.norm(),
        "uAberration" to optical.aberration.norm(),
        "uMist" to blur.mist.norm(),
        "uGlow" to blur.glow.norm(),
        "uGaussianBlur" to blur.gaussian.norm(),
        "uVignetteBlur" to blur.vignette.norm(),
        "uZoomBlur" to blur.zoom.norm(),
        "uRadialBlur" to blur.radial.norm(),
        "uDirectionalBlur" to blur.directional.norm(),
        "uSeed" to 0f,
    )

    /** Six `vec3(hue, saturation, lightness)` uniforms for the HSL color pass. */
    fun hslUniforms(): Map<String, FloatArray> = mapOf(
        "uHslRed" to hsl.red.vector(),
        "uHslOrange" to hsl.orange.vector(),
        "uHslYellow" to hsl.yellow.vector(),
        "uHslGreen" to hsl.green.vector(),
        "uHslBlue" to hsl.blue.vector(),
        "uHslPurple" to hsl.purple.vector(),
    )

    fun grainSeed(seed: Long): Int = EffectSeed.of(seed, 0)

    private fun Int.norm(): Float = this / 100f

    private fun HslBandParams.vector(): FloatArray =
        floatArrayOf(hue / 100f, saturation / 100f, lightness / 100f)
}

package com.aliahad.photoeffects.adjust

/** Shared uniform names for the CPU/AGSL Adjust passes. */
object AdjustUniforms {
    const val INTENSITY = "uIntensity"
    const val RESOLUTION = "uResolution"
    const val SEED = "uSeed"

    const val EXPOSURE = "uExposure"
    const val BRIGHTNESS = "uBrightness"
    const val CONTRAST = "uContrast"
    const val HIGHLIGHTS = "uHighlights"
    const val SHADOWS = "uShadows"
    const val BLACK_POINT = "uBlackPoint"
    const val FADE = "uFade"
    const val BRILLIANCE = "uBrilliance"
    const val PUSH_PULL = "uPushPull"
    const val CLEANUP = "uCleanup"

    const val SATURATION = "uSaturation"
    const val VIBRANCE = "uVibrance"
    const val COLOR_DENSITY = "uColorDensity"
    const val TEMPERATURE = "uTemperature"
    const val TINT = "uTint"
    const val SKINTONE = "uSkintone"
    const val HDR = "uHdr"

    const val SHARPEN = "uSharpen"
    const val ULTRASHARP = "uUltrasharp"
    const val CLARITY = "uClarity"
    const val DEHAZE = "uDehaze"
    const val MIDTONE_DETAILS = "uMidtoneDetails"

    const val GRAIN = "uGrain"
    const val GRAIN_SIZE = "uGrainSize"
    const val DUST = "uDust"
    const val VIGNETTE = "uVignette"
    const val VINTAGE = "uVintage"

    const val REFOCUS = "uRefocus"
    const val FOCUS_POINT = "uFocusPoint"
    const val FOCUS_RANGE = "uFocusRange"

    const val FISHEYE = "uFisheye"
    const val RGB_SPLIT = "uRgbSplit"
    const val ABERRATION = "uAberration"

    const val MIST = "uMist"
    const val GLOW = "uGlow"
    const val GAUSSIAN_BLUR = "uGaussianBlur"
    const val VIGNETTE_BLUR = "uVignetteBlur"
    const val ZOOM_BLUR = "uZoomBlur"
    const val RADIAL_BLUR = "uRadialBlur"
    const val DIRECTIONAL_BLUR = "uDirectionalBlur"

    val HSL = listOf(
        "uHslRed", "uHslOrange", "uHslYellow", "uHslGreen", "uHslBlue", "uHslPurple",
    )
}

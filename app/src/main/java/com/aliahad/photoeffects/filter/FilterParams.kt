package com.aliahad.photoeffects.filter

/**
 * An original colour recipe consumed by both renderers. The catalogue supplies the artistic
 * parameters; the shared CPU/AGSL pipeline supplies the processing operations. Values use
 * normalized display-referred RGB, with exposure expressed as a linear multiplier.
 */
data class FilterParams(
    val id: String,
    val family: String,
    val variant: Int,
    val exposure: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val black: Float = 0f,
    val white: Float = 1f,
    val gainR: Float = 1f,
    val gainG: Float = 1f,
    val gainB: Float = 1f,
    val shadowR: Float = 0f,
    val shadowG: Float = 0f,
    val shadowB: Float = 0f,
    val highlightR: Float = 0f,
    val highlightG: Float = 0f,
    val highlightB: Float = 0f,
    val sharpen: Int = 20,
    /** Colour strength blends the grade independently of sharpening. */
    val strength: Float = 1f,
    val summary: String = "colour grade",
    /** Directional soft-focus amount; independent of colour strength and grain. */
    val intensity: Int = 100,
    /** Horizontal triangular half-width in pixels at a 1600-pixel image width. */
    val softFocus: Float = 0f,
    val focusRadiusCurve: Float = 0f,
    val focusSpread: Float = 0f,
    val focusSpreadGrowth: Float = 0f,
    val focusDetail: Float = 0f,
    val focusDetailRadius: Float = 0f,
    val focusLift: Float = 0f,
    val grain: Int = 0,
    val grainSize: Float = 1.7f,
    val grainSeed: Int = 0,
    /** Signed luminance-selective adjustments, independent of colour strength and grain. */
    val highlights: Int = 0,
    val shadows: Int = 0,
    val aberration: Int = 0,
    /** Maximum red/blue radial displacement at a side edge of a 1600-pixel-wide image. */
    val aberrationRadius: Float = 16f,
    val controls: List<FilterControl> = listOf(FilterControl.Strength, FilterControl.Sharpen),
) {
    val displayName: String get() = "$family #$variant"

    fun uniforms(): Map<String, Float> = mapOf(
        "uExposure" to exposure, "uContrast" to contrast, "uSaturation" to saturation,
        "uBlack" to black, "uWhite" to white,
        "uGainR" to gainR, "uGainG" to gainG, "uGainB" to gainB,
        "uShadowR" to shadowR, "uShadowG" to shadowG, "uShadowB" to shadowB,
        "uHighlightR" to highlightR, "uHighlightG" to highlightG, "uHighlightB" to highlightB,
        "uSharpen" to sharpen.coerceIn(0, 100) / 100f,
        "uFilterStrength" to strength.coerceIn(0f, 1f),
        "uFilterIntensity" to intensity.coerceIn(0, 100) / 100f,
        "uSoftFocus" to softFocus,
        "uFocusRadiusCurve" to focusRadiusCurve,
        "uFocusSpread" to focusSpread,
        "uFocusSpreadGrowth" to focusSpreadGrowth,
        "uFocusDetail" to focusDetail,
        "uFocusDetailRadius" to focusDetailRadius,
        "uFocusLift" to focusLift,
        "uGrain" to grain.coerceIn(0, 100) / 100f,
        "uGrainSize" to grainSize,
        "uHighlights" to highlights.coerceIn(-100, 100) / 100f,
        "uShadows" to shadows.coerceIn(-100, 100) / 100f,
        "uAberration" to aberration.coerceIn(0, 100) / 100f,
        "uAberrationRadius" to aberrationRadius,
        "uSeed" to grainSeed.toFloat(),
    )
}

package com.aliahad.photoeffects.effect

/**
 * Parameters of the unified HALATION pipeline (PREQUEL "HALATION" family, 3 numbered variants).
 *
 * Mirrors PREQUEL's confirmed HALATION tool controls (`Intensity · Opacity · Blur · Hue · Grain`)
 * plus the recovered static recipe (soft-knee max-channel highlight extraction, 2–3 scale
 * separable Gaussian halo, additive tinted halo + optional warm haze screen, grade/grain/vignette,
 * then the effect intensity blend).
 *
 * The 3 variants are the same engine with a different [hue] (warm red-orange / cool cyan /
 * cool blue-violet) and their own intensity/opacity/blur/grain/[haloGain] values; [HalationFamily]
 * holds the measured per-variant rows.
 *
 * AGSL constraints on this project (no `uint`, no shift operators, no large-coordinate fract
 * hashing) are respected by [HALATION_AGSL] and [com.aliahad.photoeffects.effect.cpu.CpuHalationRenderer].
 */
data class HalationParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Per-variant fixed seed (201..203); combined with the photo seed at render time. */
    val seed: Int,
    // ---- halation engine (PREQUEL tool controls) ----
    /** Halo hue: 0 = warm red/orange, 0.5 = neutral, 1 = blue/violet (PREQUEL "Hue"). */
    val hue: Float = 0.0f,
    /** Max-channel highlight threshold (0..1) for the bright-pass. */
    val brightThreshold: Float = 0.68f,
    /** Soft-knee width (0..1) of the bright-pass (PREQUEL soft-knee recipe). */
    val softKnee: Float = 0.08f,
    /** Halo composite opacity (0..1) — PREQUEL "Opacity" subtool. */
    val opacity: Float = 0.55f,
    /**
     * Additive halo gain on top of [opacity], fitted per variant against the PREQUEL export
     * fringe energy (p99 / spread-fraction on the collar crop). Replaces the old shared 6.0.
     */
    val haloGain: Float = 6.0f,
    /** Halo blur amount (0..1): scales the 8/28/64·multiplier Gaussian radii. */
    val blur: Float = 0.55f,
    /** Optional warm haze screen amount (0..1, PREQUEL haze `#D7AA80`). */
    val haze: Float = 0.08f,
    // ---- film grain ----
    /** Film grain (0..1) — PREQUEL "Grain" subtool. */
    val grain: Float = 0.10f,
    // ---- base colour grade ----
    val warmth: Float = 0.0f,     // -1..1
    val tint: Float = 0.0f,       // -1 magenta .. +1 green
    val contrast: Float = 0.0f,   // -1..1
    val fade: Float = 0.0f,       // 0..1 lifted blacks
    val saturation: Float = 0.0f, // -1..1
    // ---- vignette ----
    val vignette: Float = 0.0f,   // 0..1
    /** Default main intensity slider (device measurement: 65/60/65 for variants 1/2/3). */
    val defaultIntensity: Int = 65,
) {
    /** Uniform payload pushed into the HALATION AGSL program. */
    fun toUniforms(): Map<String, Float> = mapOf(
        HalationUniforms.SEED to seed.toFloat(),
        HalationUniforms.HUE to hue.coerceIn(0f, 1f),
        HalationUniforms.BRIGHT_THRESHOLD to brightThreshold.coerceIn(0f, 1f),
        HalationUniforms.SOFT_KNEE to softKnee.coerceIn(0f, 1f),
        HalationUniforms.OPACITY to opacity.coerceIn(0f, 1f),
        HalationUniforms.HALO_GAIN to haloGain.coerceIn(0f, 32f),
        HalationUniforms.BLUR to blur.coerceIn(0f, 1f),
        HalationUniforms.HAZE to haze.coerceIn(0f, 1f),
        HalationUniforms.GRAIN to grain.coerceIn(0f, 1f),
        HalationUniforms.WARMTH to warmth.coerceIn(-1f, 1f),
        HalationUniforms.TINT to tint.coerceIn(-1f, 1f),
        HalationUniforms.CONTRAST to contrast.coerceIn(-1f, 1f),
        HalationUniforms.FADE to fade.coerceIn(0f, 1f),
        HalationUniforms.SATURATION to saturation.coerceIn(-1f, 1f),
        HalationUniforms.VIGNETTE to vignette.coerceIn(0f, 1f),
    )

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = buildString {
        append(
            when {
                hue < 0.25f -> "warm red halation"
                hue < 0.35f -> "neutral halation"
                hue <= 0.65f -> "cool cyan halation"
                else -> "cool blue halation"
            },
        )
        if (haze > 0.02f) append(" · haze")
        if (grain > 0.02f) append(" · grain")
        if (vignette > 0.02f) append(" · vignette")
    }
}

/** Uniform names shared by the HALATION AGSL shader and the CPU mirror. */
object HalationUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
    const val HUE = "uHue"
    const val BRIGHT_THRESHOLD = "uBrightThreshold"
    const val SOFT_KNEE = "uSoftKnee"
    const val OPACITY = "uOpacity"
    const val HALO_GAIN = "uHaloGain"
    const val BLUR = "uBlur"
    const val HAZE = "uHaze"
    const val GRAIN = "uGrain"
    const val WARMTH = "uWarmth"
    const val TINT = "uTint"
    const val CONTRAST = "uContrast"
    const val FADE = "uFade"
    const val SATURATION = "uSaturation"
    const val VIGNETTE = "uVignette"
}

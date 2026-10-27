package com.aliahad.photoeffects.effect

/**
 * All parameters of the unified "dusty" render pipeline (see
 * docs/prequel-research/spec/dusty-effects-spec.md §2).
 *
 * The same [DustyParams] object drives both renderers:
 *  - `CpuDustyRenderer` (deterministic Kotlin, used for thumbnails / tests / fallback)
 *  - the shared AGSL `RuntimeShader` (live GPU preview)
 *
 * Field ranges follow the spec. Retuning a variant = editing its parameters in
 * [EffectRegistry]; no renderer code changes are required.
 */
data class DustyParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Per-variant fixed seed (101..105); combined with the photo seed. */
    val seed: Int,
    // ---- color grade ----
    /**
     * Row-major 3x4 affine grade fitted per variant from PREQUEL same-photo exports:
     *   r' = g0*r + g1*g + g2*b + g3
     *   g' = g4*r + g5*g + g6*b + g7
     *   b' = g8*r + g9*g + g10*b + g11
     * Least-squares on the baseline->export pairs (verification report §4); keeps each
     * variant's measured cast (teal / blue / green / warm / brown) instead of a scalar warm tint.
     */
    val grade: List<Float> = IDENTITY_GRADE,
    // ---- optics ----
    val aberration: Float = 0f,    // 0..1 radial RGB fringing
    val blur: Float = 0f,         // 0..1 soft focus amount
    val halation: Float = 0f,     // 0..1 warm highlight bloom
    // ---- grain ----
    val grain: Float = 0f,        // 0..1 film grain strength (soft-light)
    // ---- dust ----
    val dust: Float = 0f,         // 0..1 dust amount
    val dustWhite: Boolean = true, // true = white specks, false = black
    val dustSize: Float = 1f,     // 0.5..3, smaller = finer specks
    val dustOpacity: Float = 0.7f, // 0.5..0.85
    // ---- scratches / texture ----
    val scratches: Float = 0f,    // 0..1
    val texture: Float = 0f,      // 0..1 paper/lint mottling
    // ---- vignette ----
    val vignette: Float = 0f,     // 0..1
    val defaultIntensity: Int = 100,
) {
    val dustColor: DustColor
        get() = if (dustWhite) DustColor.WHITE else DustColor.BLACK

    /** Uniform payload pushed into the AGSL shader (seed is combined per-photo by the renderer). */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(DustyUniforms.SEED, seed.toFloat())
        val g = if (grade.size == 12) grade else IDENTITY_GRADE
        for (i in 0 until 12) put(DustyUniforms.GRADE[i], g[i])
        put(DustyUniforms.ABERRATION, aberration.coerceIn(0f, 1f))
        put(DustyUniforms.BLUR, blur.coerceIn(0f, 1f))
        put(DustyUniforms.HALATION, halation.coerceIn(0f, 1f))
        put(DustyUniforms.GRAIN, grain.coerceIn(0f, 1f))
        put(DustyUniforms.DUST, dust.coerceIn(0f, 1f))
        put(DustyUniforms.DUST_WHITE, if (dustWhite) 1f else 0f)
        put(DustyUniforms.DUST_SIZE, dustSize.coerceIn(0.25f, 4f))
        put(DustyUniforms.DUST_OPACITY, dustOpacity.coerceIn(0f, 1f))
        put(DustyUniforms.SCRATCHES, scratches.coerceIn(0f, 1f))
        put(DustyUniforms.TEXTURE, texture.coerceIn(0f, 1f))
        put(DustyUniforms.VIGNETTE, vignette.coerceIn(0f, 1f))
    }

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = buildString {
        if (dust > 0.001f) {
            append(if (dustWhite) "white dust" else "black dust")
        } else {
            append("film grade")
        }
        if (grain > 0.02f) append(" · grain")
        if (scratches > 0.05f) append(" · scratches")
        if (halation > 0.05f) append(" · halation")
    }

    companion object {
        /** Identity affine grade (no colour change). */
        val IDENTITY_GRADE: List<Float> = listOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
        )
    }
}

/** Positive dust = white specks, negative = black specks (PREQUEL semantics). */
enum class DustColor { WHITE, BLACK }

/** Uniform names shared by the AGSL shader and the CPU mirror. */
object DustyUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
    /** 12 affine grade uniforms, row-major 3x4 (uGrade0..uGrade11). */
    val GRADE: List<String> = (0 until 12).map { "uGrade$it" }
    const val ABERRATION = "uAberration"
    const val BLUR = "uBlur"
    const val HALATION = "uHalation"
    const val GRAIN = "uGrain"
    const val DUST = "uDust"
    const val DUST_WHITE = "uDustWhite"
    const val DUST_SIZE = "uDustSize"
    const val DUST_OPACITY = "uDustOpacity"
    const val SCRATCHES = "uScratches"
    const val TEXTURE = "uTexture"
    const val VIGNETTE = "uVignette"
}

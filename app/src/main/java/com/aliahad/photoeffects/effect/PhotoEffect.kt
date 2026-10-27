package com.aliahad.photoeffects.effect

/** Deterministic CPU implementation of one effect on plain ARGB pixels. */
fun interface CpuEffect {
    /**
     * @param input non-premultiplied ARGB pixels, row major
     * @return a new ARGB buffer; [input] is never mutated
     *
     * Contract: intensity 0 returns an exact copy of [input]; identical arguments always
     * produce identical output (deterministic).
     */
    fun apply(input: IntArray, width: Int, height: Int, intensity: Float, seed: Long): IntArray
}

/**
 * GPU rendering program for one effect.
 *
 * The AGSL source declares `uniform shader content;` plus the float uniforms listed in
 * [uniforms]; the intensity/seed/resolution uniforms are pushed by the renderer on every draw
 * (uniform names are family-specific: `DustyUniforms` / `HalationUniforms`).
 */
data class GpuProgram(
    val agslSource: String,
    val uniforms: Map<String, Float> = emptyMap(),
    /**
     * Extra asset-backed shader inputs: uniform shader name -> app asset path.
     * Used by SENSUAL for the bundled PREQUEL `adj-Blur` mask (`uMask` -> `blurmask.jpg`);
     * the preview binds each asset as a BitmapShader input.
     */
    val extraShaderAssets: Map<String, String> = emptyMap(),
)

/** One pluggable effect: metadata + CPU/GPU renderers + a short parameter summary for the UI. */
data class PhotoEffect(
    val spec: EffectSpec,
    val cpu: CpuEffect,
    val gpu: GpuProgram,
    /** One-line parameter summary shown next to the effect name (e.g. "white dust · grain"). */
    val summary: String,
    /**
     * Dot-matrix stamp overlay metadata, when the family draws one (YESTERDAY's "Date" tool).
     * The CPU renderer rasterizes it into its output; the GPU preview draws it as a canvas
     * overlay on top of the AGSL result (see `PhotoPreviewCard`).
     */
    val stamp: DotMatrixDate.StampSpec? = null,
) {
    val id: String get() = spec.id
    val displayName: String get() = spec.displayName
    val defaultIntensity: Int get() = spec.defaultIntensity
}

/** Builds one DUSTY preset (shared AGSL shader + deterministic CPU mirror) from a parameter row. */
object DustyEffectFactory {
    fun create(
        params: DustyParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = DustyFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuDustyRenderer(params),
        gpu = GpuProgram(
            agslSource = DUSTY_AGSL,
            uniforms = params.toUniforms(),
        ),
        summary = params.summary(),
    )
}

/** Builds one HALATION preset (shared AGSL shader + deterministic CPU mirror) from a parameter row. */
object HalationEffectFactory {
    fun create(
        params: HalationParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = HalationFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuHalationRenderer(params),
        gpu = GpuProgram(
            agslSource = HALATION_AGSL,
            uniforms = params.toUniforms(),
        ),
        summary = params.summary(),
    )
}

/** Builds one YESTERDAY preset (shared AGSL shader + CPU mirror + dot-matrix date stamp). */
object YesterdayEffectFactory {
    fun create(
        params: YesterdayParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = YesterdayFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuYesterdayRenderer(params),
        gpu = GpuProgram(
            agslSource = YESTERDAY_AGSL,
            uniforms = params.toUniforms(),
        ),
        summary = params.summary(),
        stamp = params.stampSpec(),
    )
}

/** Builds one SENSUAL preset (shared AGSL shader + deterministic CPU mirror) from a parameter row. */
object SensualEffectFactory {
    fun create(
        params: SensualParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = SensualFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuSensualRenderer(params),
        gpu = GpuProgram(
            agslSource = SENSUAL_AGSL,
            uniforms = params.toUniforms(),
            // PREQUEL's adj-Blur mask: sharp centre / blurred edges, bundled in assets.
            extraShaderAssets = mapOf("uMask" to "blurmask.jpg"),
        ),
        summary = params.summary(),
    )
}

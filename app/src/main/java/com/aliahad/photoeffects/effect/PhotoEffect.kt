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
    /** Float-array uniforms (HSL bands); pushed via RuntimeShader.setFloatUniform(String, float[]). */
    val vectorUniforms: Map<String, FloatArray> = emptyMap(),
    /**
     * Extra asset-backed shader inputs: uniform shader name -> app asset path.
     * Used by SENSUAL for the bundled PREQUEL `adj-Blur` mask (`uMask` -> `blurmask.jpg`),
     * KUJI for the paper frames (`uFrame` -> `kuji_frame_N.png`) and FOCAL for the LUT atlas
     * (`uLut` -> `focal_luts.png`); the preview binds each asset as a BitmapShader input.
     */
    val extraShaderAssets: Map<String, String> = emptyMap(),
    /**
     * Extra shader inputs sampled in *bitmap pixel* space (identity local matrix) instead of
     * normalized image space. FOCAL's LUT atlas is addressed by texel coordinates, unlike the
     * image-sized mask/frame overlays.
     */
    val pixelSpaceShaders: Set<String> = emptySet(),
    /** Optional preceding stage. Colour filters feed the selected effect. */
    val inputProgram: GpuProgram? = null,
    /** Stage strength when this program belongs to a composed edit. */
    val intensityOverride: Float? = null,
    /**
     * Internal offscreen passes evaluated before this program (base first). Used by the filter
     * pipeline to make directional focus separable; each pass consumes the previous layer.
     */
    val internalPasses: List<GpuPass> = emptyList(),
    /**
     * Shader inputs that must receive the stage's *input* layer rather than this program's
     * content. The renderer binds them only when the stage input is the original bitmap.
     */
    val baseShaderInputs: Set<String> = emptySet(),
)

/**
 * One internal offscreen pass. Unlike [GpuProgram] it has no composition/intensity semantics:
 * internal passes run at full strength and are followed by the owning program.
 */
data class GpuPass(
    val agslSource: String,
    val uniforms: Map<String, Float> = emptyMap(),
    val vectorUniforms: Map<String, FloatArray> = emptyMap(),
    val extraShaderAssets: Map<String, String> = emptyMap(),
    val pixelSpaceShaders: Set<String> = emptySet(),
    val baseShaderInputs: Set<String> = emptySet(),
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
    /**
     * Rough peak Java-heap cost of one CPU export render in bytes per pixel, including the
     * caller's pixel buffers. Used to pick the largest safe export resolution.
     */
    val exportBytesPerPixel: Int = DEFAULT_EXPORT_BYTES_PER_PIXEL,
    /** Independent effect strength for a date overlay in a composed filter + effect edit. */
    val stampIntensity: Float = 1f,
) {
    val id: String get() = spec.id
    val displayName: String get() = spec.displayName
    val defaultIntensity: Int get() = spec.defaultIntensity

    companion object {
        /** Conservative fallback for renderers whose peak has not been analysed. */
        const val DEFAULT_EXPORT_BYTES_PER_PIXEL = 48
    }
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
        exportBytesPerPixel = 28,
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
        exportBytesPerPixel = 28,
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
        exportBytesPerPixel = 44,
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
        exportBytesPerPixel = 56,
    )
}

/** Builds one KUJI preset (shared AGSL shader + deterministic CPU mirror + paper frame). */
object KujiEffectFactory {
    fun create(
        params: KujiParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = KujiFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuKujiRenderer(params),
        gpu = GpuProgram(
            agslSource = KUJI_AGSL,
            uniforms = params.toUniforms(),
            // Recovered PREQUEL frame overlay, bound as `uniform shader uFrame`.
            extraShaderAssets = mapOf(KujiUniforms.FRAME_SHADER to params.frameAsset),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 12,
    )
}

/** Builds one FOCAL preset (shared AGSL shader + deterministic CPU mirror + 33^3 LUT atlas). */
object FocalEffectFactory {
    fun create(
        params: FocalParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = FocalFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuFocalRenderer(params),
        gpu = GpuProgram(
            agslSource = FOCAL_AGSL,
            uniforms = params.toUniforms(),
            // Six recovered 33^3 LUTs packed in one atlas, bound as `uniform shader uLut`.
            extraShaderAssets = mapOf(FocalUniforms.LUT_SHADER to "focal_luts.png"),
            pixelSpaceShaders = setOf(FocalUniforms.LUT_SHADER),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 12,
    )
}

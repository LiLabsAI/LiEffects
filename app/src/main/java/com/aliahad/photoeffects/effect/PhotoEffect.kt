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
 * (uniform names are family-specific: `MotesUniforms` / `LumenUniforms`).
 */
data class GpuProgram(
    val agslSource: String,
    val uniforms: Map<String, Float> = emptyMap(),
    /** Float-array uniforms (HSL bands); pushed via RuntimeShader.setFloatUniform(String, float[]). */
    val vectorUniforms: Map<String, FloatArray> = emptyMap(),
    /**
     * Extra asset-backed shader inputs: uniform shader name -> app asset path.
     * Used by VELOUR for the bundled radial blur mask (`uMask` -> `blurmask.jpg`),
     * PAPERLIGHT for the paper frames (`uFrame` -> `paperlight_frame_N.png`) and PRISM for the LUT atlas
     * (`uLut` -> `prism_luts.png`); the preview binds each asset as a BitmapShader input.
     */
    val extraShaderAssets: Map<String, String> = emptyMap(),
    /**
     * Extra shader inputs sampled in *bitmap pixel* space (identity local matrix) instead of
     * normalized image space. PRISM's LUT atlas is addressed by texel coordinates, unlike the
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
    /** Base inputs sampled at exact texels, for integer kernels such as sharpening. */
    val nearestBaseShaderInputs: Set<String> = emptySet(),
    /** Original coverage only: every stage preserves center alpha, so this is valid at any stage. */
    val originalAlphaShaderInputs: Set<String> = emptySet(),
    /** Optional float set once from the source bitmap: 1 when it has no alpha channel. */
    val opaqueSourceUniform: String? = null,
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
    val nearestBaseShaderInputs: Set<String> = emptySet(),
    val originalAlphaShaderInputs: Set<String> = emptySet(),
    val opaqueSourceUniform: String? = null,
)

/** One pluggable effect: metadata + CPU/GPU renderers + a short parameter summary for the UI. */
data class PhotoEffect(
    val spec: EffectSpec,
    val cpu: CpuEffect,
    val gpu: GpuProgram,
    /** One-line parameter summary shown next to the effect name (e.g. "white dust · grain"). */
    val summary: String,
    /**
     * Dot-matrix stamp overlay metadata, when the family draws one (DAYMARK's "Date" tool).
     * The CPU renderer rasterizes it into its output; the GPU preview draws it as a canvas
     * overlay on top of the AGSL result (see `PhotoPreviewCard`).
     */
    val stamp: DotMatrixDate.StampSpec? = null,
    /**
     * Conservative peak render working set in bytes per pixel, including the caller's pixel
     * buffers and temporary native/JNI allocations. Bounds the planned export resolution.
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

/** Builds one MOTES preset (shared AGSL shader + deterministic CPU mirror) from a parameter row. */
object MotesEffectFactory {
    fun create(
        params: MotesParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = MotesFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuMotesRenderer(params),
        gpu = GpuProgram(
            agslSource = MOTES_AGSL,
            uniforms = params.toUniforms(),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 32,
    )
}

/** Builds one LUMEN preset (shared AGSL shader + deterministic CPU mirror) from a parameter row. */
object LumenEffectFactory {
    fun create(
        params: LumenParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = LumenFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuLumenRenderer(params),
        gpu = GpuProgram(
            agslSource = LUMEN_AGSL,
            uniforms = params.toUniforms(),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 32,
    )
}

/** Builds one DAYMARK preset (shared AGSL shader + CPU mirror + dot-matrix date stamp). */
object DaymarkEffectFactory {
    fun create(
        params: DaymarkParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = DaymarkFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuDaymarkRenderer(params),
        gpu = GpuProgram(
            agslSource = DAYMARK_AGSL,
            uniforms = params.toUniforms(),
        ),
        summary = params.summary(),
        stamp = params.stampSpec(),
        exportBytesPerPixel = 48,
    )
}

/** Builds one VELOUR preset (shared AGSL shader + deterministic CPU mirror) from a parameter row. */
object VelourEffectFactory {
    fun create(
        params: VelourParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = VelourFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuVelourRenderer(params),
        gpu = GpuProgram(
            agslSource = VELOUR_AGSL,
            uniforms = params.toUniforms(),
            // Radial blur mask: sharp centre / blurred edges, bundled in assets.
            extraShaderAssets = mapOf("uMask" to "blurmask.jpg"),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 60,
    )
}

/** Builds one PAPERLIGHT preset (shared AGSL shader + deterministic CPU mirror + paper frame). */
object PaperlightEffectFactory {
    fun create(
        params: PaperlightParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = PaperlightFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuPaperlightRenderer(params),
        gpu = GpuProgram(
            agslSource = PAPERLIGHT_AGSL,
            uniforms = params.toUniforms(),
            // Bundled reference-derived frame overlay, bound as `uniform shader uFrame`.
            extraShaderAssets = mapOf(PaperlightUniforms.FRAME_SHADER to params.frameAsset),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 12,
    )
}

/** Builds one PRISM preset (shared AGSL shader + deterministic CPU mirror + 33^3 LUT atlas). */
object PrismEffectFactory {
    fun create(
        params: PrismParams,
        extraCategories: Set<EffectCategory> = emptySet(),
    ): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = PrismFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = extraCategories,
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuPrismRenderer(params),
        gpu = GpuProgram(
            agslSource = PRISM_AGSL,
            uniforms = params.toUniforms(),
            // Six recovered 33^3 LUTs packed in one atlas, bound as `uniform shader uLut`.
            extraShaderAssets = mapOf(PrismUniforms.LUT_SHADER to "prism_luts.png"),
            pixelSpaceShaders = setOf(PrismUniforms.LUT_SHADER),
        ),
        summary = params.summary(),
        exportBytesPerPixel = 12,
    )
}

object NocturneEffectFactory {
    fun create(params: NocturneParams): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = NocturneFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = setOf(EffectCategory.New, EffectCategory.ForYou),
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuNocturneRenderer(params),
        gpu = GpuProgram(NOCTURNE_AGSL, params.toUniforms()),
        summary = params.summary,
        exportBytesPerPixel = 16,
    )
}

object MarqueeEffectFactory {
    fun create(params: MarqueeParams): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id,
            displayName = params.displayName,
            familyId = MarqueeFamily.ID,
            description = params.description,
            defaultIntensity = params.defaultIntensity,
            extraCategories = setOf(EffectCategory.New, EffectCategory.ForYou, EffectCategory.Films),
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuMarqueeRenderer(params),
        gpu = GpuProgram(MARQUEE_AGSL, params.toUniforms()),
        summary = params.summary,
        exportBytesPerPixel = 16,
    )
}

object SolsticeEffectFactory {
    fun create(params: SolsticeParams): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id, displayName = params.displayName, familyId = SolsticeFamily.ID,
            description = params.description, defaultIntensity = params.defaultIntensity,
            extraCategories = setOf(EffectCategory.New, EffectCategory.ForYou, EffectCategory.Films),
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuSolsticeRenderer(params),
        gpu = GpuProgram(SOLSTICE_AGSL, params.toUniforms()),
        summary = params.summary,
        exportBytesPerPixel = 16,
    )
}

object ScreenprintEffectFactory {
    fun create(params: ScreenprintParams): PhotoEffect = PhotoEffect(
        spec = EffectSpec(
            id = params.id, displayName = params.displayName, familyId = ScreenprintFamily.ID,
            description = params.description, defaultIntensity = params.defaultIntensity,
            extraCategories = setOf(EffectCategory.New, EffectCategory.ForYou),
        ),
        cpu = com.aliahad.photoeffects.effect.cpu.CpuScreenprintRenderer(params),
        gpu = GpuProgram(SCREENPRINT_AGSL, params.toUniforms()),
        summary = params.summary,
        exportBytesPerPixel = 16,
    )
}

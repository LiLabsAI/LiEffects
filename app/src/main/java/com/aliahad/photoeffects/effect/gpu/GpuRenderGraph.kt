package com.aliahad.photoeffects.effect.gpu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Log
import com.aliahad.photoeffects.effect.GpuPass
import com.aliahad.photoeffects.effect.GpuProgram
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Explicit, offscreen GPU pass chain.
 *
 * The previous preview bound each stage as the child shader of the next stage. A shader input is
 * evaluated at every tap, so a blur inside the final stage re-ran the whole preceding stage for
 * each tap (composition cost multiplied instead of adding). Here every pass records into its own
 * [RenderNode]; the next pass consumes the rendered layer as its `content` input. Each pass is
 * therefore evaluated once per pixel, composition cost is additive, and internal filter passes
 * (separable focus, grade, aberration) become plain layers.
 *
 * The chain is deliberately only structural: uniforms are re-applied on every draw, so slider
 * changes do not rebuild the display lists.
 */
internal class GpuRenderGraph private constructor(
    val width: Int,
    val height: Int,
    private val stages: List<StageChain>,
) {

    private data class PassNode(val shader: RuntimeShader, val node: RenderNode)

    private data class StageChain(val internal: List<PassNode>, val main: PassNode)

    /** Outermost render node; draw this into a hardware canvas or a HardwareRenderer. */
    val root: RenderNode get() = stages.last().main.node

    /** Total number of offscreen passes, useful for diagnostics and tests. */
    val passCount: Int get() = stages.sumOf { it.internal.size + 1 }

    /**
     * Push current uniforms and invalidate the stage layers. Called on every draw; cheap because
     * shader compilation, asset decoding and display-list recording happened in [build].
     */
    fun update(programs: List<GpuProgram>, seed: Long, intensity: Float) {
        require(programs.size == stages.size) {
            "program/stage mismatch: ${programs.size} vs ${stages.size}"
        }
        for (i in stages.indices) {
            val stage = stages[i]
            val program = programs[i]
            require(program.internalPasses.size == stage.internal.size) {
                "internal pass mismatch: ${program.internalPasses.size} vs ${stage.internal.size}"
            }
            for (j in program.internalPasses.indices) {
                val pass = program.internalPasses[j]
                val node = stage.internal[j]
                GpuEffectRenderer.applyUniforms(
                    node.shader,
                    pass.uniforms,
                    intensityOverride = 1f,
                    intensity = intensity,
                    photoSeed = seed,
                    widthPx = width.toFloat(),
                    heightPx = height.toFloat(),
                )
                node.node.setRenderEffect(
                    RenderEffect.createRuntimeShaderEffect(
                        node.shader,
                        GpuEffectRenderer.CONTENT_SHADER,
                    ),
                )
            }
            GpuEffectRenderer.applyUniforms(
                stage.main.shader,
                program,
                intensity,
                seed,
                width.toFloat(),
                height.toFloat(),
            )
            stage.main.node.setRenderEffect(
                RenderEffect.createRuntimeShaderEffect(
                    stage.main.shader,
                    GpuEffectRenderer.CONTENT_SHADER,
                ),
            )
        }
    }

    /** Draws the final pass into [canvas] at the graph's origin. */
    fun draw(canvas: Canvas) {
        canvas.drawRenderNode(root)
    }

    /** Releases display lists; shared asset bitmaps stay in the process-wide cache. */
    fun release() {
        for (stage in stages) {
            stage.internal.forEach { it.node.discardDisplayList() }
            stage.main.node.discardDisplayList()
        }
    }

    companion object {
        private const val TAG = "PhotoEffectsGpu"

        /** Flattens a composed program chain (base first). */
        fun programs(program: GpuProgram): List<GpuProgram> {
            val base = program.inputProgram?.let(::programs).orEmpty()
            return base + program
        }

        /**
         * Structural identity of a chain. Uniforms and intensity are excluded: changing a slider
         * must not rebuild RenderNodes or re-decode assets.
         */
        fun topology(programs: List<GpuProgram>): List<GpuStageKey> = programs.map { program ->
            GpuStageKey(
                source = program.agslSource,
                assets = program.extraShaderAssets.toSortedMap().map { "${it.key}=${it.value}" },
                pixelSpace = program.pixelSpaceShaders.sorted(),
                baseInputs = program.baseShaderInputs.sorted(),
                internal = program.internalPasses.map(::passKey),
            )
        }

        private fun passKey(pass: GpuPass): GpuPassKey = GpuPassKey(
            source = pass.agslSource,
            assets = pass.extraShaderAssets.toSortedMap().map { "${it.key}=${it.value}" },
            pixelSpace = pass.pixelSpaceShaders.sorted(),
            baseInputs = pass.baseShaderInputs.sorted(),
        )

        /**
         * Builds the pass chain. Returns null when a shader cannot compile or an asset cannot be
         * decoded, in which case the caller falls back to the deterministic CPU preview.
         */
        fun build(
            context: Context,
            source: Bitmap,
            programs: List<GpuProgram>,
            width: Int,
            height: Int,
        ): GpuRenderGraph? {
            if (programs.isEmpty() || width <= 0 || height <= 0 || source.isRecycled) return null

            val sourceShader = BitmapShader(
                source,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            ).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                setLocalMatrix(
                    Matrix().apply {
                        reset()
                        setScale(
                            width.toFloat() / max(source.width, 1),
                            height.toFloat() / max(source.height, 1),
                        )
                    },
                )
            }

            val stages = ArrayList<StageChain>(programs.size)
            var previous: RenderNode? = null
            try {
                for (program in programs) {
                    // Filters run before effects in the editor, so their internal passes can bind
                    // the untouched source as an extra shader input. If a program with base
                    // inputs is used after another stage, fall back to CPU rather than silently
                    // binding the wrong layer.
                    val stageBaseShader = if (previous == null) sourceShader else null
                    val internal = ArrayList<PassNode>(program.internalPasses.size)
                    var content = previous
                    for (pass in program.internalPasses) {
                        val node = buildPass(
                            context = context,
                            source = source,
                            content = content,
                            baseShader = stageBaseShader,
                            agslSource = pass.agslSource,
                            uniforms = pass.uniforms,
                            assets = pass.extraShaderAssets,
                            pixelSpace = pass.pixelSpaceShaders,
                            baseInputs = pass.baseShaderInputs,
                            width = width,
                            height = height,
                        ) ?: return null
                        internal.add(node)
                        content = node.node
                    }
                    val main = buildPass(
                        context = context,
                        source = source,
                        content = content,
                        baseShader = stageBaseShader,
                        agslSource = program.agslSource,
                        uniforms = program.uniforms,
                        assets = program.extraShaderAssets,
                        pixelSpace = program.pixelSpaceShaders,
                        baseInputs = program.baseShaderInputs,
                        width = width,
                        height = height,
                    ) ?: return null
                    stages.add(StageChain(internal, main))
                    previous = main.node
                }
            } catch (t: Throwable) {
                Log.w(TAG, "GPU pass graph build failed; using CPU preview fallback", t)
                stages.forEach { stage ->
                    stage.internal.forEach { it.node.discardDisplayList() }
                    stage.main.node.discardDisplayList()
                }
                return null
            }
            return GpuRenderGraph(width, height, stages)
        }

        private fun buildPass(
            context: Context,
            source: Bitmap,
            content: RenderNode?,
            baseShader: BitmapShader?,
            agslSource: String,
            uniforms: Map<String, Float>,
            assets: Map<String, String>,
            pixelSpace: Set<String>,
            baseInputs: Set<String>,
            width: Int,
            height: Int,
        ): PassNode? {
            if (baseInputs.isNotEmpty() && baseShader == null) return null
            val shader = GpuEffectRenderer.compile(agslSource) ?: return null
            if (!bindAssets(context, shader, assets, pixelSpace, width, height)) return null
            for (name in baseInputs) shader.setInputShader(name, baseShader!!)

            val node = RenderNode("PhotoEffects_pass")
            node.setPosition(0, 0, width, height)
            node.setClipToBounds(true)
            val canvas = node.beginRecording(width, height)
            if (content == null) {
                canvas.drawBitmap(
                    source,
                    null,
                    android.graphics.Rect(0, 0, width, height),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
                )
            } else {
                canvas.drawRenderNode(content)
            }
            node.endRecording()
            node.setRenderEffect(
                RenderEffect.createRuntimeShaderEffect(
                    shader,
                    GpuEffectRenderer.CONTENT_SHADER,
                ),
            )
            return PassNode(shader, node)
        }

        private fun bindAssets(
            context: Context,
            shader: RuntimeShader,
            assets: Map<String, String>,
            pixelSpace: Set<String>,
            width: Int,
            height: Int,
        ): Boolean {
            for ((name, path) in assets) {
                val bitmap = GpuAssetCache.get(context, path) ?: return false
                val pixelSpaceShader = name in pixelSpace
                val bitmapShader = BitmapShader(
                    bitmap,
                    Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP,
                ).apply {
                    setFilterMode(
                        if (pixelSpaceShader) BitmapShader.FILTER_MODE_NEAREST
                        else BitmapShader.FILTER_MODE_LINEAR,
                    )
                    if (!pixelSpaceShader) {
                        setLocalMatrix(
                            Matrix().apply {
                                reset()
                                setScale(
                                    width.toFloat() / max(bitmap.width, 1),
                                    height.toFloat() / max(bitmap.height, 1),
                                )
                            },
                        )
                    }
                }
                shader.setInputShader(name, bitmapShader)
            }
            return true
        }
    }
}

internal data class GpuStageKey(
    val source: String,
    val assets: List<String>,
    val pixelSpace: List<String>,
    val baseInputs: List<String>,
    val internal: List<GpuPassKey>,
)

internal data class GpuPassKey(
    val source: String,
    val assets: List<String>,
    val pixelSpace: List<String>,
    val baseInputs: List<String>,
)

/**
 * Process-wide decoded shader assets. MainActivity preloads the CPU copies, but the GPU preview
 * binds BitmapShaders; caching here prevents re-decoding a frame each time a preset is reselected.
 */
private object GpuAssetCache {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()

    @Synchronized
    fun get(context: Context, asset: String): Bitmap? {
        bitmaps[asset]?.let { if (!it.isRecycled) return it }
        val decoded = runCatching {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        bitmaps[asset] = decoded
        return decoded
    }
}

package com.aliahad.photoeffects.effect.gpu

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import com.aliahad.photoeffects.effect.DustyUniforms
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.GpuProgram
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds a [GpuProgram] (AGSL source + parameter uniforms) to Android's RuntimeShader /
 * RenderEffect pair used for the live GPU preview.
 *
 * The shader is compiled once per effect (expensive); only the float uniforms are updated per
 * frame. The photo is bound as the child shader named [CONTENT_SHADER] by
 * `RenderEffect.createRuntimeShaderEffect`.
 */
object GpuEffectRenderer {

    const val CONTENT_SHADER = "content"
    private const val TAG = "PhotoEffectsGpu"

    // AGSL compilation is one of the most expensive preview operations. Preview-layer changes
    // (panel animation, image resize, slider change) rebuild RenderNodes but reuse the same shader
    // sources; keep one compiled instance per source. The preview has one active graph at a time
    // and composed stages use distinct sources, so uniforms/input bindings are updated safely.
    private val shaders = ConcurrentHashMap<String, RuntimeShader>()
    private val failedSources = ConcurrentHashMap.newKeySet<String>()

    /** Returns null when the device/AGSL compiler rejects the program (caller falls back to CPU). */
    fun compile(program: GpuProgram): RuntimeShader? = compile(program.agslSource)

    fun compile(agslSource: String): RuntimeShader? {
        shaders[agslSource]?.let { return it }
        if (agslSource in failedSources) return null
        return try {
            val created = RuntimeShader(agslSource)
            val existing = shaders.putIfAbsent(agslSource, created)
            if (existing == null) {
                Log.d(TAG, "AGSL program compiled (GPU preview active)")
                created
            } else {
                existing
            }
        } catch (t: Throwable) {
            failedSources.add(agslSource)
            Log.w(TAG, "AGSL program failed to compile; using CPU preview fallback", t)
            null
        }
    }

    /** Push all parameters for one frame. [widthPx]/[heightPx] are the drawn layer size. */
    fun applyUniforms(
        shader: RuntimeShader,
        program: GpuProgram,
        intensity: Float,
        photoSeed: Long,
        widthPx: Float,
        heightPx: Float,
    ) = applyUniforms(
        shader,
        program.uniforms,
        program.vectorUniforms,
        program.intensityOverride,
        intensity,
        photoSeed,
        widthPx,
        heightPx,
    )

    /** Applies uniforms for an internal pass (which has no composition metadata). */
    fun applyUniforms(
        shader: RuntimeShader,
        uniforms: Map<String, Float>,
        vectorUniforms: Map<String, FloatArray> = emptyMap(),
        intensityOverride: Float?,
        intensity: Float,
        photoSeed: Long,
        widthPx: Float,
        heightPx: Float,
    ) {
        shader.setFloatUniform(
            DustyUniforms.INTENSITY,
            ((intensityOverride ?: 1f) * intensity).coerceIn(0f, 1f),
        )
        val variantSeed = (uniforms[DustyUniforms.SEED] ?: 0f).toInt()
        shader.setFloatUniform(
            DustyUniforms.SEED,
            EffectSeed.of(photoSeed, variantSeed).toFloat(),
        )
        shader.setFloatUniform(
            DustyUniforms.RESOLUTION,
            widthPx.coerceAtLeast(1f),
            heightPx.coerceAtLeast(1f),
        )
        for ((name, value) in uniforms) {
            if (name != DustyUniforms.SEED) shader.setFloatUniform(name, value)
        }
        for ((name, values) in vectorUniforms) {
            // float2/3/4 uniforms use the vector overloads; float[] uniforms use the array form.
            when (values.size) {
                2 -> shader.setFloatUniform(name, values[0], values[1])
                3 -> shader.setFloatUniform(name, values[0], values[1], values[2])
                4 -> shader.setFloatUniform(name, values[0], values[1], values[2], values[3])
                else -> shader.setFloatUniform(name, values)
            }
        }
    }

    fun createRenderEffect(shader: RuntimeShader): RenderEffect =
        RenderEffect.createRuntimeShaderEffect(shader, CONTENT_SHADER)
}

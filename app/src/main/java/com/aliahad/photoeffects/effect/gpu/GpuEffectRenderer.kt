package com.aliahad.photoeffects.effect.gpu

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import com.aliahad.photoeffects.effect.DustyUniforms
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.GpuProgram

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

    /** Returns null when the device/AGSL compiler rejects the program (caller falls back to CPU). */
    fun compile(program: GpuProgram): RuntimeShader? = try {
        val shader = RuntimeShader(program.agslSource)
        Log.d(TAG, "AGSL program compiled (GPU preview active)")
        shader
    } catch (t: Throwable) {
        Log.w(TAG, "AGSL program failed to compile; using CPU preview fallback", t)
        null
    }

    /** Push all parameters for one frame. [widthPx]/[heightPx] are the drawn layer size. */
    fun applyUniforms(
        shader: RuntimeShader,
        program: GpuProgram,
        intensity: Float,
        photoSeed: Long,
        widthPx: Float,
        heightPx: Float,
    ) {
        shader.setFloatUniform(DustyUniforms.INTENSITY, intensity.coerceIn(0f, 1f))
        val variantSeed = (program.uniforms[DustyUniforms.SEED] ?: 0f).toInt()
        shader.setFloatUniform(
            DustyUniforms.SEED,
            EffectSeed.of(photoSeed, variantSeed).toFloat(),
        )
        shader.setFloatUniform(DustyUniforms.RESOLUTION, widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
        for ((name, value) in program.uniforms) {
            if (name != DustyUniforms.SEED) shader.setFloatUniform(name, value)
        }
    }

    fun createRenderEffect(shader: RuntimeShader): RenderEffect =
        RenderEffect.createRuntimeShaderEffect(shader, CONTENT_SHADER)
}

/*
 * LiEffects — Android photo editor
 * Copyright (C) 2026 Ali Ahad
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliahad.photoeffects.effect.gpu

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import com.aliahad.photoeffects.effect.MotesUniforms
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.GpuProgram
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds a [GpuProgram] (AGSL source + parameter uniforms) to Android's RuntimeShader /
 * RenderEffect pair used for the live GPU preview.
 *
 * Each graph owns its mutable RuntimeShader instances; only their float uniforms change per
 * frame. The photo is bound as the child shader named [CONTENT_SHADER] by
 * `RenderEffect.createRuntimeShaderEffect`.
 */
object GpuEffectRenderer {

    const val CONTENT_SHADER = "content"
    private const val TAG = "LiEffectsGpu"

    // RuntimeShader includes mutable uniforms and child bindings. Sharing one by source made
    // offscreen diagnostics and a live preview overwrite each other, even across distinct graphs.
    // Graph topology caching already keeps each graph's instances alive during slider changes.
    private val compiledSources = ConcurrentHashMap.newKeySet<String>()
    private val failedSources = ConcurrentHashMap.newKeySet<String>()

    /** Returns null when the device/AGSL compiler rejects the program (caller falls back to CPU). */
    fun compile(program: GpuProgram): RuntimeShader? = compile(program.agslSource)

    fun compile(agslSource: String): RuntimeShader? {
        if (agslSource in failedSources) return null
        return try {
            val created = RuntimeShader(agslSource)
            if (compiledSources.add(agslSource)) {
                Log.d(TAG, "AGSL program compiled (GPU preview active)")
            }
            created
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
            MotesUniforms.INTENSITY,
            ((intensityOverride ?: 1f) * intensity).coerceIn(0f, 1f),
        )
        val variantSeed = (uniforms[MotesUniforms.SEED] ?: 0f).toInt()
        shader.setFloatUniform(
            MotesUniforms.SEED,
            EffectSeed.of(photoSeed, variantSeed).toFloat(),
        )
        shader.setFloatUniform(
            MotesUniforms.RESOLUTION,
            widthPx.coerceAtLeast(1f),
            heightPx.coerceAtLeast(1f),
        )
        for ((name, value) in uniforms) {
            if (name != MotesUniforms.SEED) shader.setFloatUniform(name, value)
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

package com.aliahad.photoeffects.adjust

import com.aliahad.photoeffects.adjust.cpu.CpuAdjustRenderer
import com.aliahad.photoeffects.effect.EffectSpec
import com.aliahad.photoeffects.effect.GpuPass
import com.aliahad.photoeffects.effect.GpuProgram
import com.aliahad.photoeffects.effect.PhotoEffect

/** One Adjust stage shared by every category; only the plan bits change pass topology. */
object AdjustEffectFactory {
    const val ID = "adjust"
    const val FAMILY = "Adjust"

    fun create(params: AdjustParams): PhotoEffect {
        val plan = planFor(params)
        val uniforms = params.scalarUniforms()
        val internal = buildList {
            if (plan.color) add(
                GpuPass(
                    agslSource = ADJUST_COLOR_AGSL,
                    uniforms = uniforms,
                    vectorUniforms = params.hslUniforms(),
                    originalAlphaShaderInputs = setOf("uOriginalAlpha"),
                    opaqueSourceUniform = "uOpaqueSource",
                ),
            )
            if (plan.detail) add(GpuPass(
                ADJUST_DETAIL_AGSL, uniforms,
                originalAlphaShaderInputs = setOf("uOriginalAlpha"),
                opaqueSourceUniform = "uOpaqueSource",
            ))
            if (plan.blur) add(GpuPass(
                ADJUST_BLUR_AGSL, uniforms,
                originalAlphaShaderInputs = setOf("uOriginalAlpha"),
                opaqueSourceUniform = "uOpaqueSource",
            ))
        }
        return PhotoEffect(
            spec = EffectSpec(
                id = ID,
                displayName = "Adjust",
                familyId = FAMILY,
                description = "Light, Color, HSL, Details, Stylize, Refocus, Optical and Blur",
                defaultIntensity = 100,
            ),
            cpu = CpuAdjustRenderer(params, plan),
            gpu = GpuProgram(
                agslSource = ADJUST_FINISH_AGSL,
                uniforms = uniforms,
                internalPasses = internal,
                originalAlphaShaderInputs = setOf("uOriginalAlpha"),
                opaqueSourceUniform = "uOpaqueSource",
            ),
            summary = AdjustSummary.describe(params),
            exportBytesPerPixel = adjustBytesPerPixel(plan),
        )
    }
}

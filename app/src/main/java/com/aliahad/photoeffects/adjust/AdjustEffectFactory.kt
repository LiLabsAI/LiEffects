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

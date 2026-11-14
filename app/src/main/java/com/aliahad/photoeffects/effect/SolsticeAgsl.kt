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

package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.filter.FILTER_GRAIN_AGSL

/** Same normalized ellipse, screen blend, bounded grain and alpha contract as the CPU path. */
internal const val SOLSTICE_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uCenterX;
uniform float uCenterY;
uniform float uRadiusX;
uniform float uRadiusY;
uniform float uLightR;
uniform float uLightG;
uniform float uLightB;
uniform float uLight;
uniform float uGrain;
""" + FILTER_GRAIN_AGSL + """
half4 main(float2 fragCoord) {
    half4 srcH = content.eval(fragCoord);
    float t = clamp(uIntensity, 0.0, 1.0);
    if (t <= 0.0 || srcH.a <= 0.0) return srcH;
    float alpha = float(srcH.a);
    float3 src = float3(srcH.rgb) / alpha;
    float2 uv = fragCoord / uResolution;
    float2 d = (uv - float2(uCenterX, uCenterY)) / float2(uRadiusX, uRadiusY);
    float distance = dot(d, d);
    float field = 1.0 - smoothstep(0.02, 1.0, distance);
    float core = (1.0 - smoothstep(0.0, 0.18, distance)) * 0.12;
    float3 light = float3(uLightR, uLightG, uLightB) * (uLight * field) + core;
    float3 c = src + (1.0 - src) * light;
    float grain = uGrain > 0.0 ? filterGrain(fragCoord, uResolution, uSeed, 1.4) *
        uGrain * (0.35 + 0.65 * field) : 0.0;
    return half4(half3(mix(src, clamp(c + grain, 0.0, 1.0), t) * alpha), srcH.a);
}
"""

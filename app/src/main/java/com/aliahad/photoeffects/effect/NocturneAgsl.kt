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

internal const val NOCTURNE_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uStyle;
uniform float uExposure;
uniform float uSaturation;
uniform float uEdgeShade;
uniform float uLight;
uniform float uGrain;
""" + FILTER_GRAIN_AGSL + """

half4 main(float2 fragCoord) {
    half4 srcH = content.eval(fragCoord);
    float t = clamp(uIntensity, 0.0, 1.0);
    if (t <= 0.0 || srcH.a <= 0.0) return srcH;
    float alpha = float(srcH.a);
    float3 src = float3(srcH.rgb) / alpha;
    float lum = dot(src, float3(0.2126, 0.7152, 0.0722));
    float3 c = mix(float3(lum), src, uSaturation) * uExposure;
    c *= 0.9 + 0.1 * c;

    float2 uv = fragCoord / max(uResolution, float2(1.0)) - 0.5;
    float edge = smoothstep(0.12, 0.48, dot(uv, uv));
    float shadow = (1.0 - smoothstep(0.12, 0.60, lum)) * lum;
    float lightMask = smoothstep(0.02, 0.38, lum) * (1.0 - smoothstep(0.72, 1.0, lum));
    float field;
    float shade;
    float3 lightColor;
    if (uStyle < 0.5) {
        float diagonal = uv.x + 0.55 * uv.y + 0.10;
        field = 1.0 - smoothstep(0.015, 0.32, diagonal * diagonal * 2.8 + uv.y * uv.y * 0.65);
        shade = 1.0 - uEdgeShade * edge;
        lightColor = float3(1.0, 0.72, 0.38);
        c += shadow * float3(0.035, 0.010, -0.020);
    } else {
        float window = uv.x - 0.26 + 0.18 * uv.y;
        float rim = 1.0 - smoothstep(0.012, 0.21, abs(window));
        float reflection = 1.0 - smoothstep(0.025, 0.13, abs(uv.x + 0.36));
        field = (rim + reflection * 0.28) * (1.0 - smoothstep(0.18, 0.50, abs(uv.y)));
        float cinema = smoothstep(0.20, 0.50, abs(uv.y));
        shade = 1.0 - uEdgeShade * (edge * 0.55 + cinema * 0.45);
        lightColor = float3(0.48, 0.72, 1.0);
        c += shadow * float3(-0.025, 0.015, 0.060);
    }
    c = clamp(c * shade, 0.0, 1.0);
    c += (1.0 - c) * (uLight * field * lightMask) * lightColor;
    float grain = filterGrain(fragCoord, uResolution, uSeed, 1.15) * uGrain *
        smoothstep(0.0, 0.12, lum) * (1.0 - smoothstep(0.70, 1.0, lum));
    float3 outc = mix(src, clamp(c + grain, 0.0, 1.0), t);
    return half4(half3(outc * alpha), srcH.a);
}
"""

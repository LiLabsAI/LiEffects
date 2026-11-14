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

/**
 * Dark-blue cinematic grade, shared by both MARQUEE presets.
 *
 * Stage order is the colour-science order: exposure -> hue-selective saturation -> filmic
 * S-curve -> highlight shoulder -> matte levels -> split tone -> falloff -> grain. Saturation
 * comes before the curve so the curve cannot reintroduce colour, and the split tone comes last
 * so the curve cannot mute it. The CPU mirror in `CpuMarqueeRenderer` evaluates the same stages.
 */
internal const val MARQUEE_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uStyle;
uniform float uSeed;
uniform float uExposure;
uniform float uCoolR;
uniform float uCoolG;
uniform float uCoolB;
uniform float uCurve;
uniform float uShoulder;
uniform float uBlackR;
uniform float uBlackG;
uniform float uBlackB;
uniform float uWhite;
uniform float uShadowR;
uniform float uShadowG;
uniform float uShadowB;
uniform float uHighlightR;
uniform float uHighlightG;
uniform float uHighlightB;
uniform float uSaturation;
uniform float uSkinSaturation;
uniform float uSkinProtect;
uniform float uFalloff;
uniform float uGrain;
""" + FILTER_GRAIN_AGSL + """

const float MARQUEE_KNEE = 0.72;

// Tints scale by the headroom actually available in each channel, so a strong navy shadow
// cannot clip blue and a strong amber highlight cannot clip red. Keeps the result in gamut.
float marqueeTint(float v, float amount, float mask) {
    return v + amount * (amount >= 0.0 ? (1.0 - v) : v) * mask;
}

float3 marqueeTint(float3 c, float3 amount, float mask) {
    return float3(
        marqueeTint(c.r, amount.r, mask),
        marqueeTint(c.g, amount.g, mask),
        marqueeTint(c.b, amount.b, mask));
}

float marqueeShade(float2 uv) {
    float edge = smoothstep(0.10, 0.50, dot(uv, uv));
    if (uStyle < 0.5) {
        // Broad soft key from the upper left; the opposite corner falls away.
        float key = smoothstep(-0.35, 0.45, uv.x * 0.80 + uv.y * 0.60);
        return 1.0 - uFalloff * (edge * 0.65 + key * 0.35);
    }
    // A narrow surviving band of light, with the frame edges and top/bottom closing in.
    float band = smoothstep(0.02, 0.42, abs(uv.x + 0.12));
    float cinema = smoothstep(0.18, 0.50, abs(uv.y));
    return 1.0 - uFalloff * (edge * 0.40 + band * 0.35 + cinema * 0.25);
}

half4 main(float2 fragCoord) {
    half4 srcH = content.eval(fragCoord);
    float t = clamp(uIntensity, 0.0, 1.0);
    if (t <= 0.0 || srcH.a <= 0.0) return srcH;
    float alpha = float(srcH.a);
    float3 src = float3(srcH.rgb) / alpha;

    // Warm-hue detector on the ungraded colour: red leads blue and green. Foliage, sky and
    // neutral surfaces score zero, so only skin and practicals are exempted from the cool grade.
    float warm = clamp((src.r - src.b) * 3.0, 0.0, 1.0) *
        clamp((src.r - src.g) * 6.0, 0.0, 1.0);
    float protect = warm * uSkinProtect;

    float3 c = src * uExposure;
    // Cool the ambient world but not the faces: the signature Hollywood separation.
    float3 cool = mix(float3(1.0), float3(uCoolR, uCoolG, uCoolB), 1.0 - protect);
    c = clamp(c * cool, 0.0, 1.0);
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(float3(lum), c, mix(uSaturation, uSkinSaturation, protect));

    float3 s = c * c * (3.0 - 2.0 * c);
    c = mix(c, s, uCurve);

    float3 over = max(c - MARQUEE_KNEE, float3(0.0));
    c = c - over + over / (1.0 + uShoulder * over / (1.0 - MARQUEE_KNEE));

    float3 black = float3(uBlackR, uBlackG, uBlackB);
    c = black + c * (uWhite - black);

    // Masks key off the pre-curve tonality so the split stays put as the curve is retuned.
    // The shadow mask reaches into the midtones: that is what makes the whole frame read blue
    // rather than only the deepest shadows.
    float shadowMask = 1.0 - smoothstep(0.05, 0.72, lum);
    float highlightMask = smoothstep(0.55, 0.95, lum);
    c = marqueeTint(c, float3(uShadowR, uShadowG, uShadowB), shadowMask);
    c = marqueeTint(c, float3(uHighlightR, uHighlightG, uHighlightB), highlightMask);

    float2 uv = fragCoord / max(uResolution, float2(1.0)) - 0.5;
    c = clamp(c * marqueeShade(uv), 0.0, 1.0);

    float grain = filterGrain(fragCoord, uResolution, uSeed, 1.2) * uGrain *
        smoothstep(0.0, 0.10, lum) * (1.0 - smoothstep(0.65, 1.0, lum));
    float3 outc = mix(src, clamp(c + grain, 0.0, 1.0), t);
    return half4(half3(outc * alpha), srcH.a);
}
"""
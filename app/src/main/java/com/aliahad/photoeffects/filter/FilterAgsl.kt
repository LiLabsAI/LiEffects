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

package com.aliahad.photoeffects.filter

/**
 * Filter GPU programs.
 *
 * The single-pass [FILTER_AGSL] remains the fallback for filters without spatial stages (and for
 * the rare soft-focus + aberration combination). Filters that would otherwise evaluate the
 * directional focus in every aberration tap are split into internal offscreen passes:
 *
 *  - soft focus, no aberration:  focus horizontal -> focus vertical + detail + grade + grain + mix
 *  - aberration, no soft focus:  grade -> aberration + grain + mix
 *
 * Every pass consumes the previous RenderNode layer (see `GpuRenderGraph`), so no stage is
 * re-evaluated per tap.
 */

internal const val FILTER_UNIFORMS_AGSL = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uExposure;
uniform float uContrast;
uniform float uSaturation;
uniform float uBlack;
uniform float uWhite;
uniform float uGainR;
uniform float uGainG;
uniform float uGainB;
uniform float uShadowR;
uniform float uShadowG;
uniform float uShadowB;
uniform float uHighlightR;
uniform float uHighlightG;
uniform float uHighlightB;
uniform float uSharpen;
uniform float uFilterStrength;
uniform float uFilterIntensity;
uniform float uSoftFocus;
uniform float uFocusRadiusCurve;
uniform float uFocusSpread;
uniform float uFocusSpreadGrowth;
uniform float uFocusDetail;
uniform float uFocusDetailRadius;
uniform float uFocusLift;
uniform float uGrain;
uniform float uGrainSize;
uniform float uHighlights;
uniform float uShadows;
uniform float uAberration;
uniform float uAberrationRadius;
"""

/** Helpers that only depend on `content` and the common uniforms. */
internal const val FILTER_BASE_AGSL = """
float3 filterStraight(half4 color) {
    return color.a > 0.0
        ? float3(float(color.r), float(color.g), float(color.b)) / float(color.a)
        : float3(0.0, 0.0, 0.0);
}

float2 filterClampCoord(float2 coord) {
    float2 hi = max(uResolution - float2(0.5, 0.5), float2(0.5, 0.5));
    return clamp(coord, float2(0.5, 0.5), hi);
}

float3 filterContentRgb(float2 coord) {
    half4 h = content.eval(filterClampCoord(coord));
    return filterStraight(h);
}

float3 filterGrade(float3 base) {
    float3 c = base * uExposure;
    float gray = dot(c, float3(0.2126, 0.7152, 0.0722));
    float shadow = 1.0 - smoothstep(0.10, 0.65, gray);
    float highlight = smoothstep(0.45, 1.0, gray);
    c = ((mix(float3(gray, gray, gray), c, uSaturation) - 0.5) * uContrast + 0.5)
        * float3(uGainR, uGainG, uGainB);
    c += float3(uShadowR, uShadowG, uShadowB) * shadow;
    c += float3(uHighlightR, uHighlightG, uHighlightB) * highlight;
    c = clamp(uBlack + c * (uWhite - uBlack), 0.0, 1.0);
    c = mix(base, c, clamp(uFilterStrength, 0.0, 1.0));
    if (uHighlights != 0.0) {
        float luma = clamp(dot(c, float3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
        float mask = smoothstep(0.10, 0.90, luma);
        float highlights = clamp(uHighlights, -1.0, 1.0);
        if (highlights > 0.0) {
            c += 0.5 * highlights * mask * (1.0 - c);
        } else {
            c *= 1.0 + 0.2 * highlights * mask;
        }
    }
    if (uShadows != 0.0) {
        float luma = clamp(dot(c, float3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
        float shadows = clamp(uShadows, -1.0, 1.0);
        if (shadows > 0.0) {
            c += 0.16 * shadows * (1.0 - smoothstep(0.0, 0.8, luma)) * (1.0 - c);
        } else {
            c *= 1.0 + shadows * (1.0 - luma) * (1.0 - luma);
        }
    }
    return c;
}
"""

/** Sharpen sampled from the current `content`. */
internal const val FILTER_SHARPEN_CONTENT_AGSL = """
float3 filterSharpenContent(float3 base, float2 coord) {
    if (uSharpen <= 0.0) return base;
    float3 blurred = float3(0.0, 0.0, 0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
            blurred += filterContentRgb(coord + float2(float(x), float(y))) * w;
        }
    }
    return clamp(base + (base - blurred) * uSharpen, 0.0, 1.0);
}
"""

/** Integer sharpen taps must not interpolate across transparent texels before unpremultiplying. */
private const val FILTER_POINT_SOURCE_AGSL = """
uniform shader uPointSource;
float3 filterPointRgb(float2 coord) {
    return filterStraight(uPointSource.eval(filterClampCoord(coord)));
}
"""

/** Replaces only integer sharpen support; fractional focus and aberration taps remain linear. */
internal fun pointSampleSharpen(source: String): String = source
    .replace(
        FILTER_SHARPEN_CONTENT_AGSL,
        FILTER_POINT_SOURCE_AGSL + FILTER_SHARPEN_CONTENT_AGSL
            .replace("blurred += filterContentRgb(", "blurred += filterPointRgb("),
    )
    .replace(
        FILTER_SOURCE_AGSL,
        FILTER_POINT_SOURCE_AGSL + FILTER_SOURCE_AGSL
            .replace("blurred += filterSourceRgb(", "blurred += filterPointRgb("),
    )

/** Helpers that require a `uniform shader uSource;` declaration. */
internal const val FILTER_SOURCE_AGSL = """
float3 filterSourceRgb(float2 coord) {
    half4 h = uSource.eval(filterClampCoord(coord));
    return filterStraight(h);
}

float3 filterSharpenSource(float3 base, float2 coord) {
    if (uSharpen <= 0.0) return base;
    float3 blurred = float3(0.0, 0.0, 0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
            blurred += filterSourceRgb(coord + float2(float(x), float(y))) * w;
        }
    }
    return clamp(base + (base - blurred) * uSharpen, 0.0, 1.0);
}
"""

/** Requires [FILTER_GRAIN_AGSL]. */
internal const val FILTER_GRAIN_ADD_AGSL = """
float3 filterGrainAdd(float3 c, float2 coord) {
    if (uGrain <= 0.0) return c;
    float luma = clamp(dot(c, float3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    float response = 0.35 + 0.65 * sqrt(4.0 * luma * (1.0 - luma));
    float noise = filterGrain(coord, uResolution, uSeed, uGrainSize)
        * clamp(uGrain, 0.0, 1.0) * 0.18 * response;
    return clamp(c + float3(noise, noise, noise), 0.0, 1.0);
}
"""

/**
 * Original single-pass shader. It includes the directional focus inline because it samples the
 * same content; it is retained for simple filters and as a safe fallback for unusual combinations.
 */
internal const val FILTER_AGSL = FILTER_UNIFORMS_AGSL + FILTER_GRAIN_AGSL + DIRECTIONAL_FOCUS_AGSL +
    FILTER_BASE_AGSL + FILTER_SHARPEN_CONTENT_AGSL + FILTER_GRAIN_ADD_AGSL + """
float4 filterColorAt(float2 coord) {
    half4 sampled = content.eval(filterClampCoord(coord));
    float3 c = filterStraight(sampled);
    float level = clamp(uFilterIntensity, 0.0, 1.0);
    float focusScale = uResolution.x / 1600.0;
    float radius = uSoftFocus * level * (1.0 + uFocusRadiusCurve * (1.0 - level)) * focusScale;
    if (radius > 0.0) {
        c = directionalFocus(coord, radius, (uFocusSpread + uFocusSpreadGrowth * level) * focusScale,
            uFocusDetail, uFocusDetailRadius * focusScale, uFocusLift);
    }
    c = filterSharpenContent(c, coord);
    float3 graded = filterGrade(c);
    return float4(graded, float(sampled.a));
}

half4 main(float2 coord) {
    half4 src = content.eval(coord);
    float t = clamp(uIntensity, 0.0, 1.0);
    if (t <= 0.0) return src;
    float3 original = filterStraight(src);
    float3 c = filterColorAt(coord).rgb;
    if (uAberration > 0.0) {
        float amount = clamp(uAberration, 0.0, 1.0) * uAberrationRadius * 2.0 / 1600.0;
        float2 delta = (coord - uResolution * 0.5) * amount;
        float4 red = filterColorAt(coord + delta);
        float4 blue = filterColorAt(coord - delta);
        c.r = mix(c.r, red.r, red.a);
        c.b = mix(c.b, blue.b, blue.a);
    }
    c = filterGrainAdd(c, coord);
    return half4(half(mix(original.r, c.r, t) * float(src.a)),
        half(mix(original.g, c.g, t) * float(src.a)),
        half(mix(original.b, c.b, t) * float(src.a)), src.a);
}
"""

/** Horizontal triangular focus pass, matching the CPU's 49-tap horizontal stage. */
internal const val FILTER_FOCUS_H_AGSL = FILTER_UNIFORMS_AGSL + FILTER_BASE_AGSL + """
half4 main(float2 coord) {
    float level = clamp(uFilterIntensity, 0.0, 1.0);
    float focusScale = uResolution.x / 1600.0;
    float radius = uSoftFocus * level * (1.0 + uFocusRadiusCurve * (1.0 - level)) * focusScale;
    half4 src = content.eval(filterClampCoord(coord));
    if (radius <= 0.0) return src;
    float4 acc = float4(0.0, 0.0, 0.0, 0.0);
    for (int i = -24; i <= 24; i++) {
        float w = (24.0 - abs(float(i))) / 576.0;
        if (w > 0.0) {
            half4 h = content.eval(filterClampCoord(coord + float2(float(i) * radius / 24.0, 0.0)));
            acc += float4(float(h.r), float(h.g), float(h.b), float(h.a)) * w;
        }
    }
    return half4(half(acc.r), half(acc.g), half(acc.b), half(acc.a));
}
"""

/**
 * Vertical focus + local detail + sharpen + grade + grain + intensity mix. Consumes the horizontal
 * layer as `content` and the untouched stage input as `uSource`.
 */
internal const val FILTER_FOCUS_FINISH_AGSL = FILTER_UNIFORMS_AGSL +
    "uniform shader uSource;\n" +
    FILTER_BASE_AGSL + FILTER_SOURCE_AGSL + FILTER_GRAIN_AGSL + FILTER_GRAIN_ADD_AGSL + """
half4 main(float2 coord) {
    float level = clamp(uFilterIntensity, 0.0, 1.0);
    float focusScale = uResolution.x / 1600.0;
    half4 srcH = uSource.eval(filterClampCoord(coord));
    float3 original = filterStraight(srcH);
    float3 motion = original;
    if (level > 0.0 && uSoftFocus > 0.0) {
        float spread = max((uFocusSpread + uFocusSpreadGrowth * level) * focusScale, 0.0);
        float verticalOffset = 1.41421356 * spread;
        half4 above = content.eval(filterClampCoord(coord + float2(0.0, -verticalOffset)));
        half4 center = content.eval(filterClampCoord(coord));
        half4 below = content.eval(filterClampCoord(coord + float2(0.0, verticalOffset)));
        float4 motionPremul = float4(float(above.r), float(above.g), float(above.b), float(above.a)) * 0.25
            + float4(float(center.r), float(center.g), float(center.b), float(center.a)) * 0.5
            + float4(float(below.r), float(below.g), float(below.b), float(below.a)) * 0.25;
        float motionAlpha = motionPremul.a;
        float3 blurred = motionAlpha > 0.0
            ? float3(motionPremul.r, motionPremul.g, motionPremul.b) / motionAlpha
            : float3(0.0, 0.0, 0.0);
        motion = blurred;

        if (uFocusDetail > 0.0) {
            float localRadius = 1.41421356 * uFocusDetailRadius * focusScale;
            float3 local = float3(0.0, 0.0, 0.0);
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
                    local += filterSourceRgb(coord + float2(float(x), float(y)) * localRadius) * w;
                }
            }
            float3 src = filterSourceRgb(coord);
            float3 d = src - local;
            float3 room = float3(d.r >= 0.0 ? 1.0 - motion.r : motion.r,
                d.g >= 0.0 ? 1.0 - motion.g : motion.g,
                d.b >= 0.0 ? 1.0 - motion.b : motion.b);
            motion += uFocusDetail * d * room;
        }
        motion += uFocusLift * (1.0 - blurred);
        motion = clamp(motion, 0.0, 1.0);
    }
    motion = filterSharpenSource(motion, coord);
    float3 graded = filterGrade(motion);
    graded = filterGrainAdd(graded, coord);

    float alpha = float(srcH.a);
    float t = clamp(uIntensity, 0.0, 1.0);
    return half4(half(mix(original.r, graded.r, t) * alpha),
        half(mix(original.g, graded.g, t) * alpha),
        half(mix(original.b, graded.b, t) * alpha), half(alpha));
}
"""

/** Grade-only prepass used before the aberration finish. Consumes the source as `content`. */
internal const val FILTER_GRADE_AGSL = FILTER_UNIFORMS_AGSL + FILTER_BASE_AGSL +
    FILTER_SHARPEN_CONTENT_AGSL + """
half4 main(float2 coord) {
    half4 srcH = content.eval(filterClampCoord(coord));
    float3 base = filterStraight(srcH);
    base = filterSharpenContent(base, coord);
    float3 graded = filterGrade(base);
    float alpha = float(srcH.a);
    return half4(half(graded.r * alpha), half(graded.g * alpha), half(graded.b * alpha), half(alpha));
}
"""

/** Aberration + grain + intensity mix; samples the already-graded layer at channel offsets. */
internal const val FILTER_ABERRATION_AGSL = FILTER_UNIFORMS_AGSL +
    "uniform shader uOriginal;\n" +
    FILTER_BASE_AGSL + FILTER_GRAIN_AGSL + FILTER_GRAIN_ADD_AGSL + """
half4 main(float2 coord) {
    half4 gradedH = content.eval(filterClampCoord(coord));
    float3 c = filterStraight(gradedH);
    if (uAberration > 0.0) {
        float amount = clamp(uAberration, 0.0, 1.0) * uAberrationRadius * 2.0 / 1600.0;
        float2 delta = (coord - uResolution * 0.5) * amount;
        half4 red = content.eval(filterClampCoord(coord + delta));
        half4 blue = content.eval(filterClampCoord(coord - delta));
        c.r = mix(c.r, filterStraight(red).r, float(red.a));
        c.b = mix(c.b, filterStraight(blue).b, float(blue.a));
    }
    c = filterGrainAdd(c, coord);
    half4 srcH = uOriginal.eval(filterClampCoord(coord));
    float3 original = filterStraight(srcH);
    float alpha = float(srcH.a);
    float t = clamp(uIntensity, 0.0, 1.0);
    return half4(half(mix(original.r, c.r, t) * alpha),
        half(mix(original.g, c.g, t) * alpha),
        half(mix(original.b, c.b, t) * alpha), half(alpha));
}
"""

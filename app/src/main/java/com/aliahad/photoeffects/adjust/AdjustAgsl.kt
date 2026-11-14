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

/**
 * AGSL programs for the Adjust stage. The four pass sources mirror the CPU passes:
 * colour/HSL → detail → blur/refocus → optical/stylize finish. All controls are uniforms; only
 * the layer bits change pass topology. No external assets or LUTs are used.
 */
internal const val ADJUST_UNIFORMS_AGSL = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uExposure;
uniform float uBrightness;
uniform float uContrast;
uniform float uHighlights;
uniform float uWhites;
uniform float uShadows;
uniform float uBlackPoint;
uniform float uFade;
uniform float uBrilliance;
uniform float uPushPull;
uniform float uCleanup;
uniform float uSaturation;
uniform float uVibrance;
uniform float uColorDensity;
uniform float uTemperature;
uniform float uTint;
uniform float uSkintone;
uniform float uHdr;
uniform float uSharpen;
uniform float uUltrasharp;
uniform float uClarity;
uniform float uDehaze;
uniform float uMidtoneDetails;
uniform float uChromaDenoise;
uniform float uGrain;
uniform float uGrainSize;
uniform float uDust;
uniform float uVignette;
uniform float uVintage;
uniform float uRefocus;
uniform float uFocusPoint;
uniform float uFocusRange;
uniform float uFisheye;
uniform float uRgbSplit;
uniform float uAberration;
uniform float uMist;
uniform float uGlow;
uniform float uGaussianBlur;
uniform float uVignetteBlur;
uniform float uZoomBlur;
uniform float uRadialBlur;
uniform float uDirectionalBlur;
"""

internal const val ADJUST_COMMON_AGSL = """
uniform shader uOriginalAlpha;
uniform float uOpaqueSource;

float adjustLuma(float3 c) { return dot(c, float3(0.2126, 0.7152, 0.0722)); }

float3 adjustStraight(half4 c) {
    return c.a > 0.0
        ? float3(float(c.r), float(c.g), float(c.b)) / float(c.a)
        : float3(0.0, 0.0, 0.0);
}

float2 adjustClampCoord(float2 p) {
    return clamp(p, float2(0.5, 0.5), max(uResolution - float2(0.5, 0.5), float2(0.5, 0.5)));
}

// RenderNode coordinate interpolation can drift just off an exact texel center. At alpha zero,
// unpremultiplying even a tiny neighbour contribution changes the full straight colour.
float2 adjustPixelCenter(float2 p) { return floor(p) + float2(0.5); }

float4 adjustContent(float2 p) {
    half4 h = content.eval(adjustClampCoord(p));
    return float4(adjustStraight(h), float(h.a));
}

// Offscreen bilinear sampling can quantize tiny alpha before unpremultiplication. Match
// the CPU sampler by interpolating texels with original coverage on transparent images.
// JPEGs keep the single hardware sample; every editor stage preserves original coverage.
float4 adjustVertex(float2 p) {
    half4 h = content.eval(p);
    float a = float(uOriginalAlpha.eval(p).a);
    return float4(adjustStraight(h) * a, a);
}

float3 adjustSample(float2 p) {
    if (uOpaqueSource > 0.5) return adjustContent(p).rgb;
    float2 q = clamp(p - float2(0.5), float2(0.0), uResolution - float2(1.0));
    float2 i = floor(q);
    float2 f = q - i;
    float2 hi = uResolution - float2(0.5);
    float4 a = adjustVertex(i + float2(0.5));
    float4 b = adjustVertex(min(i + float2(1.5, 0.5), hi));
    float4 c = adjustVertex(min(i + float2(0.5, 1.5), hi));
    float4 d = adjustVertex(min(i + float2(1.5), hi));
    float4 h = mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    return h.a > 0.0 ? h.rgb / h.a : float3(0.0);
}

float adjustScreen(float a, float b) { return 1.0 - (1.0 - a) * (1.0 - b); }

float adjustHueWeight(float hue, float center, float halfWidth) {
    float d = abs(hue - center);
    d = min(d, 360.0 - d);
    return clamp(1.0 - d / halfWidth, 0.0, 1.0);
}

float3 adjustRgbToHsl(float3 c) {
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float l = (mx + mn) * 0.5;
    if (mx - mn < 0.00001) return float3(0.0, 0.0, l);
    float d = mx - mn;
    float s = l > 0.5 ? d / (2.0 - mx - mn) : d / (mx + mn);
    float h;
    if (mx == c.r) h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);
    else if (mx == c.g) h = (c.b - c.r) / d + 2.0;
    else h = (c.r - c.g) / d + 4.0;
    return float3(h * 60.0, s, l);
}

float adjustHueToRgb(float p, float q, float t) {
    float x = t;
    if (x < 0.0) x += 1.0;
    if (x > 1.0) x -= 1.0;
    if (x < 1.0 / 6.0) return p + (q - p) * 6.0 * x;
    if (x < 0.5) return q;
    if (x < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - x) * 6.0;
    return p;
}

float3 adjustHslToRgb(float3 hsl) {
    float h = hsl.x;
    float s = hsl.y;
    float l = hsl.z;
    if (s <= 0.00001) return float3(l, l, l);
    float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    float p = 2.0 * l - q;
    float hk = h;
    hk = hk - floor(hk / 360.0) * 360.0;
    hk /= 360.0;
    return float3(adjustHueToRgb(p, q, hk + 1.0 / 3.0),
        adjustHueToRgb(p, q, hk),
        adjustHueToRgb(p, q, hk - 1.0 / 3.0));
}

float3 adjustHslBand(float3 hsl, float3 v, float center, float halfWidth) {
    float w = adjustHueWeight(hsl.x, center, halfWidth);
    if (w <= 0.0 || (v.x == 0.0 && v.y == 0.0 && v.z == 0.0)) return hsl;
    float hue = hsl.x + 0.4 * v.x * w * 180.0 / 3.14159265;
    hue = hue - floor(hue / 360.0) * 360.0;
    return float3(hue, clamp(hsl.y * (1.0 + v.y * w), 0.0, 1.0), clamp(hsl.z + 0.25 * v.z * w, 0.0, 1.0));
}

float3 adjustBlur3(float2 p, float r) {
    float3 acc = float3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
            acc += adjustSample(p + float2(float(x), float(y)) * r) * w;
        }
    }
    return acc;
}

float3 adjustBlur9(float2 p, float r) {
    float3 acc = adjustSample(p) * 0.2;
    acc += adjustSample(p + float2(r, 0.0)) * 0.1;
    acc += adjustSample(p - float2(r, 0.0)) * 0.1;
    acc += adjustSample(p + float2(0.0, r)) * 0.1;
    acc += adjustSample(p - float2(0.0, r)) * 0.1;
    acc += adjustSample(p + float2(r * 0.7, r * 0.7)) * 0.1;
    acc += adjustSample(p + float2(-r * 0.7, r * 0.7)) * 0.1;
    acc += adjustSample(p + float2(r * 0.7, -r * 0.7)) * 0.1;
    acc += adjustSample(p - float2(r * 0.7, r * 0.7)) * 0.1;
    return acc;
}

float adjustBright(float2 p) {
    float l = adjustLuma(adjustSample(p));
    return clamp((l - 0.55) / 0.45, 0.0, 1.0);
}

float adjustBrightBlur9(float2 p, float r) {
    float acc = adjustBright(p) * 0.2;
    acc += adjustBright(p + float2(r, 0.0)) * 0.1;
    acc += adjustBright(p - float2(r, 0.0)) * 0.1;
    acc += adjustBright(p + float2(0.0, r)) * 0.1;
    acc += adjustBright(p - float2(0.0, r)) * 0.1;
    acc += adjustBright(p + float2(r * 0.7, r * 0.7)) * 0.1;
    acc += adjustBright(p + float2(-r * 0.7, r * 0.7)) * 0.1;
    acc += adjustBright(p + float2(r * 0.7, -r * 0.7)) * 0.1;
    acc += adjustBright(p - float2(r * 0.7, r * 0.7)) * 0.1;
    return acc;
}

float adjustRadialMask(float2 p) {
    float2 d = p / uResolution - float2(0.5, 0.5);
    return smoothstep(0.35, 0.95, length(d) * 1.2);
}
"""

internal const val ADJUST_COLOR_AGSL = ADJUST_UNIFORMS_AGSL + """
uniform float3 uHslRed;
uniform float3 uHslOrange;
uniform float3 uHslYellow;
uniform float3 uHslGreen;
uniform float3 uHslBlue;
uniform float3 uHslPurple;
""" + ADJUST_COMMON_AGSL + """
half4 main(float2 coord) {
    coord = adjustPixelCenter(coord);
    half4 src = content.eval(adjustClampCoord(coord));
    float3 c = adjustStraight(src);

    c *= pow(2.0, 0.5 * uExposure);
    if (uBrightness != 0.0) {
        float k = 0.15 * uBrightness;
        c = uBrightness > 0.0 ? c + k * (1.0 - c) : c + k * c;
    }
    if (uContrast != 0.0) c = 0.5 + (c - 0.5) * (1.0 + 0.6 * uContrast);
    float luma = adjustLuma(c);
    if (uHighlights != 0.0) {
        float m = smoothstep(0.55, 1.0, luma);
        c = uHighlights > 0.0 ? c + 0.35 * uHighlights * m * (1.0 - c)
            : c * (1.0 + 0.35 * uHighlights * m);
    }
    luma = adjustLuma(c);
    if (uShadows != 0.0) {
        float m = 1.0 - smoothstep(0.0, 0.45, luma);
        c = uShadows > 0.0 ? c + 0.30 * uShadows * m * (1.0 - c)
            : c * (1.0 + 0.30 * uShadows * m);
    }
    if (uBlackPoint != 0.0) {
        if (uBlackPoint > 0.0) {
            float bp = 0.12 * uBlackPoint;
            c = max(float3(0.0), (c - bp) / (1.0 - bp));
        } else {
            c += (-0.10 * uBlackPoint) * (1.0 - c);
        }
    }
    if (uWhites != 0.0) {
        float peak = clamp(max(c.r, max(c.g, c.b)), 0.0, 1.0);
        c *= 1.0 + 0.20 * uWhites * peak * peak * peak;
    }
    if (uFade != 0.0) {
        float f = 0.14 * uFade;
        c = f + c * (1.0 - f);
    }
    luma = adjustLuma(c);
    if (uBrilliance != 0.0) {
        float w = 1.0 - abs(2.0 * luma - 1.0);
        c = mix(float3(luma, luma, luma), c, 1.0 + 0.35 * uBrilliance * w);
    }
    if (uPushPull != 0.0) c = 0.5 + (c - 0.5) * (1.0 + 0.45 * uPushPull);

    luma = adjustLuma(c);
    if (uSaturation != 0.0) c = mix(float3(luma, luma, luma), c, 1.0 + uSaturation);
    if (uVibrance != 0.0) {
        float mx = max(c.r, max(c.g, c.b));
        float mn = min(c.r, min(c.g, c.b));
        float sat = mx > 0.0001 ? (mx - mn) / mx : 0.0;
        c = mix(float3(luma, luma, luma), c, 1.0 + 0.8 * uVibrance * (1.0 - sat));
    }
    if (uColorDensity != 0.0) {
        float k = 1.0 + 0.5 * uColorDensity;
        c = luma + (c - luma) * k;
    }
    if (uTemperature != 0.0) {
        c.r *= 1.0 + 0.12 * uTemperature;
        c.g *= 1.0 + 0.02 * uTemperature;
        c.b *= 1.0 - 0.12 * uTemperature;
    }
    if (uTint != 0.0) {
        c.r *= 1.0 + 0.04 * uTint;
        c.g *= 1.0 - 0.10 * uTint;
        c.b *= 1.0 + 0.04 * uTint;
    }
    c = clamp(c, 0.0, 1.0);
    if (uSkintone != 0.0) {
        float3 hsl0 = adjustRgbToHsl(c);
        if (hsl0.x >= 10.0 && hsl0.x <= 55.0) {
            float w = 1.0 - abs(hsl0.x - 32.0) / 23.0;
            hsl0.y = clamp(hsl0.y * (1.0 - 0.20 * uSkintone * w), 0.0, 1.0);
            c = adjustHslToRgb(hsl0);
        }
    }
    if (uHslRed.x != 0.0 || uHslRed.y != 0.0 || uHslRed.z != 0.0
        || uHslOrange.x != 0.0 || uHslOrange.y != 0.0 || uHslOrange.z != 0.0
        || uHslYellow.x != 0.0 || uHslYellow.y != 0.0 || uHslYellow.z != 0.0
        || uHslGreen.x != 0.0 || uHslGreen.y != 0.0 || uHslGreen.z != 0.0
        || uHslBlue.x != 0.0 || uHslBlue.y != 0.0 || uHslBlue.z != 0.0
        || uHslPurple.x != 0.0 || uHslPurple.y != 0.0 || uHslPurple.z != 0.0) {
        float3 hsl = adjustRgbToHsl(c);
        hsl = adjustHslBand(hsl, uHslRed, 0.0, 35.0);
        hsl = adjustHslBand(hsl, uHslOrange, 30.0, 32.0);
        hsl = adjustHslBand(hsl, uHslYellow, 60.0, 35.0);
        hsl = adjustHslBand(hsl, uHslGreen, 120.0, 48.0);
        hsl = adjustHslBand(hsl, uHslBlue, 240.0, 55.0);
        hsl = adjustHslBand(hsl, uHslPurple, 300.0, 42.0);
        c = adjustHslToRgb(hsl);
    }
    c = clamp(c, 0.0, 1.0);
    // Intermediate Adjust layers carry straight RGB at full 8-bit precision. Coverage remains
    // in uOriginalAlpha and the finish pass restores it; storing RGB premultiplied here would
    // discard colour precision at low alpha before a later blur/optical pass resamples it.
    return half4(half(c.r), half(c.g), half(c.b), 1.0);
}
"""

internal const val ADJUST_DETAIL_AGSL = ADJUST_UNIFORMS_AGSL + ADJUST_COMMON_AGSL + """
float3 adjustDenoiseChroma(float2 coord, float3 source) {
    if (uOriginalAlpha.eval(coord).a <= 0.0) return source;
    float luma = adjustLuma(source);
    float3 chroma = source - luma;
    float radius = max(1.0, floor(min(uResolution.x, uResolution.y) / 1080.0 + 0.5));
    float3 sum = float3(0.0);
    float total = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float4 neighbor = adjustVertex(adjustClampCoord(coord + float2(float(x), float(y)) * radius));
            if (neighbor.a > 0.0) {
                float3 rgb = neighbor.rgb / neighbor.a;
                float neighborLuma = adjustLuma(rgb);
                float3 neighborChroma = rgb - neighborLuma;
                float3 delta = neighborChroma - chroma;
                float weight = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) * neighbor.a;
                weight *= 1.0 - smoothstep(0.04, 0.18, abs(neighborLuma - luma));
                weight *= 1.0 - smoothstep(0.02, 0.14, dot(delta, delta));
                sum += neighborChroma * weight;
                total += weight;
            }
        }
    }
    chroma = mix(chroma, sum / total, uChromaDenoise);
    float rScale = chroma.r > 0.0 ? (1.0 - luma) / chroma.r : (chroma.r < 0.0 ? -luma / chroma.r : 1.0);
    float gScale = chroma.g > 0.0 ? (1.0 - luma) / chroma.g : (chroma.g < 0.0 ? -luma / chroma.g : 1.0);
    float bScale = chroma.b > 0.0 ? (1.0 - luma) / chroma.b : (chroma.b < 0.0 ? -luma / chroma.b : 1.0);
    return luma + chroma * min(1.0, min(rScale, min(gScale, bScale)));
}

half4 main(float2 coord) {
    coord = adjustPixelCenter(coord);
    float4 src = adjustContent(coord);
    float3 c = src.rgb;
    float luma = adjustLuma(c);
    float minDim = min(uResolution.x, uResolution.y);
    float wideRadius = max(minDim * 0.03, 2.0);
    float3 wide = adjustBlur9(coord, wideRadius);
    float wideLuma = adjustLuma(wide);

    if (uChromaDenoise != 0.0) c = adjustDenoiseChroma(coord, c);
    if (uCleanup != 0.0) {
        float detail = abs(luma - wideLuma);
        float lowDetail = 1.0 - smoothstep(0.02, 0.12, detail);
        c = mix(c, adjustBlur3(coord, 2.0), uCleanup * 0.55 * lowDetail);
    }
    if (uHdr != 0.0) {
        float lift = 0.25 * uHdr * (1.0 - luma);
        float compress = 1.0 / (1.0 + 0.20 * uHdr * luma);
        c = (c + lift * (1.0 - c)) * compress;
    }
    if (uSharpen != 0.0) {
        float3 blurred = adjustBlur3(coord, 1.0);
        c = clamp(c + (c - blurred) * uSharpen * 1.2, 0.0, 1.0);
    }
    if (uUltrasharp != 0.0) c += (luma - wideLuma) * uUltrasharp * 1.5;
    if (uClarity != 0.0) {
        float mask = 1.0 - abs(2.0 * luma - 1.0);
        c += (luma - wideLuma) * uClarity * 1.5 * mask;
    }
    if (uMidtoneDetails != 0.0) {
        float mask = 1.0 - abs(2.0 * luma - 1.0);
        c += (luma - wideLuma) * uMidtoneDetails * 1.2 * mask;
    }
    if (uDehaze != 0.0) {
        if (uDehaze > 0.0) {
            float veil = wideLuma * 0.5 * uDehaze;
            float transmission = max(1.0 - veil, 0.2);
            c = (c - veil * 0.6) / transmission;
        } else {
            float k = -uDehaze;
            c = c * (1.0 - 0.15 * k) + 0.10 * k;
        }
    }
    c = clamp(c, 0.0, 1.0);
    return half4(half(c.r), half(c.g), half(c.b), 1.0);
}
"""

internal const val ADJUST_BLUR_AGSL = ADJUST_UNIFORMS_AGSL + ADJUST_COMMON_AGSL + """
half4 main(float2 coord) {
    coord = adjustPixelCenter(coord);
    float4 src = adjustContent(coord);
    float3 c = src.rgb;
    float minDim = min(uResolution.x, uResolution.y);
    float2 center = uResolution * 0.5;
    float wideRadius = max(minDim * 0.03, 2.0);

    if (uGaussianBlur != 0.0) {
        c = mix(c, adjustBlur9(coord, max((0.002 + 0.03 * uGaussianBlur) * minDim, 1.0)), uGaussianBlur);
    }
    if (uMist != 0.0 || uGlow != 0.0) {
        float b = adjustBrightBlur9(coord, max(minDim * 0.04, 2.0));
        if (uMist != 0.0) {
            c.r = adjustScreen(c.r, b * uMist);
            c.g = adjustScreen(c.g, b * 0.82 * uMist);
            c.b = adjustScreen(c.b, b * 0.65 * uMist);
        }
        if (uGlow != 0.0) {
            c.r = adjustScreen(c.r, b * uGlow);
            c.g = adjustScreen(c.g, b * uGlow);
            c.b = adjustScreen(c.b, b * uGlow);
        }
    }
    if (uVignetteBlur != 0.0) {
        float k = uVignetteBlur * adjustRadialMask(coord);
        c = mix(c, adjustBlur9(coord, wideRadius), k);
    }
    if (uRefocus != 0.0) {
        float v = coord.y / uResolution.y;
        float band = 1.0 - smoothstep(uFocusRange * 0.25,
            uFocusRange * 0.25 + 0.08, abs(v - uFocusPoint));
        c = mix(c, adjustBlur9(coord, max(0.02 * minDim * uRefocus, 1.0)), (1.0 - band) * uRefocus);
    }
    if (uZoomBlur != 0.0) {
        float3 acc = float3(0.0);
        for (int i = 1; i <= 8; i++) {
            float t = float(i) / 8.0 * uZoomBlur * 0.06;
            acc += adjustSample(center + (coord - center) * (1.0 + t));
        }
        c = mix(c, acc / 8.0, 0.75);
    }
    if (uRadialBlur != 0.0) {
        float3 acc = float3(0.0);
        float2 d = coord - center;
        for (int i = 1; i <= 8; i++) {
            float a = float(i) / 8.0 * uRadialBlur * 0.08;
            float cs = cos(a);
            float sn = sin(a);
            acc += adjustSample(center + float2(d.x * cs - d.y * sn, d.x * sn + d.y * cs));
        }
        c = mix(c, acc / 8.0, 0.75);
    }
    if (uDirectionalBlur != 0.0) {
        float3 acc = float3(0.0);
        float len = uDirectionalBlur * 0.05 * minDim;
        float dx = cos(0.610865) * len;
        float dy = sin(0.610865) * len;
        for (int i = 0; i < 8; i++) {
            float t = (float(i) + 0.5) / 8.0 - 0.5;
            acc += adjustSample(coord + float2(dx * t, dy * t));
        }
        c = mix(c, acc / 8.0, 0.75);
    }
    c = clamp(c, 0.0, 1.0);
    return half4(half(c.r), half(c.g), half(c.b), 1.0);
}
"""

internal const val ADJUST_FINISH_AGSL = ADJUST_UNIFORMS_AGSL +
    com.aliahad.photoeffects.filter.FILTER_GRAIN_AGSL + ADJUST_COMMON_AGSL + """
half4 main(float2 coord) {
    coord = adjustPixelCenter(coord);
    float4 src = adjustContent(coord);
    float3 c = src.rgb;
    float alpha = float(uOriginalAlpha.eval(adjustClampCoord(coord)).a);
    if (uFisheye != 0.0) {
        float2 p = coord / uResolution - float2(0.5, 0.5);
        float r2 = dot(p, p);
        float f = 1.0 + 0.5 * uFisheye * r2;
        c = adjustSample((float2(0.5, 0.5) + p * f) * uResolution);
    }
    if (uRgbSplit != 0.0) {
        float2 d = (coord / uResolution - float2(0.5, 0.5)) * 0.012 * uRgbSplit;
        c.r = adjustSample(coord + d * uResolution).r;
        c.b = adjustSample(coord - d * uResolution).b;
    }
    if (uAberration != 0.0) {
        float2 d = (coord / uResolution - float2(0.5, 0.5));
        float scale = 0.006 * uAberration;
        c.r = adjustSample(coord + d * uResolution * scale).r;
        c.b = adjustSample(coord - d * uResolution * scale).b;
    }
    if (uVintage != 0.0) {
        float warm = smoothstep(0.35, 1.0, adjustLuma(c));
        c.r = c.r * (1.0 - 0.10 * uVintage) + 0.10 * uVintage + 0.04 * uVintage * warm;
        c.g = c.g * (1.0 - 0.08 * uVintage) + 0.08 * uVintage;
        c.b = c.b * (1.0 - 0.06 * uVintage) + 0.10 * uVintage;
        float luma = adjustLuma(c);
        float shadow = 1.0 - smoothstep(0.0, 0.55, luma);
        c = mix(float3(luma, luma, luma), c, 1.0 - 0.22 * uVintage * shadow);
    }
    if (uGrain != 0.0) {
        float luma = clamp(adjustLuma(c), 0.0, 1.0);
        float response = 0.35 + 0.65 * sqrt(4.0 * luma * (1.0 - luma));
        float noise = filterGrain(coord, uResolution, uSeed, uGrainSize) * uGrain * 0.14 * response;
        c = clamp(c + float3(noise, noise, noise), 0.0, 1.0);
    }
    if (uDust != 0.0) {
        float n = filterGrain(coord, uResolution, uSeed + 53.0, 1.0);
        if (abs(n) > 0.90 - 0.03 * abs(uDust)) {
            float ink = uDust > 0.0 ? 0.96 : 0.04;
            c = mix(c, float3(ink, ink, ink), clamp(abs(uDust) * 0.85, 0.0, 1.0));
        }
    }
    if (uVignette != 0.0) {
        float mask = adjustRadialMask(coord);
        if (uVignette > 0.0) c *= 1.0 - 0.55 * uVignette * mask;
        else c = 1.0 - (1.0 - c) * (1.0 + 0.45 * (-uVignette) * mask);
    }
    c = clamp(c, 0.0, 1.0);
    float t = clamp(uIntensity, 0.0, 1.0);
    float3 outc = mix(src.rgb, c, t);
    return half4(half(outc.r * alpha), half(outc.g * alpha), half(outc.b * alpha), half(alpha));
}
"""

package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.filter.FILTER_GRAIN_AGSL

internal const val SCREENPRINT_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uPitch;
uniform float uCosine;
uniform float uSine;
uniform float uPaperR;
uniform float uPaperG;
uniform float uPaperB;
uniform float uInkR;
uniform float uInkG;
uniform float uInkB;
uniform float uSecondR;
uniform float uSecondG;
uniform float uSecondB;
uniform float uDual;
uniform float uGrain;
""" + FILTER_GRAIN_AGSL + """
float printDot(float2 cell, float shade, float edge) {
    float2 d = fract(cell) - 0.5;
    float radius = 0.5 * shade;
    float mask = 1.0 - smoothstep(radius - edge, radius + edge, dot(d, d));
    float fading = mask * smoothstep(0.0, 0.10, shade);
    return mix(fading, 1.0, smoothstep(0.88, 1.0, shade));
}
half4 main(float2 fragCoord) {
    half4 srcH = content.eval(fragCoord);
    float t = clamp(uIntensity, 0.0, 1.0);
    if (t <= 0.0 || srcH.a <= 0.0) return srcH;
    float alpha = float(srcH.a);
    float3 src = float3(srcH.rgb) / alpha;
    float shade = 1.0 - dot(src, float3(0.2126, 0.7152, 0.0722));
    float cell = max(2.0, uPitch * max(uResolution.x, uResolution.y) / 1600.0);
    float edge = max(0.02, 0.75 / cell);
    float2 grid = float2(fragCoord.x * uCosine + fragCoord.y * uSine,
        -fragCoord.x * uSine + fragCoord.y * uCosine) / cell;
    float amount = printDot(grid, shade, edge);
    float3 c = float3(uPaperR, uPaperG, uPaperB) * mix(float3(1.0), float3(uInkR, uInkG, uInkB), amount);
    if (uDual > 0.5) {
        float secondShade = clamp(shade * 0.72 + (src.b - src.r) * 0.22, 0.0, 1.0);
        float second = printDot(float2(-grid.y + 0.22, grid.x + 0.12), secondShade, edge);
        c *= mix(float3(1.0), float3(uSecondR, uSecondG, uSecondB), second);
    }
    float grain = uGrain > 0.0 ? filterGrain(fragCoord, uResolution, uSeed, 1.8) * uGrain : 0.0;
    return half4(half3(mix(src, clamp(c + grain, 0.0, 1.0), t) * alpha), srcH.a);
}
"""

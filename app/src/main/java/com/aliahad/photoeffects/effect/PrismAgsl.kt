package com.aliahad.photoeffects.effect

/**
 * GPU colour-lookup pipeline shared by all Prism presets. The first variant applies
 * ellipse-masked soft focus before trilinear colour lookup. Other variants sample
 * the original directly. Atlas indices and intensity blending mirror CpuPrismRenderer.
 */
internal const val PRISM_AGSL: String = """
uniform shader content;
uniform shader uLut;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uLutOriginX;
uniform float uLutOriginY;
uniform float uEllipseOn;
uniform float uEllipseCx;
uniform float uEllipseCy;
uniform float uEllipseRx;
uniform float uEllipseRy;
uniform float uEllipseSoft;

const float LUT_MAX = 32.0;
const float LUT_TILE = 33.0;
const float LUT_TILES = 6.0;

// RuntimeShader child colors are premultiplied; effect math uses straight RGB like the CPU.
float3 sourceStraight(half4 h) {
    return h.a > 0.0 ? float3(float(h.r), float(h.g), float(h.b)) / float(h.a)
        : float3(0.0);
}

float3 sampleRgb(float2 p) {
    float2 hi = max(uResolution - float2(0.5), float2(0.5));
    half4 h = content.eval(clamp(p, float2(0.5), hi));
    return sourceStraight(h);
}

// One LUT node (r, g, b in 0..32) read at its exact texel centre.
float3 lutNode(float r, float g, float b) {
    float tileCol = mod(b, LUT_TILES);
    float tileRow = floor(b / LUT_TILES);
    float2 p = float2(
        uLutOriginX + tileCol * LUT_TILE + r + 0.5,
        uLutOriginY + tileRow * LUT_TILE + g + 0.5
    );
    half4 h = uLut.eval(p);
    return float3(h.r, h.g, h.b);
}

float3 lutLookup(float3 c) {
    float3 f = clamp(c, 0.0, 1.0) * LUT_MAX;
    float3 i0 = min(floor(f), LUT_MAX - 1.0);
    float3 fr = clamp(f - i0, 0.0, 1.0);

    float3 c00 = mix(lutNode(i0.x, i0.y, i0.z), lutNode(i0.x + 1.0, i0.y, i0.z), fr.x);
    float3 c10 = mix(lutNode(i0.x, i0.y + 1.0, i0.z), lutNode(i0.x + 1.0, i0.y + 1.0, i0.z), fr.x);
    float3 c01 = mix(lutNode(i0.x, i0.y, i0.z + 1.0), lutNode(i0.x + 1.0, i0.y, i0.z + 1.0), fr.x);
    float3 c11 = mix(lutNode(i0.x, i0.y + 1.0, i0.z + 1.0), lutNode(i0.x + 1.0, i0.y + 1.0, i0.z + 1.0), fr.x);

    float3 c0 = mix(c00, c10, fr.y);
    float3 c1 = mix(c01, c11, fr.y);
    return mix(c0, c1, fr.z);
}

// Separable 3x3 [1,2,1] kernel, calibrated for variant-one soft focus (sigma ~0.7 px at 1600).
float3 blur3x3(float2 p) {
    float3 acc = float3(0.0);
    acc += sampleRgb(p + float2(-1.0, -1.0));
    acc += sampleRgb(p + float2(0.0, -1.0)) * 2.0;
    acc += sampleRgb(p + float2(1.0, -1.0));
    acc += sampleRgb(p + float2(-1.0, 0.0)) * 2.0;
    acc += sampleRgb(p) * 4.0;
    acc += sampleRgb(p + float2(1.0, 0.0)) * 2.0;
    acc += sampleRgb(p + float2(-1.0, 1.0));
    acc += sampleRgb(p + float2(0.0, 1.0)) * 2.0;
    acc += sampleRgb(p + float2(1.0, 1.0));
    return acc * 0.0625;
}

half4 main(float2 fragCoord) {
    float t = clamp(uIntensity, 0.0, 1.0);
    half4 srcH = content.eval(fragCoord);
    if (t <= 0.0) {
        return srcH;
    }

    float3 src = sourceStraight(srcH);
    float3 base = src;

    if (uEllipseOn > 0.5) {
        float2 res = max(uResolution, float2(1.0, 1.0));
        float2 uv = fragCoord / res;
        float dx = (uv.x - uEllipseCx) / max(uEllipseRx, 0.0001);
        float dy = (uv.y - uEllipseCy) / max(uEllipseRy, 0.0001);
        float d = sqrt(dx * dx + dy * dy);
        float m = smoothstep(1.0, 1.0 + max(uEllipseSoft, 0.0001), d);
        base = mix(src, blur3x3(fragCoord), m);
    }

    float3 c = lutLookup(base);
    float3 outc = mix(src, c, t);
    float alpha = float(srcH.a);
    return half4(half(outc.x * alpha), half(outc.y * alpha), half(outc.z * alpha), srcH.a);
}
"""

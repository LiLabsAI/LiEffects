package com.aliahad.photoeffects.effect

/**
 * The AGSL program shared by all KUJI presets (PREQUEL "kuji" pack, 5 variants).
 *
 * Stages (mirrored by [com.aliahad.photoeffects.effect.cpu.CpuKujiRenderer]):
 *   1. per-channel cubic tone curves
 *   2. saturation mix + per-channel gain/offset
 *   3. monochrome film grain (cell uGrainCell * min(res)/1080)
 *   4. paper frame overlay: the bundled `kuji_frame_N.png` is bound as `uniform shader uFrame`
 *      with the same local matrix as `content`, so `uFrame.eval(fragCoord)` is the exact frame
 *      pixel; composition is premultiplied (`frame.rgb + c * (1 - frame.a)`)
 *   5. intensity blend
 *
 * All helper functions are defined inside this program: the AGSL programs are compiled
 * independently, so nothing is shared between shader strings.
 */
internal const val KUJI_AGSL: String = """
uniform shader content;
uniform shader uFrame;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uToneR3;
uniform float uToneR2;
uniform float uToneR1;
uniform float uToneR0;
uniform float uToneG3;
uniform float uToneG2;
uniform float uToneG1;
uniform float uToneG0;
uniform float uToneB3;
uniform float uToneB2;
uniform float uToneB1;
uniform float uToneB0;
uniform float uSatMix;
uniform float uGainR;
uniform float uOffR;
uniform float uGainG;
uniform float uOffG;
uniform float uGainB;
uniform float uOffB;
uniform float uGrainAmp;
uniform float uGrainCell;

// RuntimeShader child colors are premultiplied; effect math uses straight RGB like the CPU.
float3 sourceStraight(half4 h) {
    return h.a > 0.0 ? float3(float(h.r), float(h.g), float(h.b)) / float(h.a)
        : float3(0.0);
}

float fract1(float x) { return x - floor(x); }

float hashF(float2 cell, float seed) {
    float2 c = cell * 0.1 + float2(seed * 0.137, seed * 0.291);
    return fract1(sin(dot(c, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float t = clamp(uIntensity, 0.0, 1.0);
    half4 srcH = content.eval(fragCoord);
    if (t <= 0.0) {
        return srcH;
    }

    float2 res = max(uResolution, float2(1.0, 1.0));
    float minDim = min(res.x, res.y);
    float3 src = sourceStraight(srcH);

    // ------------------------------------------------- 1. per-channel cubic tone curves
    float3 c = float3(
        ((uToneR3 * src.r + uToneR2) * src.r + uToneR1) * src.r + uToneR0,
        ((uToneG3 * src.g + uToneG2) * src.g + uToneG1) * src.g + uToneG0,
        ((uToneB3 * src.b + uToneB2) * src.b + uToneB1) * src.b + uToneB0
    );

    // ------------------------------------------------- 2. saturation mix + gain/offset
    float gray = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(float3(gray, gray, gray), c, 1.0 - clamp(uSatMix, 0.0, 1.0));
    c = float3(
        uGainR * c.r + uOffR,
        uGainG * c.g + uOffG,
        uGainB * c.b + uOffB
    );

    // ------------------------------------------------- 3. monochrome film grain
    float cell = max(uGrainCell * (minDim / 1080.0), 1.0);
    float gv = hashF(floor(fragCoord / cell), uSeed + 37.0);
    float grain = (gv - 0.5) * (uGrainAmp / 0.2887);
    c = clamp(c + grain, 0.0, 1.0);

    // ------------------------------------------------- 4. paper frame overlay (premultiplied)
    half4 f = uFrame.eval(fragCoord);
    c = float3(f.r, f.g, f.b) + c * (1.0 - clamp(f.a, 0.0, 1.0));

    // ------------------------------------------------- 5. intensity blend
    float3 outc = mix(src, c, t);
    float alpha = float(srcH.a);
    return half4(half(outc.x * alpha), half(outc.y * alpha), half(outc.z * alpha), srcH.a);
}
"""

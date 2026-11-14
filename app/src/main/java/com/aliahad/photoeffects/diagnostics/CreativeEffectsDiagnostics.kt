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

package com.aliahad.photoeffects.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.canvas.CanvasBitmapRenderer
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.ScreenprintFamily
import com.aliahad.photoeffects.effect.SolsticeFamily
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import com.aliahad.photoeffects.filter.FilterRegistry
import kotlin.math.abs

/** Production CPU/GPU comparisons, shared by the debug harness and instrumented regression. */
object CreativeEffectsDiagnostics {
    data class Result(val checked: Int, val failed: Int, val maxMean: Double, val maxRgb: Int, val maxAlpha: Int,
                      val maxMirrorRgb: Int, val maxCanvasRgb: Int)

    fun run(context: Context): Result {
        var checked = 0
        var failed = 0
        var maxMean = 0.0
        var maxRgb = 0
        var maxAlpha = 0
        var maxMirrorRgb = 0
        var maxCanvasRgb = 0
        fun verify(effect: PhotoEffect, width: Int, height: Int, transparent: Boolean,
                   strength: Float = 1f, seed: Long = 42L, canvas: CanvasParams? = null) {
            val pixels = IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val alpha = if (!transparent) 255 else intArrayOf(0, 32, 64, 128, 192, 255)[(x / 7 + y / 5) % 6]
                (alpha shl 24) or ((x * 255 / (width - 1).coerceAtLeast(1)) shl 16) or
                    ((y * 255 / (height - 1).coerceAtLeast(1)) shl 8) or ((x * 17 + y * 11) % 256)
            }
            val source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            source.setHasAlpha(transparent)
            try {
                val canonical = IntArray(pixels.size)
                source.getPixels(canonical, 0, width, 0, 0, width, height)
                val before = canonical.copyOf()
                var expected = effect.cpu.apply(canonical, width, height, strength, seed)
                val geometry = canvas?.geometry(width, height)
                if (geometry != null) {
                    val look = Bitmap.createBitmap(expected, width, height, Bitmap.Config.ARGB_8888)
                    try {
                        val framed = CanvasBitmapRenderer.render(look, geometry)
                        try {
                            expected = IntArray(framed.width * framed.height)
                            framed.getPixels(expected, 0, framed.width, 0, 0, framed.width, framed.height)
                        } finally { framed.recycle() }
                    } finally { look.recycle() }
                }
                val actual = GpuReadback.render(context, source, effect.gpu, width, height, seed, strength, geometry)
                var worst = 0
                var alphaError = 0
                var sum = 0L
                var worstIndex = 0
                if (actual != null && actual.size == expected.size) for (i in expected.indices) {
                    val alpha = expected[i] ushr 24
                    alphaError = maxOf(alphaError, abs(alpha - (actual[i] ushr 24)))
                    for (shift in intArrayOf(0, 8, 16)) {
                        val premultiplied = (((expected[i] ushr shift) and 255) * alpha + 127) / 255
                        val error = abs(premultiplied - ((actual[i] ushr shift) and 255))
                        if (error > worst) { worst = error; worstIndex = i }
                        sum += error
                    }
                }
                val mean = sum.toDouble() / (expected.size * 3)
                // Multiple offscreen stores plus spatial resampling can add one A8 step.
                val alphaLimit = if (canvas != null) 2 else 1
                // The rotated, mirrored high-contrast fixture reaches 8 with Filter + Adjust
                // alone on the emulator. Keep that existing Canvas resampling bound separate
                // from the stricter shader-mirror limit; never relax the direct effect checks.
                val rgbLimit = if (canvas != null) 8 else 4
                val passes = actual != null && actual.size == expected.size && canonical.contentEquals(before) &&
                    mean < 0.8 && worst <= rgbLimit && alphaError <= alphaLimit
                checked++
                if (!passes) failed++
                maxMean = maxOf(maxMean, mean)
                maxRgb = maxOf(maxRgb, worst)
                maxAlpha = maxOf(maxAlpha, alphaError)
                if (canvas == null) maxMirrorRgb = maxOf(maxMirrorRgb, worst)
                else maxCanvasRgb = maxOf(maxCanvasRgb, worst)
                val message = "Creative ${effect.id} ${width}x$height alpha=$transparent strength=$strength " +
                    "canvas=${canvas != null} seed=$seed mean=$mean max=$worst rgbLimit=$rgbLimit alphaError=$alphaError passed=$passes " +
                    "worstIndex=$worstIndex cpu=${expected[worstIndex].toUInt().toString(16)} " +
                    "gpu=${actual?.getOrNull(worstIndex)?.toUInt()?.toString(16)}"
                if (passes) Log.i("RenderDiagnostics", message) else Log.e("RenderDiagnostics", message)
            } finally { source.recycle() }
        }

        val filter = FilterRegistry.byId("lucent_1")!!
        val adjust = AdjustEffectFactory.create(AdjustParams().with(AdjustControl.Exposure, 20))
        val crop = CanvasParams(CanvasRatio.SQUARE, centerX = 0.4, centerY = 0.6, zoom = 1.4,
            quarterTurns = 1, straighten = 13.0, mirrored = true)
        val base = EditComposition.compose(EditStage(filter, 70), EditStage(adjust, 100), null)!!
        verify(base, 241, 161, false)
        verify(base, 241, 161, false, canvas = crop)
        for (effect in SolsticeFamily.effects + ScreenprintFamily.effects) {
            for (transparent in listOf(false, true)) for (strength in listOf(0f, 0.35f, 1f)) {
                verify(effect, 193, 129, transparent, strength)
            }
            verify(effect, 641, 427, false)
            verify(effect, 71, 107, true, seed = Long.MAX_VALUE)
            verify(effect, 1, 17, true)
            verify(effect, 17, 1, true)
            val composed = EditComposition.compose(EditStage(filter, 70), EditStage(adjust, 100), EditStage(effect, 70))!!
            verify(composed, 241, 161, false)
            verify(composed, 241, 161, false, canvas = crop)
        }
        val result = Result(checked, failed, maxMean, maxRgb, maxAlpha, maxMirrorRgb, maxCanvasRgb)
        Log.i("RenderDiagnostics", "Creative effects complete: checked=$checked failed=$failed " +
            "maxMean=$maxMean maxRgb=$maxRgb maxAlpha=$maxAlpha maxMirrorRgb=$maxMirrorRgb maxCanvasRgb=$maxCanvasRgb")
        return result
    }
}

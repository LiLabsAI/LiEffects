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

package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.canvas.CanvasParams
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Source-limited resolution tiers, JPEG quality and metadata settings.
 *
 * A tier is only offered when the source is at least that large (export never upscales); the
 * highest offered tier exports at the source size and lower tiers use their nominal long edge
 * scaled to the source aspect, rounded to even pixels.
 */
enum class ExportResolution(val longSide: Int, val shortSide: Int, val label: String) {
    HD(1280, 720, "HD"),
    FULL_HD(1920, 1080, "Full HD"),
    FOUR_K(3840, 2160, "4K"),
}

/** JPEG encoding qualities and the size-estimate coefficients used by the export sheet. */
enum class ExportQuality(
    val jpegQuality: Int,
    val label: String,
    val hint: String,
    val estimatedBytesPerPixel: Double,
) {
    LOW(40, "Low", "Smaller size", 0.09),
    MEDIUM(75, "Medium", "Balanced", 0.18),
    HIGH(95, "High", "Full detail", 0.35),
}

enum class ExportMetadataMode(val label: String, val hint: String) {
    OVERRIDE("Override", "As new media"),
    KEEP("Keep", "Copy of original"),
}

/**
 * [resolution] defaults to [ExportResolution.FOUR_K], which [ExportPlanner] resolves to the
 * largest tier the photo can fill (i.e. the source size).
 */
data class ExportOptions(
    val resolution: ExportResolution = ExportResolution.FOUR_K,
    val quality: ExportQuality = ExportQuality.HIGH,
    val metadata: ExportMetadataMode = ExportMetadataMode.OVERRIDE,
)

data class ResolutionOption(
    val resolution: ExportResolution,
    val width: Int,
    val height: Int,
    val enabled: Boolean,
    val renderWidth: Int = width,
    val renderHeight: Int = height,
)

data class ExportPlan(
    val resolution: ExportResolution,
    val width: Int,
    val height: Int,
    val quality: ExportQuality,
    val metadata: ExportMetadataMode,
    val estimatedBytes: Long,
    val renderWidth: Int = width,
    val renderHeight: Int = height,
)

object ExportPlanner {

    private const val MB = 1_048_576L

    /** List the tiers from smallest to largest: HD, Full HD, 4K. */
    val resolutions: List<ExportResolution> = ExportResolution.entries

    /**
     * One row per tier, in display order, with the pixels that tier would export for this
     * source. Sizes are capped by [ExportBudget] because the CPU mirrors cannot render every
     * source at full resolution; the displayed size is the size that will actually be written.
     *
     * If the source is smaller than the HD tier, all tiers would be "Not available"; the
     * Full HD row is then offered at the source size so saving stays possible.
     */
    fun resolutionOptions(
        sourceWidth: Int,
        sourceHeight: Int,
        effect: PhotoEffect?,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
        canvas: CanvasParams = CanvasParams(),
    ): List<ResolutionOption> {
        val geometry = canvas.geometry(sourceWidth, sourceHeight)
        val (cropWidth, cropHeight) = geometry.outputSize()
        val sourceLong = max(cropWidth, cropHeight)
        val sourceShort = min(cropWidth, cropHeight)
        // Geometry is last: budget the entire rendered source, plus its cropped destination.
        val maxPixels = ExportBudget.maxPixels(effect, maxMemoryBytes, if (canvas.isIdentity) 0 else 12)
        val topAvailable = resolutions.lastOrNull { isAvailable(it, sourceLong, sourceShort) }
        return resolutions.map { resolution ->
            val offered = topAvailable?.let { isAvailable(resolution, sourceLong, sourceShort) }
                ?: (resolution == ExportResolution.FULL_HD)
            if (!offered) {
                ResolutionOption(resolution, 0, 0, enabled = false)
            } else {
                val (width, height) = if (topAvailable == null || resolution == topAvailable) {
                    cropWidth to cropHeight
                } else {
                    nominalSize(resolution, cropWidth, cropHeight)
                }
                if (canvas.isIdentity) {
                    val (cappedWidth, cappedHeight) = ExportBudget.targetSize(width, height, maxPixels)
                    ResolutionOption(resolution, cappedWidth, cappedHeight, enabled = true)
                } else {
                    val scale = if (topAvailable == null || resolution == topAvailable) 1.0 else
                        min(width / geometry.cropWidth, height / geometry.cropHeight).coerceAtMost(1.0)
                    val (renderWidth, renderHeight) = ExportBudget.targetSize(
                        // Decode enough samples for the requested tier; flooring can make a
                        // nominal 720×1280 Story export lose one column and two rows.
                        ceil(sourceWidth * scale).toInt().coerceAtLeast(1),
                        ceil(sourceHeight * scale).toInt().coerceAtLeast(1), maxPixels,
                    )
                    val (outputWidth, outputHeight) = geometry.outputSize(renderWidth, renderHeight)
                    ResolutionOption(resolution, outputWidth, outputHeight, true, renderWidth, renderHeight)
                }
            }
        }
    }

    /** Parse [source] dims as [ExportResolution] using [requested] and the same fallbacks as [plan]. */
    fun effectiveResolution(
        sourceWidth: Int,
        sourceHeight: Int,
        requested: ExportResolution,
        effect: PhotoEffect? = null,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
        canvas: CanvasParams = CanvasParams(),
    ): ExportResolution =
        select(resolutionOptions(sourceWidth, sourceHeight, effect, maxMemoryBytes, canvas), requested)
            .resolution

    /**
     * The export that will actually run. When the requested tier is unavailable (smaller
     * source), fall back to Full HD and then to the largest offered tier.
     */
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        options: ExportOptions,
        effect: PhotoEffect?,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
        canvas: CanvasParams = CanvasParams(),
    ): ExportPlan {
        val chosen = select(
            resolutionOptions(sourceWidth, sourceHeight, effect, maxMemoryBytes, canvas),
            options.resolution,
        )
        return ExportPlan(
            resolution = chosen.resolution,
            width = chosen.width,
            height = chosen.height,
            quality = options.quality,
            metadata = options.metadata,
            estimatedBytes = estimateBytes(chosen.width, chosen.height, options.quality),
            renderWidth = chosen.renderWidth,
            renderHeight = chosen.renderHeight,
        )
    }

    /** Approximate JPEG size: pixels × 0.35 / 0.18 / 0.09 bytes for High / Medium / Low. */
    fun estimateBytes(width: Int, height: Int, quality: ExportQuality): Long =
        (width.toLong() * height * quality.estimatedBytesPerPixel).roundToLong()

    /** Show whole megabytes, so 1440×1920 @ High (~0.92 MB) reads "~1 MB". */
    fun formatEstimate(bytes: Long): String {
        val megabytes = (bytes.toDouble() / MB).roundToInt().coerceAtLeast(1)
        return "~$megabytes MB"
    }

    private fun select(
        options: List<ResolutionOption>,
        requested: ExportResolution,
    ): ResolutionOption =
        options.firstOrNull { it.enabled && it.resolution == requested }
            ?: options.firstOrNull { it.enabled && it.resolution == ExportResolution.FULL_HD }
            ?: options.lastOrNull { it.enabled }
            ?: options.first()

    private fun isAvailable(
        resolution: ExportResolution,
        sourceLong: Int,
        sourceShort: Int,
    ): Boolean = sourceLong >= resolution.longSide && sourceShort >= resolution.shortSide

    private fun nominalSize(
        resolution: ExportResolution,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Int, Int> {
        if (sourceWidth == sourceHeight) return resolution.longSide to resolution.longSide
        val aspect = sourceWidth.toFloat() / sourceHeight
        return if (aspect > 1f) {
            resolution.longSide to evenRound(resolution.longSide / aspect)
        } else {
            evenRound(resolution.longSide * aspect) to resolution.longSide
        }
    }

    private fun evenRound(value: Float): Int {
        val rounded = value.roundToInt()
        return (rounded - rounded % 2).coerceAtLeast(2)
    }
}

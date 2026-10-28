package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.effect.PhotoEffect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * PREQUEL 2.5.0 photo export settings, recovered from `km7` / `hm7` / `bm7` / `nfd` / `il7`.
 *
 * A tier is only offered when the source is at least that large (PREQUEL never upscales); the
 * highest offered tier exports at the source size and lower tiers use their nominal long edge
 * scaled to the source aspect, rounded to even pixels.
 */
enum class ExportResolution(val longSide: Int, val shortSide: Int, val label: String) {
    HD(1280, 720, "HD"),
    FULL_HD(1920, 1080, "Full HD"),
    FOUR_K(3840, 2160, "4K"),
}

/** The exact JPEG qualities PREQUEL passes to its encoder, plus its size-estimate coefficients. */
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
 * largest tier the photo can fill (i.e. the source size) — PREQUEL's default export.
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
)

data class ExportPlan(
    val resolution: ExportResolution,
    val width: Int,
    val height: Int,
    val quality: ExportQuality,
    val metadata: ExportMetadataMode,
    val estimatedBytes: Long,
)

object ExportPlanner {

    private const val MB = 1_048_576L

    /** PREQUEL lists the tiers from smallest to largest: HD, Full HD, 4K. */
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
    ): List<ResolutionOption> {
        val sourceLong = max(sourceWidth, sourceHeight)
        val sourceShort = min(sourceWidth, sourceHeight)
        val maxPixels = ExportBudget.maxPixels(effect, maxMemoryBytes)
        val topAvailable = resolutions.lastOrNull { isAvailable(it, sourceLong, sourceShort) }
        return resolutions.map { resolution ->
            val offered = topAvailable?.let { isAvailable(resolution, sourceLong, sourceShort) }
                ?: (resolution == ExportResolution.FULL_HD)
            if (!offered) {
                ResolutionOption(resolution, 0, 0, enabled = false)
            } else {
                val (width, height) = if (topAvailable == null || resolution == topAvailable) {
                    sourceWidth to sourceHeight
                } else {
                    nominalSize(resolution, sourceWidth, sourceHeight)
                }
                val (cappedWidth, cappedHeight) = ExportBudget.targetSize(width, height, maxPixels)
                ResolutionOption(resolution, cappedWidth, cappedHeight, enabled = true)
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
    ): ExportResolution =
        select(resolutionOptions(sourceWidth, sourceHeight, effect, maxMemoryBytes), requested)
            .resolution

    /**
     * The export that will actually run. When the requested tier is unavailable (smaller
     * source), PREQUEL falls back to Full HD and then to the largest offered tier.
     */
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        options: ExportOptions,
        effect: PhotoEffect?,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    ): ExportPlan {
        val chosen = select(
            resolutionOptions(sourceWidth, sourceHeight, effect, maxMemoryBytes),
            options.resolution,
        )
        return ExportPlan(
            resolution = chosen.resolution,
            width = chosen.width,
            height = chosen.height,
            quality = options.quality,
            metadata = options.metadata,
            estimatedBytes = estimateBytes(chosen.width, chosen.height, options.quality),
        )
    }

    /** PREQUEL's `il7` estimate: pixels × 0.35 / 0.18 / 0.09 bytes for High / Medium / Low. */
    fun estimateBytes(width: Int, height: Int, quality: ExportQuality): Long =
        (width.toLong() * height * quality.estimatedBytesPerPixel).roundToLong()

    /** PREQUEL shows a whole number of MB, so 1440×1920 @ High (~0.92 MB) reads "~1 MB". */
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

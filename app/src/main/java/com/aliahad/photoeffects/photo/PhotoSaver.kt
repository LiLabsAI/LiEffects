package com.aliahad.photoeffects.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.render.EffectBitmapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the edited photo to the public gallery under Pictures/PhotoEffects.
 *
 * The effect is rendered on the CPU from a fresh decode of the source at the size picked by
 * [ExportPlanner] (bounded by [ExportBudget] so the heaviest CPU mirror still fits the heap),
 * written to a cache file so the EXIF block can be applied, then streamed through MediaStore —
 * no storage permission is required on API 29+. If a render runs out of memory, the resolution
 * is quartered and retried.
 */
object PhotoSaver {

    /** Retry floor for an out-of-memory shrink; explicit low resolutions start above this. */
    private const val MIN_RETRY_PIXELS = 500_000

    data class Result(
        val success: Boolean,
        val displayPath: String?,
        val error: String?,
        val width: Int = 0,
        val height: Int = 0,
    )

    suspend fun save(
        context: Context,
        photo: LoadedPhoto,
        effect: PhotoEffect?,
        intensityPercent: Int,
        options: ExportOptions = ExportOptions(),
    ): Result = withContext(Dispatchers.Default) {
        val plan = ExportPlanner.plan(photo.sourceWidth, photo.sourceHeight, options, effect)
        val basePixels = plan.width.toLong() * plan.height
        val attempts = generateSequence(basePixels) { it / 4 }
            .takeWhile { it >= MIN_RETRY_PIXELS }
            .toList()
            .ifEmpty { listOf(basePixels) }
        var source: Bitmap? = null
        var rendered: Bitmap? = null
        var lastError: Throwable? = null
        for (pixels in attempts) {
            try {
                val (width, height) = ExportBudget.targetSize(plan.width, plan.height, pixels.toInt())
                val decoded = PhotoLoader.decodeForExport(context, photo, width, height)
                source = decoded
                rendered = if (effect != null) {
                    EffectBitmapRenderer.renderFull(decoded, effect, intensityPercent, photo.seed)
                } else {
                    decoded
                }
                break
            } catch (oom: OutOfMemoryError) {
                lastError = oom
                if (source != null && source !== photo.bitmap) source.recycle()
                source = null
            }
        }
        val bitmap = rendered ?: return@withContext Result(
            false,
            null,
            lastError?.let { "Not enough memory to export this photo" } ?: "Export failed",
        )
        try {
            withContext(Dispatchers.IO) { writeToGallery(context, bitmap, plan, photo) }
        } finally {
            if (bitmap !== photo.bitmap) bitmap.recycle()
            if (source != null && source !== photo.bitmap && source !== bitmap) source.recycle()
        }
    }

    private fun writeToGallery(
        context: Context,
        bitmap: Bitmap,
        plan: ExportPlan,
        photo: LoadedPhoto,
    ): Result {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "PhotoEffects_$timestamp.jpg"
        val relativePath = "${Environment.DIRECTORY_PICTURES}/PhotoEffects"
        val cacheFile = File(context.cacheDir, "export_${System.nanoTime()}.jpg")
        return try {
            cacheFile.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, plan.quality.jpegQuality, output)) {
                    error("JPEG compression failed")
                }
            }
            // Metadata is best-effort: a source that no longer has readable EXIF must not
            // abort an otherwise successful export.
            runCatching { ExportMetadata.write(context, photo, cacheFile, displayName, plan.metadata) }
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: return Result(false, null, "MediaStore insert returned null")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    cacheFile.inputStream().use { it.copyTo(output) }
                } ?: error("Could not open output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result(true, "$relativePath/$displayName", null, bitmap.width, bitmap.height)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                Result(false, null, t.message ?: t.javaClass.simpleName)
            }
        } catch (t: Throwable) {
            Result(false, null, t.message ?: t.javaClass.simpleName)
        } finally {
            cacheFile.delete()
        }
    }
}

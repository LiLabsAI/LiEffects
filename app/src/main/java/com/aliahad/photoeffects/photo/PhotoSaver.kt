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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the edited photo to the public gallery under Pictures/PhotoEffects.
 *
 * The effect is rendered on the CPU at export resolution (deterministic, mirrors the GPU
 * preview) and written through MediaStore, so no storage permission is required on API 29+.
 */
object PhotoSaver {

    data class Result(val success: Boolean, val displayPath: String?, val error: String?)

    suspend fun save(
        context: Context,
        photo: LoadedPhoto,
        effect: PhotoEffect?,
        intensityPercent: Int,
    ): Result = withContext(Dispatchers.Default) {
        val rendered: Bitmap = if (effect != null) {
            EffectBitmapRenderer.render(
                photo.bitmap,
                effect,
                intensityPercent,
                photo.seed,
                EffectBitmapRenderer.EXPORT_MAX_DIMENSION,
            )
        } else {
            EffectBitmapRenderer.downscale(photo.bitmap, EffectBitmapRenderer.EXPORT_MAX_DIMENSION)
        }
        withContext(Dispatchers.IO) { writeToGallery(context, rendered) }
    }

    private fun writeToGallery(context: Context, bitmap: Bitmap): Result {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "PhotoEffects_$timestamp.jpg"
        val relativePath = "${Environment.DIRECTORY_PICTURES}/PhotoEffects"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: return Result(false, null, "MediaStore insert returned null")
        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                    error("JPEG compression failed")
                }
            } ?: error("Could not open output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result(true, "$relativePath/$displayName", null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            Result(false, null, t.message ?: t.javaClass.simpleName)
        }
    }
}

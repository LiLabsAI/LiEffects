package com.aliahad.photoeffects

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.photo.ExportMetadataMode
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportResolution
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.photo.PhotoLoader
import com.aliahad.photoeffects.photo.PhotoSaver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks for the PREQUEL-style resolution / quality / metadata export options. */
@RunWith(AndroidJUnit4::class)
class ExportOptionsInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun hd_option_exports_1280_wide() {
        val source = syntheticPhoto(4000, 3000)
        val result = runBlocking {
            PhotoSaver.save(
                context,
                load(source),
                null,
                100,
                ExportOptions(resolution = ExportResolution.HD),
            )
        }
        assertTrue("save failed: ${result.error}", result.success)
        assertEquals(1280, result.width)
        assertEquals(960, result.height)
        deleteExport(result.displayPath!!)
        source.delete()
    }

    @Test
    fun override_writes_fresh_metadata_without_camera_tags() {
        val source = syntheticPhoto(2000, 1500)
        addSourceExif(source)
        val result = runBlocking {
            PhotoSaver.save(
                context,
                load(source),
                null,
                100,
                ExportOptions(metadata = ExportMetadataMode.OVERRIDE),
            )
        }
        assertTrue("save failed: ${result.error}", result.success)
        val uri = exportedUri(result.displayPath!!) ?: error("export not found in MediaStore")
        try {
            val exif = context.contentResolver.openInputStream(uri)!!.use { ExifInterface(it) }
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
            assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals(
                ExifInterface.ORIENTATION_NORMAL,
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0),
            )
            assertFalse(exif.getLatLong(FloatArray(2)))
        } finally {
            deleteExport(result.displayPath!!)
            source.delete()
        }
    }

    @Test
    fun keep_copies_camera_and_location_tags() {
        val source = syntheticPhoto(2000, 1500)
        addSourceExif(source)
        val result = runBlocking {
            PhotoSaver.save(
                context,
                load(source),
                null,
                100,
                ExportOptions(metadata = ExportMetadataMode.KEEP),
            )
        }
        assertTrue("save failed: ${result.error}", result.success)
        val uri = exportedUri(result.displayPath!!) ?: error("export not found in MediaStore")
        try {
            val exif = context.contentResolver.openInputStream(uri)!!.use { ExifInterface(it) }
            assertEquals("TestMake", exif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("TestModel", exif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals(
                "2020:01:02 03:04:05",
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            )
            assertEquals(
                ExifInterface.ORIENTATION_NORMAL,
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0),
            )
            val latLong = FloatArray(2)
            assertTrue("GPS missing from kept metadata", exif.getLatLong(latLong))
            assertEquals(48.8584f, latLong[0], 0.001f)
            assertEquals(2.2945f, latLong[1], 0.001f)
        } finally {
            deleteExport(result.displayPath!!)
            source.delete()
        }
    }

    private fun load(file: File): LoadedPhoto =
        runBlocking { PhotoLoader.loadFromUri(context, file.toUri()) }

    private fun syntheticPhoto(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = x * 255 / width
                val g = y * 255 / height
                val b = (x + y) * 255 / (width + height)
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val file = File(context.cacheDir, "source_${width}x$height.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun addSourceExif(file: File) {
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "TestMake")
            setAttribute(ExifInterface.TAG_MODEL, "TestModel")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2020:01:02 03:04:05")
            setLatLong(48.8584, 2.2945)
            saveAttributes()
        }
    }

    private fun exportedUri(displayPath: String): Uri? {
        val displayName = displayPath.substringAfterLast('/')
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }

    private fun deleteExport(displayPath: String) {
        val uri = exportedUri(displayPath) ?: return
        context.contentResolver.delete(uri, null, null)
    }
}

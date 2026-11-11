package com.aliahad.photoeffects

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.photo.ExportBudget
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.photo.PhotoLoader
import com.aliahad.photoeffects.photo.PhotoSaver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end export check on a real 12 MP (4000 x 3000) photo.
 *
 * Push the source with
 * `adb push test_12mp.jpg /sdcard/Android/data/com.aliahad.photoeffects/files/Test_12MP.jpg`;
 * the tests are skipped when it is absent. Verifies that saving no longer throws away
 * resolution (the old export was capped at 1440 px) while memory-heavy effects still fit.
 */
@RunWith(AndroidJUnit4::class)
class EffectExportInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun prism_export_keeps_full_12mp_resolution() {
        val photo = loadTestPhoto() ?: return
        val effect = requireNotNull(EffectRegistry.byId("prism_2"))
        val result = runBlocking { PhotoSaver.save(context, photo, effect, 100) }
        assertTrue("save failed: ${result.error}", result.success)
        val exported = queryExported(result.displayPath!!.substringAfterLast('/'))!!
        Log.i(TAG, "prism_2 export ${exported.width}x${exported.height} ${exported.size} B")
        assertEquals(4000, exported.width)
        assertEquals(3000, exported.height)
        assertTrue("prism_2 export is only ${exported.size} B", exported.size > 1_500_000)
    }

    @Test
    fun heaviest_effect_export_beats_the_old_1440_cap() {
        val photo = loadTestPhoto() ?: return
        val effect = requireNotNull(EffectRegistry.byId("velour_1"))
        val budget = ExportBudget.maxPixels(effect)
        val result = runBlocking { PhotoSaver.save(context, photo, effect, 100) }
        assertTrue("save failed: ${result.error}", result.success)
        val exported = queryExported(result.displayPath!!.substringAfterLast('/'))!!
        Log.i(TAG, "velour_1 budget=$budget export ${exported.width}x${exported.height} ${exported.size} B")
        assertTrue("velour_1 export is only ${exported.width}px wide", exported.width > 1440)
        assertTrue("velour_1 export is only ${exported.size} B", exported.size > 1_000_000)
    }

    @Test
    fun report_heap_limit() {
        val maxMemory = Runtime.getRuntime().maxMemory()
        Log.i(TAG, "maxMemory=${maxMemory / (1024 * 1024)} MB")
        assertTrue(maxMemory >= 256L * 1024 * 1024)
    }

    private fun loadTestPhoto(): LoadedPhoto? {
        val file = File(context.getExternalFilesDir(null), TEST_NAME)
        assumeTrue("$TEST_NAME missing at $file", file.exists())
        return runBlocking { PhotoLoader.loadFromUri(context, file.toUri()) }
    }

    private data class Exported(val width: Int, val height: Int, val size: Long)

    private fun queryExported(name: String): Exported? {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.SIZE,
            ),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return Exported(cursor.getInt(0), cursor.getInt(1), cursor.getLong(2))
            }
        }
        return null
    }

    private companion object {
        const val TAG = "EffectExportTest"
        const val TEST_NAME = "Test_12MP.jpg"
    }
}

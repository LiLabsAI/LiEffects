package com.aliahad.photoeffects.photo

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the EXIF block of an exported JPEG.
 *
 * "Override" mode ("As new media") writes only fresh capture metadata — no camera,
 * lens or location tags (verified on its exports). "Keep" ("Copy of original") carries the
 * original photo's capture tags over. Either way the pixels we write are already upright
 * (ImageDecoder applies the source orientation), so the orientation tag is reset to normal.
 */
internal object ExportMetadata {

    /** Capture tags carried over in [ExportMetadataMode.KEEP]; ExifInterface has no enumeration. */
    private val KEPT_TAGS = listOf(
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_USER_COMMENT,
    )

    fun write(
        context: Context,
        photo: LoadedPhoto,
        jpeg: File,
        displayName: String,
        mode: ExportMetadataMode,
    ) {
        val exif = ExifInterface(jpeg.absolutePath)
        if (mode == ExportMetadataMode.KEEP) {
            copyFromSource(context, photo, exif)
        } else {
            val now = Date()
            val dateTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(now)
            val subSeconds = SimpleDateFormat("SSS", Locale.US).format(now)
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, subSeconds)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subSeconds)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subSeconds)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, displayName)
        }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    }

    private fun copyFromSource(context: Context, photo: LoadedPhoto, out: ExifInterface) {
        val source = runCatching { openSource(context, photo) }.getOrNull() ?: return
        for (tag in KEPT_TAGS) {
            val value = source.getAttribute(tag) ?: continue
            // Some tags read back in a display format their setter cannot parse; skip those.
            runCatching { out.setAttribute(tag, value) }
        }
        val latLong = FloatArray(2)
        if (source.getLatLong(latLong)) {
            out.setLatLong(latLong[0].toDouble(), latLong[1].toDouble())
        }
        val altitude = source.getAltitude(Double.NaN)
        if (!altitude.isNaN()) out.setAltitude(altitude)
        for (tag in listOf(
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
        )) {
            val value = source.getAttribute(tag) ?: continue
            runCatching { out.setAttribute(tag, value) }
        }
    }

    private fun openSource(context: Context, photo: LoadedPhoto): ExifInterface? {
        val uri = photo.sourceUri?.let(Uri::parse)
        return if (uri != null) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                ExifInterface(pfd.fileDescriptor)
            }
        } else {
            context.assets.open(PhotoLoader.DEMO_ASSET).use { ExifInterface(it) }
        }
    }
}

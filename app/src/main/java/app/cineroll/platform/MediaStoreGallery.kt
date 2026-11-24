package app.cineroll.platform

import android.content.ContentValues
import android.content.Context
import android.media.ExifInterface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.cineroll.shoot.Gallery
import app.cineroll.shoot.GalleryException
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Saves developed Exposures to `Pictures/Cineroll`. Needs no storage permission on Android 10+. */
class MediaStoreGallery(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : Gallery {

    override fun save(jpeg: ByteArray) {
        val resolver = context.contentResolver
        val moment = now()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(moment))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CINEROLL_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cineroll")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw GalleryException("Gallery refused the new image")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw GalleryException("Could not open the new image")
            stream.use { it.write(jpeg) }
            // Publishing re-reads the file and takes the date taken from its EXIF. The developed JPEG has
            // none, so write it in first, or the gallery has no date for the Exposure.
            val pending = resolver.openFileDescriptor(uri, "rw") ?: throw GalleryException("Could not reopen the new image")
            pending.use { writeDateTaken(it.fileDescriptor, moment) }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is GalleryException) e else GalleryException("Could not write the image", e)
        }
    }

    /** Writes the moment into the JPEG as its EXIF date taken, in UTC with an explicit offset. */
    private fun writeDateTaken(file: FileDescriptor, moment: Long) {
        val exif = ExifInterface(file)
        val utc = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, utc.format(Date(moment)))
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, "%03d".format(Math.floorMod(moment, 1000L)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+00:00")
        }
        exif.saveAttributes()
    }
}

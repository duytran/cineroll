package app.cineroll.platform

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import app.cineroll.shoot.Gallery
import app.cineroll.shoot.GalleryException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves developed Exposures to `Pictures/Cineroll`. Needs no storage permission on Android 10+. */
class MediaStoreGallery(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : Gallery {

    override fun save(jpeg: ByteArray) {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now()))
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
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is GalleryException) e else GalleryException("Could not write the image", e)
        }
    }
}

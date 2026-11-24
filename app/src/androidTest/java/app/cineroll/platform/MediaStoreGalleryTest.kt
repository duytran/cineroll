package app.cineroll.platform

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreGalleryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A real, tiny JPEG: the gallery writes the date taken into it, so it has to be a valid file. */
    private fun smallJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun cineRollImageIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("Pictures/Cineroll/%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    private fun idUri(id: Long) = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    private fun deleteAll(ids: Set<Long>) {
        ids.forEach { context.contentResolver.delete(idUri(it), null, null) }
    }

    @Test
    fun recordsTheMomentOfSavingAsTheDateTaken() {
        val before = cineRollImageIds()
        val moment = 1_700_000_000_000L

        MediaStoreGallery(context, now = { moment }).save(smallJpeg())

        val added = cineRollImageIds() - before
        try {
            assertEquals(1, added.size)
            val dateTaken = context.contentResolver.query(
                idUri(added.single()), arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null,
            )!!.use { cursor ->
                cursor.moveToFirst()
                if (cursor.isNull(0)) null else cursor.getLong(0)
            }
            assertNotNull("the gallery has no date taken", dateTaken)
            assertTrue("date taken $dateTaken is not near $moment", abs(dateTaken!! - moment) < 1000)
        } finally {
            deleteAll(added)
        }
    }

    @Test
    fun savesTheJpegIntoThePicturesCineRollAlbum() {
        val before = cineRollImageIds()

        MediaStoreGallery(context).save(smallJpeg())

        val added = cineRollImageIds() - before
        try {
            assertEquals(1, added.size)
            val stored = context.contentResolver.openInputStream(idUri(added.single()))!!.use { it.readBytes() }
            val decoded = BitmapFactory.decodeByteArray(stored, 0, stored.size)
            assertNotNull("saved file is not a readable JPEG", decoded)
            assertEquals(16, decoded.width)
            assertEquals(8, decoded.height)
        } finally {
            deleteAll(added)
        }
    }
}

package app.cineroll.platform

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreGalleryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

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

    @Test
    fun savesTheJpegIntoThePicturesCineRollAlbum() {
        val before = cineRollImageIds()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

        MediaStoreGallery(context).save(jpeg)

        val added = cineRollImageIds() - before
        try {
            assertEquals(1, added.size)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, added.single())
            val stored = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(jpeg, stored)
        } finally {
            added.forEach { id ->
                context.contentResolver.delete(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null,
                )
            }
        }
    }
}

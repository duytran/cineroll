package app.cineroll.develop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import app.cineroll.film.Film
import java.io.ByteArrayOutputStream

class GpuDeveloper(private val jpegQuality: Int = 95) : Developer {

    override fun develop(image: CapturedImage, film: Film): ByteArray {
        try {
            val source = decodeUpright(image)
            try {
                val developed = FilmRenderer().render(source, film)
                try {
                    return encode(developed)
                } finally {
                    developed.recycle()
                }
            } finally {
                source.recycle()
            }
        } catch (e: DevelopException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw DevelopException("Out of memory while developing", e)
        } catch (e: RuntimeException) {
            throw DevelopException("Could not develop the Exposure", e)
        }
    }

    private fun decodeUpright(image: CapturedImage): Bitmap {
        val decoded = BitmapFactory.decodeByteArray(image.jpeg, 0, image.jpeg.size)
            ?: throw DevelopException("Captured image is not a valid JPEG")
        if (image.rotationDegrees % 360 == 0) return decoded
        val matrix = Matrix().apply { postRotate(image.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, false)
        decoded.recycle()
        return rotated
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
            throw DevelopException("Could not encode the developed JPEG")
        }
        return out.toByteArray()
    }
}

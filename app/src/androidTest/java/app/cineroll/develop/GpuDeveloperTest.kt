package app.cineroll.develop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cineroll.film.FilmMath
import app.cineroll.film.Films
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpuDeveloperTest {

    private val developer = GpuDeveloper()
    private val noGrain = Films.Everyday400.copy(grain = 0f)

    private val quadrantColours = listOf(
        Color.rgb(200, 60, 40), Color.rgb(30, 160, 90),
        Color.rgb(40, 70, 200), Color.rgb(128, 128, 128),
    )

    /** Four solid quadrants, so JPEG blocks stay clean at the quadrant centres. */
    private fun fourColourJpeg(width: Int = 64, height: Int = 64): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halfW = width / 2
        val halfH = height / 2
        val rects = listOf(
            Rect(0, 0, halfW, halfH), Rect(halfW, 0, width, halfH),
            Rect(0, halfH, halfW, height), Rect(halfW, halfH, width, height),
        )
        rects.forEachIndexed { i, rect -> canvas.drawRect(rect, Paint().apply { color = quadrantColours[i] }) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }

    private fun decode(jpeg: ByteArray): Bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    @Test
    fun matchesTheFilmMathAtSamplePoints() {
        val input = fourColourJpeg()
        val inputBitmap = decode(input)
        val output = decode(developer.develop(CapturedImage(input, 0), noGrain))

        assertEquals(64, output.width)
        assertEquals(64, output.height)
        for ((x, y) in listOf(16 to 16, 48 to 16, 16 to 48, 48 to 48)) {
            val src = inputBitmap.getPixel(x, y)
            val expected = FilmMath.develop(
                floatArrayOf(Color.red(src) / 255f, Color.green(src) / 255f, Color.blue(src) / 255f),
                noGrain,
            )
            val got = output.getPixel(x, y)
            assertNear(expected[0] * 255f, Color.red(got), "red at $x,$y")
            assertNear(expected[1] * 255f, Color.green(got), "green at $x,$y")
            assertNear(expected[2] * 255f, Color.blue(got), "blue at $x,$y")
        }
    }

    @Test
    fun appliesTheCaptureRotation() {
        val input = fourColourJpeg(width = 64, height = 32)
        val output = decode(developer.develop(CapturedImage(input, 90), noGrain))
        assertEquals(32, output.width)
        assertEquals(64, output.height)
    }

    @Test
    fun grainIsDeterministicAndVisible() {
        val input = fourColourJpeg()
        val grainy = Films.Everyday400.copy(grain = 0.1f)
        val first = developer.develop(CapturedImage(input, 0), grainy)
        val second = developer.develop(CapturedImage(input, 0), grainy)
        val clean = developer.develop(CapturedImage(input, 0), grainy.copy(grain = 0f))

        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(clean))

        val bitmap = decode(first)
        val distinct = (4..11).flatMap { x -> (4..11).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("grain should vary pixels inside a flat area", distinct.size > 1)
    }

    @Test
    fun badJpegThrowsDevelopException() {
        assertThrows(DevelopException::class.java) {
            developer.develop(CapturedImage(byteArrayOf(1, 2, 3), 0), noGrain)
        }
    }

    private fun assertNear(expected: Float, actual: Int, what: String) {
        // 4/255 covers GPU rounding plus the JPEG round trip. Raise to 6 only if a device proves noisier.
        assertTrue("$what: expected ~$expected, got $actual", abs(expected - actual) <= 4f)
    }
}

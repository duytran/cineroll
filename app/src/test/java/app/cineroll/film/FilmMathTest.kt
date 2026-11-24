package app.cineroll.film

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FilmMathTest {

    private val neutral = Film("neutral", 1f, 1f, 1f, 1f, 1f, 0f, 0f)

    @Test
    fun neutralFilmLeavesColoursAlone() {
        val out = FilmMath.develop(floatArrayOf(0.2f, 0.5f, 0.8f), neutral)
        assertArrayEquals(floatArrayOf(0.2f, 0.5f, 0.8f), out, 0.0001f)
    }

    @Test
    fun liftRaisesBlacksAndKeepsWhite() {
        val lifted = neutral.copy(lift = 0.1f)
        assertEquals(0.1f, FilmMath.develop(floatArrayOf(0f, 0f, 0f), lifted)[0], 0.0001f)
        assertEquals(1f, FilmMath.develop(floatArrayOf(1f, 1f, 1f), lifted)[0], 0.0001f)
    }

    @Test
    fun zeroSaturationGivesGrey() {
        val grey = neutral.copy(saturation = 0f)
        val out = FilmMath.develop(floatArrayOf(1f, 0f, 0f), grey)
        assertEquals(out[0], out[1], 0.0001f)
        assertEquals(out[1], out[2], 0.0001f)
        assertEquals(0.2126f, out[0], 0.0001f)
    }

    @Test
    fun gainsShiftEachChannel() {
        val warm = neutral.copy(redGain = 1.2f, blueGain = 0.5f)
        val out = FilmMath.develop(floatArrayOf(0.5f, 0.5f, 0.5f), warm)
        assertArrayEquals(floatArrayOf(0.6f, 0.5f, 0.25f), out, 0.0001f)
    }

    @Test
    fun contrastPivotsAroundMidGreyAndResultIsClamped() {
        val punchy = neutral.copy(contrast = 2f)
        val out = FilmMath.develop(floatArrayOf(0.1f, 0.5f, 0.9f), punchy)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), out, 0.0001f)
    }
}

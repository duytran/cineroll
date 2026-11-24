package app.cineroll.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmTest {

    @Test
    fun fourBuiltInFilmsInOrder() {
        assertEquals(
            listOf("Everyday 400", "Daylight 100", "Faded 800", "Mono 400"),
            Films.all.map { it.name },
        )
        assertSame(Films.Everyday400, Films.all[0])
        assertSame(Films.Daylight100, Films.all[1])
        assertSame(Films.Faded800, Films.all[2])
        assertSame(Films.Mono400, Films.all[3])
    }

    @Test
    fun everyBuiltInFilmHasADescription() {
        Films.all.forEach { assertTrue("${it.name} needs a description", it.description.isNotBlank()) }
    }

    @Test
    fun everyday400KeepsItsSlice1Values() {
        val film = Films.Everyday400
        assertEquals(1.04f, film.redGain, 0f)
        assertEquals(1.0f, film.greenGain, 0f)
        assertEquals(0.94f, film.blueGain, 0f)
        assertEquals(1.1f, film.saturation, 0f)
        assertEquals(1.15f, film.contrast, 0f)
        assertEquals(0.03f, film.lift, 0f)
        assertEquals(0.06f, film.grain, 0f)
    }

    @Test
    fun monoHasNoColour() {
        val mono = Films.Mono400
        assertEquals(0f, mono.saturation, 0f)
        assertEquals(mono.redGain, mono.greenGain, 0f)
        assertEquals(mono.greenGain, mono.blueGain, 0f)
    }

    @Test
    fun findsEveryBuiltInFilmByName() {
        Films.all.forEach { assertSame(it, Films.byName(it.name)) }
        assertNull(Films.byName("No such film"))
    }

    @Test
    fun rejectsParametersOutOfRange() {
        val ok = Films.Everyday400
        assertThrows(IllegalArgumentException::class.java) { ok.copy(redGain = 1.6f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(saturation = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(contrast = 2.1f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(lift = 0.3f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(grain = 0.5f) }
    }

    @Test
    fun rejectsABlankNameOrDescription() {
        val ok = Films.Everyday400
        assertThrows(IllegalArgumentException::class.java) { ok.copy(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(description = " ") }
    }
}

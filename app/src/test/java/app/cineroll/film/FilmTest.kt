package app.cineroll.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class FilmTest {

    @Test
    fun builtInFilmIsValidAndNamed() {
        val film = Films.Everyday400
        assertEquals("Everyday 400", film.name)
        assertEquals(listOf(film), Films.all)
    }

    @Test
    fun findsBuiltInFilmByName() {
        assertSame(Films.Everyday400, Films.byName("Everyday 400"))
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
}

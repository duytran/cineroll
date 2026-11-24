package app.cineroll.roll

import app.cineroll.film.Films
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RollTest {

    @Test
    fun newRollIs35mmWith36ExposuresLeft() {
        val roll = Roll.new(Films.Everyday400)
        assertEquals(Format.MM35, roll.format)
        assertEquals(36, roll.total)
        assertEquals(0, roll.used)
        assertEquals(36, roll.left)
        assertFalse(roll.isFinished)
    }

    @Test
    fun exposeUsesOneExposure() {
        val roll = Roll.new(Films.Everyday400).expose()
        assertEquals(1, roll.used)
        assertEquals(35, roll.left)
    }

    @Test
    fun rollFinishesAfterThe36thExposure() {
        var roll = Roll.new(Films.Everyday400)
        repeat(35) { roll = roll.expose() }
        assertFalse(roll.isFinished)
        roll = roll.expose()
        assertTrue(roll.isFinished)
        assertEquals(0, roll.left)
    }

    @Test
    fun finishedRollRefusesAnotherExposure() {
        val finished = Roll(Films.Everyday400, Format.MM35, used = 36)
        assertThrows(IllegalStateException::class.java) { finished.expose() }
    }

    @Test
    fun aNewRollIsNotEnded() {
        assertFalse(Roll.new(Films.Everyday400).ended)
    }

    @Test
    fun rewindFinishesTheRollAndKeepsTheCount() {
        val rewound = Roll.new(Films.Everyday400).expose().expose().rewind()

        assertTrue(rewound.ended)
        assertTrue(rewound.isFinished)
        assertEquals(2, rewound.used)
    }

    @Test
    fun rewindOnAFinishedRollThrows() {
        val used = Roll(Films.Everyday400, Format.MM35, used = 36)
        val ended = Roll.new(Films.Everyday400).rewind()
        assertThrows(IllegalStateException::class.java) { used.rewind() }
        assertThrows(IllegalStateException::class.java) { ended.rewind() }
    }

    @Test
    fun exposeAfterRewindThrows() {
        val rewound = Roll.new(Films.Everyday400).expose().rewind()
        assertThrows(IllegalStateException::class.java) { rewound.expose() }
    }

    @Test
    fun rejectsUsedCountOutsideTheRoll() {
        assertThrows(IllegalArgumentException::class.java) { Roll(Films.Everyday400, Format.MM35, used = 37) }
        assertThrows(IllegalArgumentException::class.java) { Roll(Films.Everyday400, Format.MM35, used = -1) }
    }
}

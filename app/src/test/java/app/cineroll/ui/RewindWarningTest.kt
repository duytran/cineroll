package app.cineroll.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RewindWarningTest {

    @Test
    fun saysHowManyExposuresWillBeLost() {
        assertEquals("The 23 Exposures left will be lost.", rewindWarning(23))
        assertEquals("The 36 Exposures left will be lost.", rewindWarning(36))
    }

    @Test
    fun usesTheSingularForOneExposure() {
        assertEquals("The 1 Exposure left will be lost.", rewindWarning(1))
    }
}

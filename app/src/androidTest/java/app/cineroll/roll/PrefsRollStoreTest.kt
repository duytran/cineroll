package app.cineroll.roll

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cineroll.film.Films
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsRollStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("cineroll_roll", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun firstLaunchHasNoRoll() {
        assertNull(PrefsRollStore(context).load())
    }

    @Test
    fun savedRollComesBackWithSameFilmAndCount() {
        val roll = Roll.new(Films.Everyday400).expose().expose()
        PrefsRollStore(context).save(roll)
        assertEquals(roll, PrefsRollStore(context).load())
    }

    @Test
    fun finishedRollStaysFinished() {
        val finished = Roll(Films.Everyday400, Format.MM35, used = 36)
        PrefsRollStore(context).save(finished)
        assertTrue(PrefsRollStore(context).load()!!.isFinished)
    }

    @Test
    fun aRewoundRollStaysEnded() {
        val rewound = Roll.new(Films.Everyday400).expose().rewind()
        PrefsRollStore(context).save(rewound)

        val loaded = PrefsRollStore(context).load()!!
        assertEquals(rewound, loaded)
        assertTrue(loaded.ended)
        assertEquals(1, loaded.used)
    }

    @Test
    fun aRollSavedBySlice1WithoutEndedLoadsAsNotEnded() {
        prefs.edit().putString("film", "Everyday 400").putString("format", "MM35").putInt("used", 4).commit()

        val loaded = PrefsRollStore(context).load()!!
        assertFalse(loaded.ended)
        assertEquals(4, loaded.used)
    }

    @Test
    fun clearRemovesTheSavedRoll() {
        val store = PrefsRollStore(context)
        store.save(Roll.new(Films.Everyday400))

        store.clear()

        assertNull(PrefsRollStore(context).load())
    }

    @Test
    fun unknownFilmCountsAsNoRoll() {
        prefs.edit().putString("film", "No such film").putString("format", "MM35").putInt("used", 3).commit()
        assertNull(PrefsRollStore(context).load())
    }
}

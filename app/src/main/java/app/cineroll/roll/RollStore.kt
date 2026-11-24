package app.cineroll.roll

import android.content.Context
import app.cineroll.film.Films
import java.io.IOException

/** Keeps the one loaded Roll so it survives closing the app. */
interface RollStore {
    /** The saved Roll, or `null` if nothing valid is saved. */
    fun load(): Roll?

    /** @throws IOException if the Roll could not be written. */
    fun save(roll: Roll)
}

class PrefsRollStore(context: Context) : RollStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("cineroll_roll", Context.MODE_PRIVATE)

    override fun load(): Roll? {
        val film = prefs.getString("film", null)?.let(Films::byName) ?: return null
        val format = prefs.getString("format", null)
            ?.let { name -> Format.entries.firstOrNull { it.name == name } } ?: return null
        val used = prefs.getInt("used", -1)
        if (used !in 0..format.exposures) return null
        return Roll(film, format, used)
    }

    override fun save(roll: Roll) {
        val written = prefs.edit()
            .putString("film", roll.film.name)
            .putString("format", roll.format.name)
            .putInt("used", roll.used)
            .commit()
        if (!written) throw IOException("Could not save the Roll")
    }
}

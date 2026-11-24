package app.cineroll.roll

import app.cineroll.film.Film

/** The frame shape of a Camera. It sets how many Exposures a Roll holds. */
enum class Format(val exposures: Int) {
    MM35(36),
}

/**
 * One Film loaded into one Camera, with a fixed number of Exposures set by the Format.
 * [ended] is true after a Rewind; the unused Exposures are then lost.
 */
data class Roll(val film: Film, val format: Format, val used: Int = 0, val ended: Boolean = false) {
    init {
        require(used in 0..format.exposures) { "used must be 0..${format.exposures}, was $used" }
    }

    val total: Int get() = format.exposures
    val left: Int get() = total - used
    val isFinished: Boolean get() = ended || used >= total

    /** Uses up one Exposure. A Finished Roll cannot be shot. */
    fun expose(): Roll {
        check(!isFinished) { "Roll is finished" }
        return copy(used = used + 1)
    }

    /** Ends the Roll early. A Finished Roll cannot be rewound. */
    fun rewind(): Roll {
        check(!isFinished) { "Roll is finished" }
        return copy(ended = true)
    }

    companion object {
        fun new(film: Film): Roll = Roll(film, Format.MM35)
    }
}

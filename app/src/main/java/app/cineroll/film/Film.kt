package app.cineroll.film

/**
 * A named look: colour, tone and grain. A Film is data; the GPU shader reads these as uniforms.
 *
 * Colour values are applied in this order: gains, saturation, contrast (around mid grey), lift, grain.
 */
data class Film(
    val name: String,
    val redGain: Float,
    val greenGain: Float,
    val blueGain: Float,
    val saturation: Float,
    val contrast: Float,
    val lift: Float,
    val grain: Float,
) {
    init {
        require(name.isNotBlank()) { "Film needs a name" }
        require(redGain in 0.5f..1.5f) { "redGain out of range: $redGain" }
        require(greenGain in 0.5f..1.5f) { "greenGain out of range: $greenGain" }
        require(blueGain in 0.5f..1.5f) { "blueGain out of range: $blueGain" }
        require(saturation in 0f..2f) { "saturation out of range: $saturation" }
        require(contrast in 0.5f..2f) { "contrast out of range: $contrast" }
        require(lift in 0f..0.2f) { "lift out of range: $lift" }
        require(grain in 0f..0.3f) { "grain out of range: $grain" }
    }
}

object Films {
    /** Warm, slightly punchy, faded blacks, light grain. */
    val Everyday400 = Film(
        name = "Everyday 400",
        redGain = 1.04f,
        greenGain = 1.0f,
        blueGain = 0.94f,
        saturation = 1.1f,
        contrast = 1.15f,
        lift = 0.03f,
        grain = 0.06f,
    )

    val all: List<Film> = listOf(Everyday400)

    fun byName(name: String): Film? = all.firstOrNull { it.name == name }
}

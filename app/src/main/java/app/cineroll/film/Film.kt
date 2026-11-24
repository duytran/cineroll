package app.cineroll.film

/**
 * A named look: colour, tone and grain. A Film is data; the GPU shader reads these as uniforms.
 *
 * Colour values are applied in this order: gains, saturation, contrast (around mid grey), lift, grain.
 * [description] is one line shown in the Film picker.
 */
data class Film(
    val name: String,
    val description: String,
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
        require(description.isNotBlank()) { "Film needs a description" }
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
        description = "Warm and easy, with soft blacks.",
        redGain = 1.04f,
        greenGain = 1.0f,
        blueGain = 0.94f,
        saturation = 1.1f,
        contrast = 1.15f,
        lift = 0.03f,
        grain = 0.06f,
    )

    /** Cool, clean and a little punchy, with almost no lift and fine grain. */
    val Daylight100 = Film(
        name = "Daylight 100",
        description = "Cool and clean, with fine grain.",
        redGain = 0.97f,
        greenGain = 1.0f,
        blueGain = 1.05f,
        saturation = 1.15f,
        contrast = 1.2f,
        lift = 0.01f,
        grain = 0.03f,
    )

    /** Muted colour, low contrast, lifted blacks, heavy grain. */
    val Faded800 = Film(
        name = "Faded 800",
        description = "Muted colour, lifted blacks, heavy grain.",
        redGain = 1.02f,
        greenGain = 1.0f,
        blueGain = 0.98f,
        saturation = 0.75f,
        contrast = 0.9f,
        lift = 0.1f,
        grain = 0.14f,
    )

    /** Black and white (saturation 0, equal gains), high contrast, medium grain. */
    val Mono400 = Film(
        name = "Mono 400",
        description = "Black and white, high contrast.",
        redGain = 1.0f,
        greenGain = 1.0f,
        blueGain = 1.0f,
        saturation = 0f,
        contrast = 1.3f,
        lift = 0.02f,
        grain = 0.08f,
    )

    val all: List<Film> = listOf(Everyday400, Daylight100, Faded800, Mono400)

    fun byName(name: String): Film? = all.firstOrNull { it.name == name }
}

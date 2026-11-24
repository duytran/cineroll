package app.cineroll.film

/**
 * The Film look on one pixel, on the CPU, without grain. It is the reference the GPU shader
 * in `FilmRenderer` must match; keep the two in step.
 */
object FilmMath {
    fun develop(rgb: FloatArray, film: Film): FloatArray {
        require(rgb.size == 3) { "expected r, g, b" }
        val c = floatArrayOf(rgb[0] * film.redGain, rgb[1] * film.greenGain, rgb[2] * film.blueGain)
        val luma = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
        for (i in 0..2) {
            var v = luma + (c[i] - luma) * film.saturation
            v = (v - 0.5f) * film.contrast + 0.5f
            v = film.lift + v * (1f - film.lift)
            c[i] = v.coerceIn(0f, 1f)
        }
        return c
    }
}

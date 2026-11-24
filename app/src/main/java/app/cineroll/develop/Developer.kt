package app.cineroll.develop

import app.cineroll.film.Film

/** A full-resolution JPEG from the phone camera, plus the rotation needed to show it upright. */
class CapturedImage(val jpeg: ByteArray, val rotationDegrees: Int)

class DevelopException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Bakes a Film into a captured image. All GPU work stays behind this interface. */
interface Developer {
    /** @return the developed JPEG. @throws DevelopException on any failure. */
    fun develop(image: CapturedImage, film: Film): ByteArray
}

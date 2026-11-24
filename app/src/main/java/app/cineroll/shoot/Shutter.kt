package app.cineroll.shoot

import app.cineroll.develop.CapturedImage
import app.cineroll.develop.DevelopException
import app.cineroll.develop.Developer
import app.cineroll.film.Film
import app.cineroll.roll.Roll
import app.cineroll.roll.RollStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Takes a full-resolution image from the phone camera. */
interface Capturer {
    /** @throws CaptureException if the image could not be taken. */
    suspend fun capture(): CapturedImage
}

/** Saves a developed JPEG to the phone's gallery. */
interface Gallery {
    /** @throws GalleryException if the JPEG could not be saved. */
    fun save(jpeg: ByteArray)
}

class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GalleryException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class ShootResult { Developed, Busy, NoRoll, RollFinished, CaptureFailed, DevelopFailed, SaveFailed }

/**
 * The shoot flow: capture, develop, save, then charge the Roll. The Roll is charged only after the
 * save succeeds, so a failure never costs an Exposure.
 *
 * [roll] is `null` while no Roll is loaded (first launch, or after a Finished Roll was cleared);
 * the app then shows the Film picker.
 */
class Shutter(
    private val capturer: Capturer,
    private val developer: Developer,
    private val gallery: Gallery,
    private val store: RollStore,
    initialRoll: Roll?,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val _roll = MutableStateFlow(initialRoll)
    val roll: StateFlow<Roll?> = _roll

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val taking = AtomicBoolean(false)

    suspend fun shoot(): ShootResult {
        val current = _roll.value ?: return ShootResult.NoRoll
        if (current.isFinished) return ShootResult.RollFinished
        if (!taking.compareAndSet(false, true)) return ShootResult.Busy
        _busy.value = true
        try {
            val image = try {
                capturer.capture()
            } catch (e: CaptureException) {
                log("Capture failed", e)
                return ShootResult.CaptureFailed
            }

            val developed = try {
                withContext(work) { developer.develop(image, current.film) }
            } catch (e: DevelopException) {
                log("Develop failed", e)
                return ShootResult.DevelopFailed
            }

            try {
                withContext(work) { gallery.save(developed) }
            } catch (e: GalleryException) {
                log("Save failed", e)
                return ShootResult.SaveFailed
            }

            val next = current.expose()
            _roll.value = next
            saveRoll(next)
            return ShootResult.Developed
        } finally {
            taking.set(false)
            _busy.value = false
        }
    }

    /** Loads a new 35mm Roll with [film], but only while no Roll is loaded. */
    fun load(film: Film) {
        if (_roll.value != null) return
        val fresh = Roll.new(film)
        _roll.value = fresh
        saveRoll(fresh)
    }

    /**
     * Ends the loaded Roll early. The unused Exposures are lost.
     * @return `true` if the Roll was rewound; `false` if there is no Roll, it is already finished,
     * or an Exposure is being developed.
     */
    fun rewind(): Boolean {
        val current = _roll.value ?: return false
        if (current.isFinished || taking.get()) return false
        val ended = current.rewind()
        _roll.value = ended
        saveRoll(ended)
        return true
    }

    /** Drops the Finished Roll so the Film picker shows. Does nothing while a Roll is loaded. */
    fun clear() {
        val current = _roll.value ?: return
        if (!current.isFinished) return
        _roll.value = null
        try {
            store.clear()
        } catch (e: IOException) {
            log("Could not clear the Roll", e)
        }
    }

    private fun saveRoll(roll: Roll) {
        try {
            store.save(roll)
        } catch (e: IOException) {
            log("Could not save the Roll", e)
        }
    }
}

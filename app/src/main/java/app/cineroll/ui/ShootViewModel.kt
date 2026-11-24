package app.cineroll.ui

import android.app.Application
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cineroll.develop.GpuDeveloper
import app.cineroll.film.Film
import app.cineroll.platform.CameraXCapturer
import app.cineroll.platform.MediaStoreGallery
import app.cineroll.roll.PrefsRollStore
import app.cineroll.shoot.ShootResult
import app.cineroll.shoot.Shutter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShootViewModel(app: Application) : AndroidViewModel(app) {

    private val tag = "Cineroll"

    val capturer = CameraXCapturer(ContextCompat.getMainExecutor(app))

    private val store = PrefsRollStore(app)

    /** Starts with the saved Roll, or with none: the Film picker then asks for a Film. */
    val shutter = Shutter(
        capturer = capturer,
        developer = GpuDeveloper(),
        gallery = MediaStoreGallery(app),
        store = store,
        initialRoll = store.load(),
        log = { message, error -> Log.w(tag, message, error) },
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun shoot() {
        viewModelScope.launch {
            _message.value = when (shutter.shoot()) {
                ShootResult.CaptureFailed, ShootResult.DevelopFailed -> "Couldn't take the photo, try again"
                ShootResult.SaveFailed -> "Couldn't save the photo, check storage"
                ShootResult.Developed, ShootResult.Busy, ShootResult.NoRoll, ShootResult.RollFinished -> null
            }
        }
    }

    fun load(film: Film) = shutter.load(film)

    fun rewind() {
        shutter.rewind()
    }

    fun clear() = shutter.clear()

    fun messageShown() {
        _message.value = null
    }
}

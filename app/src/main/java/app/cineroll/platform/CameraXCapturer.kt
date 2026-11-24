package app.cineroll.platform

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import app.cineroll.develop.CapturedImage
import app.cineroll.shoot.CaptureException
import app.cineroll.shoot.Capturer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Takes a full-resolution JPEG through a CameraX [ImageCapture] that the viewfinder binds. */
class CameraXCapturer(private val executor: Executor) : Capturer {

    @Volatile
    private var imageCapture: ImageCapture? = null

    fun bind(imageCapture: ImageCapture?) {
        this.imageCapture = imageCapture
    }

    override suspend fun capture(): CapturedImage {
        val capture = imageCapture ?: throw CaptureException("Camera is not ready")
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        continuation.resume(CapturedImage(bytes, image.imageInfo.rotationDegrees))
                    } catch (e: Exception) {
                        continuation.resumeWithException(CaptureException("Could not read the captured image", e))
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(CaptureException("Capture failed", exception))
                }
            })
        }
    }
}

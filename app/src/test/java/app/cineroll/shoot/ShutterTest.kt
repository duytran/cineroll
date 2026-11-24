package app.cineroll.shoot

import app.cineroll.develop.CapturedImage
import app.cineroll.develop.DevelopException
import app.cineroll.develop.Developer
import app.cineroll.film.Film
import app.cineroll.film.Films
import app.cineroll.roll.Format
import app.cineroll.roll.Roll
import app.cineroll.roll.RollStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCapturer : Capturer {
    var failure: CaptureException? = null
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    override suspend fun capture(): CapturedImage {
        calls++
        gate?.await()
        failure?.let { throw it }
        return CapturedImage(byteArrayOf(1, 2, 3), 0)
    }
}

private class FakeDeveloper : Developer {
    var failure: DevelopException? = null
    override fun develop(image: CapturedImage, film: Film): ByteArray {
        failure?.let { throw it }
        return byteArrayOf(9, 9)
    }
}

private class FakeGallery : Gallery {
    var failure: GalleryException? = null
    val saved = mutableListOf<ByteArray>()
    override fun save(jpeg: ByteArray) {
        failure?.let { throw it }
        saved += jpeg
    }
}

private class FakeStore : RollStore {
    var failure: IOException? = null
    var cleared = 0
    val saved = mutableListOf<Roll>()
    override fun load(): Roll? = saved.lastOrNull()
    override fun save(roll: Roll) {
        failure?.let { throw it }
        saved += roll
    }
    override fun clear() {
        failure?.let { throw it }
        saved.clear()
        cleared++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShutterTest {

    private val capturer = FakeCapturer()
    private val developer = FakeDeveloper()
    private val gallery = FakeGallery()
    private val store = FakeStore()

    private fun TestScope.shutter(roll: Roll? = Roll.new(Films.Everyday400)) =
        Shutter(capturer, developer, gallery, store, roll, work = UnconfinedTestDispatcher(testScheduler))

    private val Shutter.current: Roll get() = roll.value!!

    @Test
    fun successSavesTheDevelopedExposureAndChargesTheRollOnce() = runTest {
        val shutter = shutter()

        assertEquals(ShootResult.Developed, shutter.shoot())

        assertEquals(1, gallery.saved.size)
        assertArrayEquals(byteArrayOf(9, 9), gallery.saved.single())
        assertEquals(1, shutter.current.used)
        assertEquals(1, store.saved.single().used)
        assertFalse(shutter.busy.value)
    }

    @Test
    fun captureFailureChargesNothing() = runTest {
        capturer.failure = CaptureException("boom")
        val shutter = shutter()

        assertEquals(ShootResult.CaptureFailed, shutter.shoot())

        assertEquals(0, shutter.current.used)
        assertTrue(gallery.saved.isEmpty())
        assertTrue(store.saved.isEmpty())
        assertFalse(shutter.busy.value)
    }

    @Test
    fun developFailureChargesNothingAndSavesNothing() = runTest {
        developer.failure = DevelopException("boom")
        val shutter = shutter()

        assertEquals(ShootResult.DevelopFailed, shutter.shoot())

        assertEquals(0, shutter.current.used)
        assertTrue(gallery.saved.isEmpty())
        assertTrue(store.saved.isEmpty())
        assertFalse(shutter.busy.value)
    }

    @Test
    fun saveFailureChargesNothing() = runTest {
        gallery.failure = GalleryException("storage full")
        val shutter = shutter()

        assertEquals(ShootResult.SaveFailed, shutter.shoot())

        assertEquals(0, shutter.current.used)
        assertTrue(store.saved.isEmpty())
        assertFalse(shutter.busy.value)
    }

    @Test
    fun failedRollStoreWriteAfterAGoodSaveDoesNotBlockTheNextShot() = runTest {
        store.failure = IOException("disk")
        val logged = mutableListOf<String>()
        val shutter = Shutter(
            capturer, developer, gallery, store, Roll.new(Films.Everyday400),
            work = UnconfinedTestDispatcher(testScheduler), log = { message, _ -> logged += message },
        )

        assertEquals(ShootResult.Developed, shutter.shoot())
        assertEquals(ShootResult.Developed, shutter.shoot())

        assertEquals(2, shutter.current.used)
        assertEquals(2, gallery.saved.size)
        assertEquals(2, logged.size)
    }

    @Test
    fun aSecondTapWhileDevelopingTakesNoExposure() = runTest {
        val gate = CompletableDeferred<Unit>()
        capturer.gate = gate
        val shutter = shutter()

        val first = async { shutter.shoot() }
        runCurrent()
        assertTrue(shutter.busy.value)

        assertEquals(ShootResult.Busy, shutter.shoot())
        assertEquals(1, capturer.calls)

        gate.complete(Unit)
        assertEquals(ShootResult.Developed, first.await())
        assertEquals(1, shutter.current.used)
    }

    @Test
    fun the36thExposureFinishesTheRollAndNothingMoreCanBeShot() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 35))

        assertEquals(ShootResult.Developed, shutter.shoot())
        assertTrue(shutter.current.isFinished)

        val callsBefore = capturer.calls
        assertEquals(ShootResult.RollFinished, shutter.shoot())
        assertEquals(callsBefore, capturer.calls)
    }

    @Test
    fun shootingWithNoRollDoesNothing() = runTest {
        val shutter = shutter(null)

        assertEquals(ShootResult.NoRoll, shutter.shoot())

        assertEquals(0, capturer.calls)
        assertTrue(gallery.saved.isEmpty())
        assertTrue(store.saved.isEmpty())
        assertFalse(shutter.busy.value)
    }

    @Test
    fun loadMakesANewRollWithThatFilmAndSavesIt() = runTest {
        val shutter = shutter(null)

        shutter.load(Films.Mono400)

        assertEquals(Roll.new(Films.Mono400), shutter.roll.value)
        assertEquals(Roll.new(Films.Mono400), store.saved.single())
    }

    @Test
    fun loadDoesNothingWhileARollExists() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 5))

        shutter.load(Films.Mono400)

        assertEquals(Films.Everyday400, shutter.current.film)
        assertEquals(5, shutter.current.used)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun loadStillPublishesTheRollWhenTheStoreFails() = runTest {
        store.failure = IOException("disk")
        val logged = mutableListOf<String>()
        val shutter = Shutter(
            capturer, developer, gallery, store, null,
            work = UnconfinedTestDispatcher(testScheduler), log = { message, _ -> logged += message },
        )

        shutter.load(Films.Faded800)

        assertEquals(Roll.new(Films.Faded800), shutter.roll.value)
        assertEquals(1, logged.size)
    }

    @Test
    fun rewindFinishesTheRollKeepsTheCountAndSavesIt() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 13))

        assertTrue(shutter.rewind())

        assertTrue(shutter.current.isFinished)
        assertEquals(13, shutter.current.used)
        assertTrue(store.saved.single().ended)
        assertTrue(gallery.saved.isEmpty())
    }

    @Test
    fun afterARewindNoMoreExposuresCanBeShot() = runTest {
        val shutter = shutter()
        shutter.rewind()

        assertEquals(ShootResult.RollFinished, shutter.shoot())

        assertEquals(0, capturer.calls)
    }

    @Test
    fun rewindIsRefusedWhileDeveloping() = runTest {
        val gate = CompletableDeferred<Unit>()
        capturer.gate = gate
        val shutter = shutter()

        val first = async { shutter.shoot() }
        runCurrent()
        assertTrue(shutter.busy.value)

        assertFalse(shutter.rewind())
        assertFalse(shutter.current.isFinished)

        gate.complete(Unit)
        assertEquals(ShootResult.Developed, first.await())
        assertEquals(1, shutter.current.used)
    }

    @Test
    fun rewindIsRefusedWithNoRollAndOnAFinishedRoll() = runTest {
        assertFalse(shutter(null).rewind())
        assertFalse(shutter(Roll(Films.Everyday400, Format.MM35, used = 36)).rewind())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun rewindStillFinishesTheRollWhenTheStoreFails() = runTest {
        store.failure = IOException("disk")
        val logged = mutableListOf<String>()
        val shutter = Shutter(
            capturer, developer, gallery, store, Roll.new(Films.Everyday400),
            work = UnconfinedTestDispatcher(testScheduler), log = { message, _ -> logged += message },
        )

        assertTrue(shutter.rewind())

        assertTrue(shutter.current.isFinished)
        assertEquals(1, logged.size)
    }

    @Test
    fun clearDropsAFinishedRollAndClearsTheStore() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 36))

        shutter.clear()

        assertNull(shutter.roll.value)
        assertEquals(1, store.cleared)
    }

    @Test
    fun clearDoesNothingWhileARollIsLoaded() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 5))

        shutter.clear()

        assertEquals(5, shutter.current.used)
        assertEquals(0, store.cleared)
    }

    @Test
    fun clearStillShowsThePickerWhenTheStoreFails() = runTest {
        store.failure = IOException("disk")
        val logged = mutableListOf<String>()
        val shutter = Shutter(
            capturer, developer, gallery, store, Roll(Films.Everyday400, Format.MM35, used = 36),
            work = UnconfinedTestDispatcher(testScheduler), log = { message, _ -> logged += message },
        )

        shutter.clear()

        assertNull(shutter.roll.value)
        assertEquals(1, logged.size)
    }
}

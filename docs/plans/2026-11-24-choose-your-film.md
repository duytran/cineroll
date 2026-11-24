# Choose Your Film Implementation Plan

> **For agentic workers:** Implement this plan task-by-task, in order — one fresh subagent per task (recommended) or inline in one session. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick one of four built-in Films when loading a Roll, and Rewind a loaded Roll to end it early.

**Architecture:** The `Film` stays data read by the existing shader, so three new Films are three new `Film` values and no shader change. `Roll` gains an `ended` flag and `rewind()`. `Shutter.roll` becomes `StateFlow<Roll?>`, where `null` means no Roll is loaded; `CineRollApp` picks the screen from that state (no navigation library). Spec: [`docs/specs/2026-11-24-choose-your-film-design.md`](../specs/2026-11-24-choose-your-film-design.md). Builds on [`2026-11-24-shoot-one-roll.md`](2026-11-24-shoot-one-roll.md).

**Tech Stack:** Same as the first plan: Kotlin 2.4.21, Android Gradle Plugin 9.5.0, Gradle 9.6.0, JDK 17, compileSdk and targetSdk 37, CameraX 1.7.0, Jetpack Compose (BOM 2026.09.00), kotlinx-coroutines 1.11.0, JUnit 4. No new dependencies.

## Prerequisites (not a task)

Slice 1 is merged to `main` and builds. Tasks 2, 3 and 4 run instrumented tests and need an emulator (with a virtual back camera) or a Pixel connected, as in the first plan.

## Global Constraints

- Native Android, Kotlin, CameraX (ADR 0005). The viewfinder stays plain; the Film is applied only after capture, on the GPU (ADR 0006). **No shader change** and no new `Film` parameters in this slice.
- The screen follows the Roll state, with no navigation library ([ADR 0008](../adr/0008-screen-follows-roll-state.md)): no Roll → Film picker; Finished Roll → Roll finished screen; loaded Roll → viewfinder. The camera permission screen still comes before all three.
- Four built-in Films, in this order: **Everyday 400**, **Daylight 100**, **Faded 800**, **Mono 400**. Everyday 400's values do not change. Names are invented, not real film brands.
- Every new Roll is 35mm (36 Exposures). A Roll keeps its Film until it is finished; to change Film you Rewind and load a new Roll.
- The picker has no cancel: with no Roll loaded there is nothing to go back to.
- Rewind opens a confirm dialog "Rewind?" with "The 23 Exposures left will be lost." and the buttons Rewind and Cancel. The Rewind button is disabled while an Exposure is developing. After Rewind the Finished Roll screen shows, as when the 36th Exposure is used.
- Saved data without an `ended` value loads as `ended = false`, so a Roll saved by slice 1 still loads.
- Store failures follow slice 1's rule: log and carry on with the in-memory state (spec, Error handling).
- Refinements the spec leaves open, decided here: `Shutter.load(film)` only works while no Roll is loaded; `Shutter.clear()` only works while the Roll is finished; `Shutter.rewind()` returns `true` when it rewound and `false` when it refused; `shoot()` with no Roll returns a new `ShootResult.NoRoll`.
- Out of scope: new shader parameters, Film previews or sample images, user-made Films, several Cameras, Contact Sheet, live Film preview, any Format other than 35mm.
- Use [`GLOSSARY.md`](../../GLOSSARY.md) terms in code and docs (Film, Roll, Exposure, Finished Roll, Rewind). Avoid the words it lists under _Avoid_ in code names (no "filter", "preset", "session").
- Every task adds one entry under `## [Unreleased]` in `CHANGELOG.md` (date = the day the task is done, `YYYY-MM-DD`; the examples below use 2026-11-24) and commits it with the code.
- **Never** add a `Co-Authored-By:` line or any attribution trailer to a commit message.
- Per [ADR 0003](../adr/0003-issue-and-pr-per-task.md): before writing code, ask the user once, then open one GitHub issue per task (`Task N: <name>`), build each task on its own branch `task/<issue>-<slug>` (stacked on the previous task's branch), and merge it through its own PR with `Closes #<issue>`. Write the issue number next to each task heading below, e.g. `### Task 1: … (#13)`.
- Run commands from the repository root. Run one instrumented test class with `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>` (Android Gradle Plugin 9.5.0 has no `--tests` for instrumented tests; unit tests do).

## File Structure

```
app/src/main/java/app/cineroll/
  film/Film.kt            Film gains `description`; Films gains three Films          (Task 1)
  roll/Roll.kt            `ended` flag and `rewind()`                                (Task 2)
  roll/RollStore.kt       saves `ended`; new `clear()`                               (Task 2)
  shoot/Shutter.kt        nullable Roll; `load`, `clear`, `rewind`; ShootResult.NoRoll (Task 3)
  ui/ShootViewModel.kt    no default Roll; `load`, `rewind`, `clear`                 (Task 3)
  ui/Screens.kt           Film picker (Task 3); Rewind button, dialog, Film name (Task 4)
app/src/test/java/app/cineroll/
  film/FilmTest.kt, film/FilmMathTest.kt, roll/RollTest.kt, shoot/ShutterTest.kt
  ui/RewindWarningTest.kt                                                           (Task 4, new)
app/src/androidTest/java/app/cineroll/
  develop/GpuDeveloperTest.kt, roll/PrefsRollStoreTest.kt, ui/ShootSmokeTest.kt
```

---

### Task 1: Four built-in Films

**Files:**
- Modify: `app/src/main/java/app/cineroll/film/Film.kt`
- Modify: `app/src/test/java/app/cineroll/film/FilmTest.kt`
- Modify: `app/src/test/java/app/cineroll/film/FilmMathTest.kt:9`
- Modify: `app/src/androidTest/java/app/cineroll/develop/GpuDeveloperTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: nothing new.
- Produces: `Film.description: String` (second constructor parameter, after `name`, must not be blank); `Films.Everyday400`, `Films.Daylight100`, `Films.Faded800`, `Films.Mono400` (all `Film`); `Films.all: List<Film>` in that order; `Films.byName(name: String): Film?` finds each of the four.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `app/src/test/java/app/cineroll/film/FilmTest.kt`:

```kotlin
package app.cineroll.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmTest {

    @Test
    fun fourBuiltInFilmsInOrder() {
        assertEquals(
            listOf("Everyday 400", "Daylight 100", "Faded 800", "Mono 400"),
            Films.all.map { it.name },
        )
        assertSame(Films.Everyday400, Films.all[0])
        assertSame(Films.Daylight100, Films.all[1])
        assertSame(Films.Faded800, Films.all[2])
        assertSame(Films.Mono400, Films.all[3])
    }

    @Test
    fun everyBuiltInFilmHasADescription() {
        Films.all.forEach { assertTrue("${it.name} needs a description", it.description.isNotBlank()) }
    }

    @Test
    fun everyday400KeepsItsSlice1Values() {
        val film = Films.Everyday400
        assertEquals(1.04f, film.redGain, 0f)
        assertEquals(1.0f, film.greenGain, 0f)
        assertEquals(0.94f, film.blueGain, 0f)
        assertEquals(1.1f, film.saturation, 0f)
        assertEquals(1.15f, film.contrast, 0f)
        assertEquals(0.03f, film.lift, 0f)
        assertEquals(0.06f, film.grain, 0f)
    }

    @Test
    fun monoHasNoColour() {
        val mono = Films.Mono400
        assertEquals(0f, mono.saturation, 0f)
        assertEquals(mono.redGain, mono.greenGain, 0f)
        assertEquals(mono.greenGain, mono.blueGain, 0f)
    }

    @Test
    fun findsEveryBuiltInFilmByName() {
        Films.all.forEach { assertSame(it, Films.byName(it.name)) }
        assertNull(Films.byName("No such film"))
    }

    @Test
    fun rejectsParametersOutOfRange() {
        val ok = Films.Everyday400
        assertThrows(IllegalArgumentException::class.java) { ok.copy(redGain = 1.6f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(saturation = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(contrast = 2.1f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(lift = 0.3f) }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(grain = 0.5f) }
    }

    @Test
    fun rejectsABlankNameOrDescription() {
        val ok = Films.Everyday400
        assertThrows(IllegalArgumentException::class.java) { ok.copy(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { ok.copy(description = " ") }
    }
}
```

In `app/src/test/java/app/cineroll/film/FilmMathTest.kt`, replace line 9:

```kotlin
    private val neutral = Film("neutral", "Changes nothing.", 1f, 1f, 1f, 1f, 1f, 0f, 0f)
```

In `app/src/androidTest/java/app/cineroll/develop/GpuDeveloperTest.kt`, add this test directly after `matchesTheFilmMathAtSamplePoints` (all imports it needs are already in the file):

```kotlin
    @Test
    fun monoFilmMatchesTheFilmMathAndHasNoColour() {
        val mono = Films.Mono400.copy(grain = 0f)
        val input = fourColourJpeg()
        val inputBitmap = decode(input)
        val output = decode(developer.develop(CapturedImage(input, 0), mono))

        for ((x, y) in listOf(16 to 16, 48 to 16, 16 to 48, 48 to 48)) {
            val src = inputBitmap.getPixel(x, y)
            val expected = FilmMath.develop(
                floatArrayOf(Color.red(src) / 255f, Color.green(src) / 255f, Color.blue(src) / 255f),
                mono,
            )
            val got = output.getPixel(x, y)
            assertNear(expected[0] * 255f, Color.red(got), "red at $x,$y")
            assertNear(expected[1] * 255f, Color.green(got), "green at $x,$y")
            assertNear(expected[2] * 255f, Color.blue(got), "blue at $x,$y")
            // R = G = B, give or take 2 for the JPEG round trip.
            assertTrue("grey at $x,$y", abs(Color.red(got) - Color.green(got)) <= 2)
            assertTrue("grey at $x,$y", abs(Color.green(got) - Color.blue(got)) <= 2)
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.cineroll.film.*'`
Expected: FAIL to compile: `Unresolved reference 'Daylight100'`, `'description'`.

- [ ] **Step 3: Write the implementation**

Replace the whole of `app/src/main/java/app/cineroll/film/Film.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all unit tests pass (the first slice's tests still pass; `FilmMathTest` uses the new `description` argument).

Run (emulator or device connected): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.develop.GpuDeveloperTest`
Expected: BUILD SUCCESSFUL, 5 tests pass. If the Mono test fails on the grey check by 3 or more, record the largest difference in this plan's `Revisions` and raise the allowed difference only as far as that value needs.

- [ ] **Step 5: Log it and commit**

Add under `### Added` in `CHANGELOG.md`, newest first:

```markdown
- 2026-11-24: Three more built-in Films (Daylight 100, Faded 800, Mono 400) using only the existing Film parameters, and a one-line `description` on every Film for the Film picker. The Developer test now also checks Mono 400 against the CPU reference and that its output is grey. No shader change. See [plan](docs/plans/2026-11-24-choose-your-film.md), [spec](docs/specs/2026-11-24-choose-your-film-design.md), [ADR 0006](docs/adr/0006-develop-on-the-gpu-after-capture.md).
```

```bash
git add app/src CHANGELOG.md
git commit -m "feat: add Daylight 100, Faded 800 and Mono 400 Films"
```

---

### Task 2: Rewind on the Roll, and the store keeps it

**Files:**
- Modify: `app/src/main/java/app/cineroll/roll/Roll.kt`
- Modify: `app/src/main/java/app/cineroll/roll/RollStore.kt`
- Modify: `app/src/test/java/app/cineroll/roll/RollTest.kt`
- Modify: `app/src/test/java/app/cineroll/shoot/ShutterTest.kt` (its `FakeStore`)
- Modify: `app/src/androidTest/java/app/cineroll/roll/PrefsRollStoreTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `Film`, `Format` from Task 1 and slice 1.
- Produces: `Roll(film: Film, format: Format, used: Int = 0, ended: Boolean = false)`; `Roll.rewind(): Roll` (throws `IllegalStateException` if already finished); `Roll.isFinished` is `ended || used >= total`; `RollStore.clear()` (throws `IOException` if it could not remove the Roll); `PrefsRollStore` saves and loads `ended`.

- [ ] **Step 1: Write the failing tests**

Add these tests to `app/src/test/java/app/cineroll/roll/RollTest.kt`, before the final closing brace of the class:

```kotlin
    @Test
    fun aNewRollIsNotEnded() {
        assertFalse(Roll.new(Films.Everyday400).ended)
    }

    @Test
    fun rewindFinishesTheRollAndKeepsTheCount() {
        val rewound = Roll.new(Films.Everyday400).expose().expose().rewind()

        assertTrue(rewound.ended)
        assertTrue(rewound.isFinished)
        assertEquals(2, rewound.used)
    }

    @Test
    fun rewindOnAFinishedRollThrows() {
        val used = Roll(Films.Everyday400, Format.MM35, used = 36)
        val ended = Roll.new(Films.Everyday400).rewind()
        assertThrows(IllegalStateException::class.java) { used.rewind() }
        assertThrows(IllegalStateException::class.java) { ended.rewind() }
    }

    @Test
    fun exposeAfterRewindThrows() {
        val rewound = Roll.new(Films.Everyday400).expose().rewind()
        assertThrows(IllegalStateException::class.java) { rewound.expose() }
    }
```

In `app/src/test/java/app/cineroll/shoot/ShutterTest.kt`, replace the `FakeStore` class with this version (it must implement the new `clear()` so the file compiles):

```kotlin
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
```

Add these tests to `app/src/androidTest/java/app/cineroll/roll/PrefsRollStoreTest.kt`, before the final closing brace, and add `import org.junit.Assert.assertFalse` to its imports:

```kotlin
    @Test
    fun aRewoundRollStaysEnded() {
        val rewound = Roll.new(Films.Everyday400).expose().rewind()
        PrefsRollStore(context).save(rewound)

        val loaded = PrefsRollStore(context).load()!!
        assertEquals(rewound, loaded)
        assertTrue(loaded.ended)
        assertEquals(1, loaded.used)
    }

    @Test
    fun aRollSavedBySlice1WithoutEndedLoadsAsNotEnded() {
        prefs.edit().putString("film", "Everyday 400").putString("format", "MM35").putInt("used", 4).commit()

        val loaded = PrefsRollStore(context).load()!!
        assertFalse(loaded.ended)
        assertEquals(4, loaded.used)
    }

    @Test
    fun clearRemovesTheSavedRoll() {
        val store = PrefsRollStore(context)
        store.save(Roll.new(Films.Everyday400))

        store.clear()

        assertNull(PrefsRollStore(context).load())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.cineroll.roll.RollTest'`
Expected: FAIL to compile: `Unresolved reference 'ended'`, `'rewind'`, and `ShutterTest.kt` reports that `FakeStore` overrides nothing (`clear`).

- [ ] **Step 3: Write the implementation**

In `app/src/main/java/app/cineroll/roll/Roll.kt`, replace the `Roll` class (keep `Format` and the `companion object` as they are) with:

```kotlin
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
```

Replace the whole of `app/src/main/java/app/cineroll/roll/RollStore.kt`:

```kotlin
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

    /** Removes the saved Roll, so [load] returns `null`. @throws IOException if it could not be removed. */
    fun clear()
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
        // A Roll saved before Rewind existed has no "ended" value: it was never rewound.
        val ended = prefs.getBoolean("ended", false)
        return Roll(film, format, used, ended)
    }

    override fun save(roll: Roll) {
        val written = prefs.edit()
            .putString("film", roll.film.name)
            .putString("format", roll.format.name)
            .putInt("used", roll.used)
            .putBoolean("ended", roll.ended)
            .commit()
        if (!written) throw IOException("Could not save the Roll")
    }

    override fun clear() {
        if (!prefs.edit().clear().commit()) throw IOException("Could not clear the Roll")
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all unit tests pass.

Run (emulator or device connected): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.roll.PrefsRollStoreTest`
Expected: BUILD SUCCESSFUL, 7 tests pass.

- [ ] **Step 5: Log it and commit**

Add under `### Added` in `CHANGELOG.md`, newest first:

```markdown
- 2026-11-24: Rewind in the domain: `Roll.rewind()` ends a loaded Roll early and keeps its count, `RollStore` saves that (`ended`) and has a `clear()`. A Roll saved by the first slice, which has no `ended` value, still loads as not ended. Not on screen yet. See [plan](docs/plans/2026-11-24-choose-your-film.md), [spec](docs/specs/2026-11-24-choose-your-film-design.md).
```

```bash
git add app/src CHANGELOG.md
git commit -m "feat: add Rewind to Roll and RollStore"
```

---

### Task 3: `Shutter` without a default Roll, and the Film picker

**Files:**
- Modify: `app/src/main/java/app/cineroll/shoot/Shutter.kt`
- Modify: `app/src/main/java/app/cineroll/ui/ShootViewModel.kt`
- Modify: `app/src/main/java/app/cineroll/ui/Screens.kt`
- Modify: `app/src/test/java/app/cineroll/shoot/ShutterTest.kt`
- Modify: `app/src/androidTest/java/app/cineroll/ui/ShootSmokeTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `Roll.rewind()`, `Roll.new(film)`, `RollStore.clear()` from Task 2; `Films.all`, `Film.description` from Task 1.
- Produces: `Shutter(capturer, developer, gallery, store, initialRoll: Roll?, work, log)`; `Shutter.roll: StateFlow<Roll?>`; `Shutter.load(film: Film)`; `Shutter.clear()`; `Shutter.rewind(): Boolean`; `ShootResult.NoRoll`; `ShootViewModel.load(film: Film)`, `.rewind()`, `.clear()`; a `FilmPickerScreen` whose Film buttons carry the test tag `film-<Film name>` (for example `film-Mono 400`).

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/app/cineroll/shoot/ShutterTest.kt`:

1. Add `import org.junit.Assert.assertNull` to the imports.
2. Replace the `shutter(...)` helper so a Roll can be absent, and add a `current` helper below it:

```kotlin
    private fun TestScope.shutter(roll: Roll? = Roll.new(Films.Everyday400)) =
        Shutter(capturer, developer, gallery, store, roll, work = UnconfinedTestDispatcher(testScheduler))

    private val Shutter.current: Roll get() = roll.value!!
```

3. Delete the two tests `loadNewRollReplacesAFinishedRollAndSavesIt` and `loadNewRollDoesNothingWhileARollIsLoaded`.
4. Replace every `shutter.roll.value.` with `shutter.current.` by running:

```bash
sed -i 's/shutter\.roll\.value\./shutter.current./g' app/src/test/java/app/cineroll/shoot/ShutterTest.kt
```

5. Add these tests before the final closing brace of the class:

```kotlin
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
```

In `app/src/androidTest/java/app/cineroll/ui/ShootSmokeTest.kt`: add `import androidx.compose.ui.test.onAllNodesWithTag` to the imports, and insert these lines at the start of the test body, before the first `compose.waitUntil`:

```kotlin
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("film-Everyday 400").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("film-Everyday 400").performClick()

```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.cineroll.shoot.ShutterTest'`
Expected: FAIL to compile: `Unresolved reference 'load'`, `'clear'`, `'rewind'`, `'NoRoll'`, and a type mismatch passing `null` as `initialRoll`.

- [ ] **Step 3: Write the implementation**

Replace the whole of `app/src/main/java/app/cineroll/shoot/Shutter.kt`:

```kotlin
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
```

Replace the whole of `app/src/main/java/app/cineroll/ui/ShootViewModel.kt`:

```kotlin
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
```

In `app/src/main/java/app/cineroll/ui/Screens.kt`:

1. Add these imports (keep them in alphabetical position with the others): `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.material3.MaterialTheme`, `app.cineroll.film.Film`, `app.cineroll.film.Films`.
2. Replace the `roll` line and the `when` in `CineRollApp`, and add the picker. The part of `CineRollApp` from `val roll by ...` to its closing brace becomes:

```kotlin
    val roll by viewModel.shutter.roll.collectAsState()
    val current = roll
    when {
        !granted -> PermissionScreen(
            onAllow = { askForCamera.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        current == null -> FilmPickerScreen(onPick = viewModel::load)
        current.isFinished -> FinishedRollScreen(onLoadNewRoll = viewModel::clear)
        else -> ViewfinderScreen(viewModel)
    }
}
```

3. Add the new screen below `PermissionScreen`:

```kotlin
@Composable
private fun FilmPickerScreen(onPick: (Film) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Load a Film", style = MaterialTheme.typography.headlineSmall)
        Films.all.forEach { film ->
            Button(
                onClick = { onPick(film) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("film-${film.name}"),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(film.name)
                    Text(film.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
```

4. In `ViewfinderScreen`, replace the line `val roll by viewModel.shutter.roll.collectAsState()` with the line below, so the screen draws nothing for the instant a Roll is cleared:

```kotlin
    val roll = viewModel.shutter.roll.collectAsState().value ?: return
```

(`return` is allowed in a Composable function that returns `Unit`. Leave the rest of `ViewfinderScreen` as it is; it uses `roll.left` and `roll.total`.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all unit tests pass.

Run (emulator or device connected): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.ui.ShootSmokeTest`
Expected: BUILD SUCCESSFUL. The test now picks Everyday 400 from the picker, then shoots one Exposure (`36/36` to `35/36`). It leaves one developed Exposure in `DCIM/Cineroll` on the test device.

- [ ] **Step 5: Log it and commit**

Add under `### Added` in `CHANGELOG.md`, newest first:

```markdown
- 2026-11-24: Film picker. The app no longer loads Everyday 400 on its own: with no Roll loaded (first launch, or after "Load new Roll") it shows the four Films with a line each, and tapping one loads a new 35mm Roll. `Shutter.roll` is now `Roll?` (`null` = no Roll); new `load(film)`, `clear()` and `rewind()` (not on screen yet). The smoke test picks a Film first. See [plan](docs/plans/2026-11-24-choose-your-film.md), [spec](docs/specs/2026-11-24-choose-your-film-design.md), [ADR 0008](docs/adr/0008-screen-follows-roll-state.md).
```

```bash
git add app/src CHANGELOG.md
git commit -m "feat: add the Film picker and a Shutter that can have no Roll"
```

---

### Task 4: Rewind button, confirm dialog and the Film name in the viewfinder

**Files:**
- Modify: `app/src/main/java/app/cineroll/ui/Screens.kt`
- Create: `app/src/test/java/app/cineroll/ui/RewindWarningTest.kt`
- Modify: `app/src/androidTest/java/app/cineroll/ui/ShootSmokeTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `ShootViewModel.rewind()`, `ShootViewModel.clear()`, `Shutter.busy`, `Roll.left`, `Roll.film` from Tasks 2 and 3.
- Produces: `internal fun rewindWarning(left: Int): String` in `Screens.kt`; test tags `film-name`, `rewind`, `rewind-confirm`, `load-new-roll`, and the existing `counter` and `shutter`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/app/cineroll/ui/RewindWarningTest.kt`:

```kotlin
package app.cineroll.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RewindWarningTest {

    @Test
    fun saysHowManyExposuresWillBeLost() {
        assertEquals("The 23 Exposures left will be lost.", rewindWarning(23))
        assertEquals("The 36 Exposures left will be lost.", rewindWarning(36))
    }

    @Test
    fun usesTheSingularForOneExposure() {
        assertEquals("The 1 Exposure left will be lost.", rewindWarning(1))
    }
}
```

Replace the whole of `app/src/androidTest/java/app/cineroll/ui/ShootSmokeTest.kt`:

```kotlin
package app.cineroll.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class ShootSmokeTest {

    @get:Rule(order = 0)
    val freshRoll = object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("cineroll_roll", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @get:Rule(order = 1)
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 15_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Leaves one developed Exposure in DCIM/Cineroll on the test device. */
    @Test
    fun pickAFilmShootRewindAndLoadAgain() {
        // A fresh install shows the picker.
        waitForTag("film-Mono 400")
        compose.onNodeWithTag("film-Mono 400").performClick()

        // The viewfinder shows the Film and a full Roll.
        waitForText("36/36")
        compose.onNodeWithTag("film-name").assertTextEquals("Mono 400")
        compose.onNodeWithTag("counter").assertTextEquals("36/36")

        // One Exposure.
        compose.onNodeWithTag("shutter").performClick()
        waitForText("35/36", timeoutMillis = 30_000)
        compose.onNodeWithTag("counter").assertTextEquals("35/36")

        // Rewind, confirm. The button is disabled until the Exposure has been developed.
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("rewind") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("rewind").performClick()
        compose.onNodeWithTag("rewind-confirm").performClick()
        waitForText("This Roll is finished.")

        // Load new Roll goes back to the picker.
        compose.onNodeWithTag("load-new-roll").performClick()
        waitForTag("film-Mono 400")
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.cineroll.ui.RewindWarningTest'`
Expected: FAIL to compile: `Unresolved reference 'rewindWarning'`.

- [ ] **Step 3: Write the implementation**

In `app/src/main/java/app/cineroll/ui/Screens.kt`:

1. Add these imports: `androidx.compose.material3.AlertDialog`, `androidx.compose.material3.TextButton`.
2. Add `rewindWarning` at the end of the file (after `bindCamera`):

```kotlin
/** What the Rewind dialog says will be lost. */
internal fun rewindWarning(left: Int): String =
    if (left == 1) "The 1 Exposure left will be lost." else "The $left Exposures left will be lost."
```

3. In `FinishedRollScreen`, add the tag to the button: change its `modifier` to

```kotlin
        Button(
            onClick = onLoadNewRoll,
            modifier = Modifier.padding(top = 24.dp).testTag("load-new-roll"),
        ) { Text("Load new Roll") }
```

4. Replace the whole of `ViewfinderScreen` with:

```kotlin
@Composable
private fun ViewfinderScreen(viewModel: ShootViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val roll = viewModel.shutter.roll.collectAsState().value ?: return
    val busy by viewModel.shutter.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmingRewind by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { bindCamera(ctx, lifecycleOwner, it, viewModel.capturer) }
                },
            )
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = roll.film.name, color = Color.White, modifier = Modifier.testTag("film-name"))
                Text(text = "${roll.left}/${roll.total}", color = Color.White, modifier = Modifier.testTag("counter"))
            }
            Button(
                onClick = { confirmingRewind = true },
                enabled = !busy,
                modifier = Modifier.align(Alignment.BottomStart).padding(32.dp).testTag("rewind"),
            ) { Text("Rewind") }
            Button(
                onClick = viewModel::shoot,
                enabled = !busy,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).testTag("shutter"),
            ) { Text("Shoot") }
        }
    }

    if (confirmingRewind) {
        AlertDialog(
            onDismissRequest = { confirmingRewind = false },
            title = { Text("Rewind?") },
            text = { Text(rewindWarning(roll.left)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingRewind = false
                        viewModel.rewind()
                    },
                    modifier = Modifier.testTag("rewind-confirm"),
                ) { Text("Rewind") }
            },
            dismissButton = { TextButton(onClick = { confirmingRewind = false }) { Text("Cancel") } },
        )
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all unit tests pass.

Run (emulator or device connected): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.ui.ShootSmokeTest`
Expected: BUILD SUCCESSFUL, 1 test passes (picker, Mono 400, `36/36` to `35/36`, Rewind, Finished Roll screen, picker again).

Then the whole suite: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

By hand (emulator or Pixel), things the smoke test does not cover; note in the PR what you saw: (1) Cancel in the Rewind dialog leaves the Roll and counter unchanged; (2) close and reopen the app after a Rewind: the Finished Roll screen is still there; (3) close and reopen the app on the picker: the picker is still there; (4) a Mono 400 Exposure in the gallery is black and white.

- [ ] **Step 5: Log it and commit**

Add under `### Added` in `CHANGELOG.md`, newest first:

```markdown
- 2026-11-24: Rewind on screen. The viewfinder shows the Film name above the counter and has a Rewind button (disabled while an Exposure is developing) that asks "Rewind? The 23 Exposures left will be lost." before it ends the Roll and shows the Finished Roll screen. The smoke test now covers the whole second slice: picker, Mono 400, one Exposure, Rewind, Load new Roll, picker. The second slice, choose your Film, is complete. See [plan](docs/plans/2026-11-24-choose-your-film.md), [spec](docs/specs/2026-11-24-choose-your-film-design.md).
```

```bash
git add app/src CHANGELOG.md
git commit -m "feat: add Rewind and show the Film name in the viewfinder"
```

---

## Revisions

*(None yet. Record here, with the date, any change made against this plan while building it.)*

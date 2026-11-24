# Shoot One Roll Implementation Plan

> **For agentic workers:** Implement this plan task-by-task, in order — one fresh subagent per task (recommended) or inline in one session. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android app where you shoot one Roll of 36 Exposures on one 35mm Camera with one Film, each developed Exposure saved to the gallery.

**Architecture:** Pure Kotlin domain (`Film`, `Roll`) and a `Shutter` that runs capture → develop → save → charge the Roll, with the platform pieces (CameraX, GPU, MediaStore, SharedPreferences) behind small interfaces so the flow is unit-testable with fakes. The Film is data; a GLES 3.0 shader applies it after capture. Spec: [`docs/specs/2026-11-24-shoot-one-roll-design.md`](../specs/2026-11-24-shoot-one-roll-design.md).

**Tech Stack:** Kotlin 2.4.21, Android Gradle Plugin 9.5.0 (built-in Kotlin), Gradle 9.6.0, JDK 17, compileSdk and targetSdk 37, CameraX 1.7.0, Jetpack Compose (BOM 2026.09.00), kotlinx-coroutines 1.11.0, OpenGL ES 3.0 via `EGL14`, JUnit 4. Versions looked up on 2026-11-24; see `Revisions` at the bottom.

## Prerequisites (not a task)

This machine had no JDK, Gradle, Android SDK or `adb` when the plan was written. Before Task 1, install JDK 17 and Android Studio (or the command-line SDK) with platform API 37, and have an emulator (with a virtual back camera) or a Pixel connected. Create `local.properties` with `sdk.dir=...` (it is git-ignored). Tasks 2, 3, 5 and 6 run instrumented tests and need that emulator or device.

## Global Constraints

- Native Android, Kotlin, CameraX; Pixel phones first (spec, ADR 0005).
- `minSdk` = 29 (chosen here, as the spec leaves it to the plan): `MediaStore` `RELATIVE_PATH` works without a storage permission from Android 10.
- Camera = one 35mm Camera; 35mm holds **36** Exposures (ADR 0002).
- The viewfinder is plain; the Film is applied only after capture, on the GPU (ADR 0006).
- Only the developed Exposure is saved; the captured original is dropped (ADR 0001).
- Each developed Exposure is saved to the gallery under a `Cineroll` album (`DCIM/Cineroll`, see ADR 0007; first written as `Pictures/Cineroll`).
- The counter shows Exposures **left**, as `23/36`; a new Roll shows `36/36`.
- **The Roll is charged after the save, never before.** A failure at capture, develop or save uses no Exposure.
- At 36 the Roll becomes a Finished Roll and the app asks for a new one ("Load new Roll").
- The loaded Roll survives closing the app.
- Out of scope: several Cameras, Rewind, Contact Sheet, Lens, Diptych, border and date stamp, live Film preview, more Films, Film picker, retries, queue of pending Exposures, crash reporting.
- Use [`GLOSSARY.md`](../../GLOSSARY.md) terms in code and docs (Film, Camera, Roll, Exposure, Finished Roll, Format). Avoid the words it lists under _Avoid_ in code names.
- Every task adds one entry under `## [Unreleased]` in `CHANGELOG.md` (date = the day the task is done, `YYYY-MM-DD`; the examples below use 2026-11-24) and commits it with the code.
- **Never** add a `Co-Authored-By:` line or any attribution trailer to a commit message.
- Per [ADR 0003](../adr/0003-issue-and-pr-per-task.md): one GitHub issue and one PR per task, branch `task/<issue>-<slug>`.

## File Structure

```
settings.gradle.kts                      root build
build.gradle.kts
gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/app/cineroll/
  film/Film.kt                           Film data class + Films (built-in Film)
  film/FilmMath.kt                       reference maths of the Film look (CPU)
  roll/Roll.kt                           Format + Roll
  roll/RollStore.kt                      RollStore interface + PrefsRollStore
  develop/Developer.kt                   CapturedImage, Developer, DevelopException
  develop/GpuDeveloper.kt                decode, rotate, render, encode
  develop/FilmRenderer.kt                offscreen GLES shader pass
  shoot/Shutter.kt                       Capturer, Gallery, ShootResult, Shutter
  platform/CameraXCapturer.kt            Capturer backed by CameraX
  platform/MediaStoreGallery.kt          Gallery backed by MediaStore
  ui/ShootViewModel.kt                   wires the real pieces
  ui/MainActivity.kt, ui/Screens.kt      Compose screens
app/src/test/java/app/cineroll/...       JVM unit tests
app/src/androidTest/java/app/cineroll/...  instrumented tests
```

---

### Task 1: Project scaffold, `Film` and `Roll` (#1)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/app/cineroll/film/Film.kt`, `app/src/main/java/app/cineroll/film/FilmMath.kt`, `app/src/main/java/app/cineroll/roll/Roll.kt`
- Test: `app/src/test/java/app/cineroll/film/FilmTest.kt`, `app/src/test/java/app/cineroll/film/FilmMathTest.kt`, `app/src/test/java/app/cineroll/roll/RollTest.kt`
- Modify: `.gitignore`, `CHANGELOG.md`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class Film(name: String, redGain: Float, greenGain: Float, blueGain: Float, saturation: Float, contrast: Float, lift: Float, grain: Float)`; `object Films { val Everyday400: Film; val all: List<Film>; fun byName(name: String): Film? }`
  - `object FilmMath { fun develop(rgb: FloatArray, film: Film): FloatArray }` (rgb in 0..1, no grain)
  - `enum class Format(val exposures: Int) { MM35(36) }`
  - `data class Roll(film: Film, format: Format, used: Int = 0)` with `total: Int`, `left: Int`, `isFinished: Boolean`, `fun expose(): Roll`, `Roll.new(film: Film): Roll`

- [ ] **Step 1: Create the build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "cineroll"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`app/build.gradle.kts` (all dependencies for the whole plan are declared here once):

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.cineroll"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.cineroll"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    val cameraX = "1.7.0"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

`app/src/main/AndroidManifest.xml` (the launcher activity is added in Task 6):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:allowBackup="false"
        android:label="Cineroll"
        android:theme="@android:style/Theme.Material.NoActionBar" />
</manifest>
```

Append to `.gitignore`:

```gitignore

# Android and Gradle
.gradle/
build/
*.iml
.cxx/
captures/
```

- [ ] **Step 2: Generate the Gradle wrapper and check the build runs**

Run (needs Gradle installed once, for example with SDKMAN `sdk install gradle 9.6.0`; or open the folder in Android Studio and let it create the wrapper):

```bash
gradle wrapper --gradle-version 9.6.0
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` (no tests yet, so the test task reports no sources).

- [ ] **Step 3: Write the failing tests**

`app/src/test/java/app/cineroll/film/FilmTest.kt`:

```kotlin
package app.cineroll.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class FilmTest {

    @Test
    fun builtInFilmIsValidAndNamed() {
        val film = Films.Everyday400
        assertEquals("Everyday 400", film.name)
        assertEquals(listOf(film), Films.all)
    }

    @Test
    fun findsBuiltInFilmByName() {
        assertSame(Films.Everyday400, Films.byName("Everyday 400"))
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
}
```

`app/src/test/java/app/cineroll/film/FilmMathTest.kt`:

```kotlin
package app.cineroll.film

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FilmMathTest {

    private val neutral = Film("neutral", 1f, 1f, 1f, 1f, 1f, 0f, 0f)

    @Test
    fun neutralFilmLeavesColoursAlone() {
        val out = FilmMath.develop(floatArrayOf(0.2f, 0.5f, 0.8f), neutral)
        assertArrayEquals(floatArrayOf(0.2f, 0.5f, 0.8f), out, 0.0001f)
    }

    @Test
    fun liftRaisesBlacksAndKeepsWhite() {
        val lifted = neutral.copy(lift = 0.1f)
        assertEquals(0.1f, FilmMath.develop(floatArrayOf(0f, 0f, 0f), lifted)[0], 0.0001f)
        assertEquals(1f, FilmMath.develop(floatArrayOf(1f, 1f, 1f), lifted)[0], 0.0001f)
    }

    @Test
    fun zeroSaturationGivesGrey() {
        val grey = neutral.copy(saturation = 0f)
        val out = FilmMath.develop(floatArrayOf(1f, 0f, 0f), grey)
        assertEquals(out[0], out[1], 0.0001f)
        assertEquals(out[1], out[2], 0.0001f)
        assertEquals(0.2126f, out[0], 0.0001f)
    }

    @Test
    fun gainsShiftEachChannel() {
        val warm = neutral.copy(redGain = 1.2f, blueGain = 0.5f)
        val out = FilmMath.develop(floatArrayOf(0.5f, 0.5f, 0.5f), warm)
        assertArrayEquals(floatArrayOf(0.6f, 0.5f, 0.25f), out, 0.0001f)
    }

    @Test
    fun contrastPivotsAroundMidGreyAndResultIsClamped() {
        val punchy = neutral.copy(contrast = 2f)
        val out = FilmMath.develop(floatArrayOf(0.1f, 0.5f, 0.9f), punchy)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), out, 0.0001f)
    }
}
```

`app/src/test/java/app/cineroll/roll/RollTest.kt`:

```kotlin
package app.cineroll.roll

import app.cineroll.film.Films
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RollTest {

    @Test
    fun newRollIs35mmWith36ExposuresLeft() {
        val roll = Roll.new(Films.Everyday400)
        assertEquals(Format.MM35, roll.format)
        assertEquals(36, roll.total)
        assertEquals(0, roll.used)
        assertEquals(36, roll.left)
        assertFalse(roll.isFinished)
    }

    @Test
    fun exposeUsesOneExposure() {
        val roll = Roll.new(Films.Everyday400).expose()
        assertEquals(1, roll.used)
        assertEquals(35, roll.left)
    }

    @Test
    fun rollFinishesAfterThe36thExposure() {
        var roll = Roll.new(Films.Everyday400)
        repeat(35) { roll = roll.expose() }
        assertFalse(roll.isFinished)
        roll = roll.expose()
        assertTrue(roll.isFinished)
        assertEquals(0, roll.left)
    }

    @Test
    fun finishedRollRefusesAnotherExposure() {
        val finished = Roll(Films.Everyday400, Format.MM35, used = 36)
        assertThrows(IllegalStateException::class.java) { finished.expose() }
    }

    @Test
    fun rejectsUsedCountOutsideTheRoll() {
        assertThrows(IllegalArgumentException::class.java) { Roll(Films.Everyday400, Format.MM35, used = 37) }
        assertThrows(IllegalArgumentException::class.java) { Roll(Films.Everyday400, Format.MM35, used = -1) }
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL at compile time with `Unresolved reference: Films` (and `Roll`, `FilmMath`).

- [ ] **Step 5: Write the implementation**

`app/src/main/java/app/cineroll/film/Film.kt`:

```kotlin
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
```

`app/src/main/java/app/cineroll/film/FilmMath.kt`:

```kotlin
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
```

`app/src/main/java/app/cineroll/roll/Roll.kt`:

```kotlin
package app.cineroll.roll

import app.cineroll.film.Film

/** The frame shape of a Camera. It sets how many Exposures a Roll holds. */
enum class Format(val exposures: Int) {
    MM35(36),
}

/** One Film loaded into one Camera, with a fixed number of Exposures set by the Format. */
data class Roll(val film: Film, val format: Format, val used: Int = 0) {
    init {
        require(used in 0..format.exposures) { "used must be 0..${format.exposures}, was $used" }
    }

    val total: Int get() = format.exposures
    val left: Int get() = total - used
    val isFinished: Boolean get() = used >= total

    /** Uses up one Exposure. A Finished Roll cannot be shot. */
    fun expose(): Roll {
        check(!isFinished) { "Roll is finished" }
        return copy(used = used + 1)
    }

    companion object {
        fun new(film: Film): Roll = Roll(film, Format.MM35)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 13 tests passed (3 + 5 + 5).

- [ ] **Step 7: Add the CHANGELOG entry and commit**

Add under `### Added` in `CHANGELOG.md`:

```markdown
- 2026-11-24: Android project scaffold (Kotlin, Gradle, Compose, CameraX) with the `Film` and `Roll` domain: a 35mm Roll holds 36 Exposures and finishes at the 36th. See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [ADR 0005](docs/adr/0005-native-android.md), [ADR 0002](docs/adr/0002-each-camera-holds-its-own-roll.md).
```

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app CHANGELOG.md
git commit -m "feat: add Android scaffold with Film and Roll"
```

---

### Task 2: `RollStore` (#2)

**Files:**
- Create: `app/src/main/java/app/cineroll/roll/RollStore.kt`
- Test: `app/src/androidTest/java/app/cineroll/roll/PrefsRollStoreTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `Roll`, `Format`, `Films.byName`.
- Produces:
  - `interface RollStore { fun load(): Roll?; fun save(roll: Roll) }` — `save` throws `java.io.IOException` if it cannot write; `load` returns `null` when nothing valid is saved.
  - `class PrefsRollStore(context: Context) : RollStore`

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/app/cineroll/roll/PrefsRollStoreTest.kt`:

```kotlin
package app.cineroll.roll

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cineroll.film.Films
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsRollStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("cineroll_roll", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun firstLaunchHasNoRoll() {
        assertNull(PrefsRollStore(context).load())
    }

    @Test
    fun savedRollComesBackWithSameFilmAndCount() {
        val roll = Roll.new(Films.Everyday400).expose().expose()
        PrefsRollStore(context).save(roll)
        assertEquals(roll, PrefsRollStore(context).load())
    }

    @Test
    fun finishedRollStaysFinished() {
        val finished = Roll(Films.Everyday400, Format.MM35, used = 36)
        PrefsRollStore(context).save(finished)
        assertTrue(PrefsRollStore(context).load()!!.isFinished)
    }

    @Test
    fun unknownFilmCountsAsNoRoll() {
        prefs.edit().putString("film", "No such film").putString("format", "MM35").putInt("used", 3).commit()
        assertNull(PrefsRollStore(context).load())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.roll.PrefsRollStoreTest"`
Expected: FAIL at compile time with `Unresolved reference: PrefsRollStore`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/cineroll/roll/RollStore.kt`:

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
        return Roll(film, format, used)
    }

    override fun save(roll: Roll) {
        val written = prefs.edit()
            .putString("film", roll.film.name)
            .putString("format", roll.format.name)
            .putInt("used", roll.used)
            .commit()
        if (!written) throw IOException("Could not save the Roll")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.roll.PrefsRollStoreTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed on the emulator or device.

- [ ] **Step 5: Add the CHANGELOG entry and commit**

```markdown
- 2026-11-24: `RollStore` keeps the loaded Roll across app restarts (Film, Format, Exposures used). See [plan](docs/plans/2026-11-24-shoot-one-roll.md).
```

```bash
git add app CHANGELOG.md
git commit -m "feat: save and load the loaded Roll"
```

---

### Task 3: `Developer` (GPU Film pass) (#3)

**Files:**
- Create: `app/src/main/java/app/cineroll/develop/Developer.kt`, `app/src/main/java/app/cineroll/develop/GpuDeveloper.kt`, `app/src/main/java/app/cineroll/develop/FilmRenderer.kt`
- Test: `app/src/androidTest/java/app/cineroll/develop/GpuDeveloperTest.kt`
- Modify: `CHANGELOG.md`, `docs/specs/2026-11-24-shoot-one-roll-design.md` (Revisions)

**Interfaces:**
- Consumes: `Film`, `FilmMath`.
- Produces:
  - `class CapturedImage(val jpeg: ByteArray, val rotationDegrees: Int)`
  - `class DevelopException(message: String, cause: Throwable? = null) : Exception`
  - `interface Developer { fun develop(image: CapturedImage, film: Film): ByteArray }` — returns a developed JPEG; throws `DevelopException` on any failure (bad JPEG, image too large, GL error, out of memory).
  - `class GpuDeveloper(jpegQuality: Int = 95) : Developer`

**Note on testing:** the spec pins the look with a golden image. This plan pins it with sample pixels instead: the test compares GPU output against `FilmMath` (the CPU reference) at known points, with grain off. That checks the shader against the same formulas the Film is defined by and needs no binary file in git. Step 6 records this in the spec's `Revisions`.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/app/cineroll/develop/GpuDeveloperTest.kt`:

```kotlin
package app.cineroll.develop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cineroll.film.FilmMath
import app.cineroll.film.Films
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpuDeveloperTest {

    private val developer = GpuDeveloper()
    private val noGrain = Films.Everyday400.copy(grain = 0f)

    private val quadrantColours = listOf(
        Color.rgb(200, 60, 40), Color.rgb(30, 160, 90),
        Color.rgb(40, 70, 200), Color.rgb(128, 128, 128),
    )

    /** Four solid quadrants, so JPEG blocks stay clean at the quadrant centres. */
    private fun fourColourJpeg(width: Int = 64, height: Int = 64): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halfW = width / 2
        val halfH = height / 2
        val rects = listOf(
            Rect(0, 0, halfW, halfH), Rect(halfW, 0, width, halfH),
            Rect(0, halfH, halfW, height), Rect(halfW, halfH, width, height),
        )
        rects.forEachIndexed { i, rect -> canvas.drawRect(rect, Paint().apply { color = quadrantColours[i] }) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }

    private fun decode(jpeg: ByteArray): Bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    @Test
    fun matchesTheFilmMathAtSamplePoints() {
        val input = fourColourJpeg()
        val inputBitmap = decode(input)
        val output = decode(developer.develop(CapturedImage(input, 0), noGrain))

        assertEquals(64, output.width)
        assertEquals(64, output.height)
        for ((x, y) in listOf(16 to 16, 48 to 16, 16 to 48, 48 to 48)) {
            val src = inputBitmap.getPixel(x, y)
            val expected = FilmMath.develop(
                floatArrayOf(Color.red(src) / 255f, Color.green(src) / 255f, Color.blue(src) / 255f),
                noGrain,
            )
            val got = output.getPixel(x, y)
            assertNear(expected[0] * 255f, Color.red(got), "red at $x,$y")
            assertNear(expected[1] * 255f, Color.green(got), "green at $x,$y")
            assertNear(expected[2] * 255f, Color.blue(got), "blue at $x,$y")
        }
    }

    @Test
    fun appliesTheCaptureRotation() {
        val input = fourColourJpeg(width = 64, height = 32)
        val output = decode(developer.develop(CapturedImage(input, 90), noGrain))
        assertEquals(32, output.width)
        assertEquals(64, output.height)
    }

    @Test
    fun grainIsDeterministicAndVisible() {
        val input = fourColourJpeg()
        val grainy = Films.Everyday400.copy(grain = 0.1f)
        val first = developer.develop(CapturedImage(input, 0), grainy)
        val second = developer.develop(CapturedImage(input, 0), grainy)
        val clean = developer.develop(CapturedImage(input, 0), grainy.copy(grain = 0f))

        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(clean))

        val bitmap = decode(first)
        val distinct = (4..11).flatMap { x -> (4..11).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("grain should vary pixels inside a flat area", distinct.size > 1)
    }

    @Test
    fun badJpegThrowsDevelopException() {
        assertThrows(DevelopException::class.java) {
            developer.develop(CapturedImage(byteArrayOf(1, 2, 3), 0), noGrain)
        }
    }

    private fun assertNear(expected: Float, actual: Int, what: String) {
        // 4/255 covers GPU rounding plus the JPEG round trip. Raise to 6 only if a device proves noisier.
        assertTrue("$what: expected ~$expected, got $actual", abs(expected - actual) <= 4f)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.develop.GpuDeveloperTest"`
Expected: FAIL at compile time with `Unresolved reference: GpuDeveloper`.

- [ ] **Step 3: Write the interface and the JPEG side**

`app/src/main/java/app/cineroll/develop/Developer.kt`:

```kotlin
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
```

`app/src/main/java/app/cineroll/develop/GpuDeveloper.kt`:

```kotlin
package app.cineroll.develop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import app.cineroll.film.Film
import java.io.ByteArrayOutputStream

class GpuDeveloper(private val jpegQuality: Int = 95) : Developer {

    override fun develop(image: CapturedImage, film: Film): ByteArray {
        try {
            val source = decodeUpright(image)
            try {
                val developed = FilmRenderer().render(source, film)
                try {
                    return encode(developed)
                } finally {
                    developed.recycle()
                }
            } finally {
                source.recycle()
            }
        } catch (e: DevelopException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw DevelopException("Out of memory while developing", e)
        } catch (e: RuntimeException) {
            throw DevelopException("Could not develop the Exposure", e)
        }
    }

    private fun decodeUpright(image: CapturedImage): Bitmap {
        val decoded = BitmapFactory.decodeByteArray(image.jpeg, 0, image.jpeg.size)
            ?: throw DevelopException("Captured image is not a valid JPEG")
        if (image.rotationDegrees % 360 == 0) return decoded
        val matrix = Matrix().apply { postRotate(image.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, false)
        decoded.recycle()
        return rotated
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
            throw DevelopException("Could not encode the developed JPEG")
        }
        return out.toByteArray()
    }
}
```

- [ ] **Step 4: Write the GPU pass**

`app/src/main/java/app/cineroll/develop/FilmRenderer.kt`:

```kotlin
package app.cineroll.develop

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import app.cineroll.film.Film
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * One offscreen GLES 3.0 pass that applies a Film to a bitmap. A fresh EGL context is created and
 * torn down per call, so it is safe to call from any background thread.
 *
 * The shader mirrors `FilmMath` (gains, saturation, contrast, lift) and adds grain: a hash of the
 * pixel coordinates, so the same image and Film always give the same output.
 */
internal class FilmRenderer {

    fun render(source: Bitmap, film: Film): Bitmap {
        val width = source.width
        val height = source.height

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw DevelopException("EGL is not available")
        }

        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var program = 0
        val textures = IntArray(1)
        try {
            val config = chooseConfig(display)
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) throw DevelopException("Could not create a GL context")
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0,
            )
            if (surface == EGL14.EGL_NO_SURFACE) throw DevelopException("Image too large for the GPU: ${width}x$height")
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throw DevelopException("Could not use the GL context")
            }

            val maxTexture = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            if (width > maxTexture[0] || height > maxTexture[0]) {
                throw DevelopException("Image too large for the GPU: ${width}x$height")
            }

            program = buildProgram()
            GLES30.glGenTextures(1, textures, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, source, 0)

            GLES30.glViewport(0, 0, width, height)
            GLES30.glUseProgram(program)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uImage"), 0)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uGain"), film.redGain, film.greenGain, film.blueGain)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSaturation"), film.saturation)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uContrast"), film.contrast)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uLift"), film.lift)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uGrain"), film.grain)

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, QUAD)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glFinish()
            checkGl("draw")

            // The quad maps framebuffer row 0 to image row 0, so rows come back top-down: no flip needed.
            val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
            checkGl("read")
            pixels.rewind()
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.copyPixelsFromBuffer(pixels)
            return result
        } finally {
            if (textures[0] != 0) GLES30.glDeleteTextures(1, textures, 0)
            if (program != 0) GLES30.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        }
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            throw DevelopException("No suitable GL config")
        }
        return configs[0]!!
    }

    private fun buildProgram(): Int {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw DevelopException("Could not link the Film shader: $log")
        }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw DevelopException("Could not compile the Film shader: $log")
        }
        return shader
    }

    private fun checkGl(step: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) throw DevelopException("GL error 0x${error.toString(16)} after $step")
    }

    private companion object {
        val QUAD: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

        const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

        const val FRAGMENT_SHADER = """#version 300 es
precision highp float;
uniform sampler2D uImage;
uniform vec3 uGain;
uniform float uSaturation;
uniform float uContrast;
uniform float uLift;
uniform float uGrain;
in vec2 vUv;
out vec4 outColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 c = texture(uImage, vUv).rgb * uGain;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = vec3(luma) + (c - vec3(luma)) * uSaturation;
    c = (c - 0.5) * uContrast + 0.5;
    c = uLift + c * (1.0 - uLift);
    c += (hash(gl_FragCoord.xy) - 0.5) * uGrain;
    outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.develop.GpuDeveloperTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed. If `matchesTheFilmMathAtSamplePoints` is off by 5 or 6 on a device, the shader and `FilmMath` disagree: fix the shader or the maths, do not widen the tolerance beyond 6.

- [ ] **Step 6: Record the test approach in the spec**

Add this bullet under the existing `## Revisions` heading at the bottom of `docs/specs/2026-11-24-shoot-one-roll-design.md`:

```markdown
- 2026-11-24: In Testing, the `Developer` golden image became a sample-pixel check against `FilmMath` (the CPU reference of the Film look), with grain off, plus tests for rotation, deterministic grain and a bad JPEG. Same intent (the Film look is pinned), no binary golden file in git. See the [plan](../plans/2026-11-24-shoot-one-roll.md), Task 3.
```

- [ ] **Step 7: Add the CHANGELOG entry and commit**

```markdown
- 2026-11-24: `Developer` bakes the Film into a captured JPEG with a GLES 3.0 shader (gains, saturation, contrast, lift, deterministic grain), applying the capture rotation. The Film look is pinned by a test against the CPU reference. See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [ADR 0006](docs/adr/0006-develop-on-the-gpu-after-capture.md), [spec](docs/specs/2026-11-24-shoot-one-roll-design.md).
```

```bash
git add app CHANGELOG.md docs/specs/2026-11-24-shoot-one-roll-design.md
git commit -m "feat: develop Exposures on the GPU"
```

---

### Task 4: `Shutter` (#4)

**Files:**
- Create: `app/src/main/java/app/cineroll/shoot/Shutter.kt`
- Test: `app/src/test/java/app/cineroll/shoot/ShutterTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `Roll`, `Films`, `RollStore`, `CapturedImage`, `Developer`, `DevelopException`.
- Produces:
  - `interface Capturer { suspend fun capture(): CapturedImage }` — throws `CaptureException`.
  - `interface Gallery { fun save(jpeg: ByteArray) }` — throws `GalleryException`.
  - `class CaptureException(message: String, cause: Throwable? = null) : Exception`, `class GalleryException(message: String, cause: Throwable? = null) : Exception`
  - `enum class ShootResult { Developed, Busy, RollFinished, CaptureFailed, DevelopFailed, SaveFailed }`
  - `class Shutter(capturer, developer, gallery, store, initialRoll, work: CoroutineDispatcher = Dispatchers.Default, log: (String, Throwable) -> Unit = { _, _ -> })` with `val roll: StateFlow<Roll>`, `val busy: StateFlow<Boolean>`, `suspend fun shoot(): ShootResult`, `fun loadNewRoll()`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/cineroll/shoot/ShutterTest.kt`:

```kotlin
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
    val saved = mutableListOf<Roll>()
    override fun load(): Roll? = saved.lastOrNull()
    override fun save(roll: Roll) {
        failure?.let { throw it }
        saved += roll
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShutterTest {

    private val capturer = FakeCapturer()
    private val developer = FakeDeveloper()
    private val gallery = FakeGallery()
    private val store = FakeStore()

    private fun TestScope.shutter(roll: Roll = Roll.new(Films.Everyday400)) =
        Shutter(capturer, developer, gallery, store, roll, work = UnconfinedTestDispatcher(testScheduler))

    @Test
    fun successSavesTheDevelopedExposureAndChargesTheRollOnce() = runTest {
        val shutter = shutter()

        assertEquals(ShootResult.Developed, shutter.shoot())

        assertEquals(1, gallery.saved.size)
        assertArrayEquals(byteArrayOf(9, 9), gallery.saved.single())
        assertEquals(1, shutter.roll.value.used)
        assertEquals(1, store.saved.single().used)
        assertFalse(shutter.busy.value)
    }

    @Test
    fun captureFailureChargesNothing() = runTest {
        capturer.failure = CaptureException("boom")
        val shutter = shutter()

        assertEquals(ShootResult.CaptureFailed, shutter.shoot())

        assertEquals(0, shutter.roll.value.used)
        assertTrue(gallery.saved.isEmpty())
        assertTrue(store.saved.isEmpty())
        assertFalse(shutter.busy.value)
    }

    @Test
    fun developFailureChargesNothingAndSavesNothing() = runTest {
        developer.failure = DevelopException("boom")
        val shutter = shutter()

        assertEquals(ShootResult.DevelopFailed, shutter.shoot())

        assertEquals(0, shutter.roll.value.used)
        assertTrue(gallery.saved.isEmpty())
        assertTrue(store.saved.isEmpty())
        assertFalse(shutter.busy.value)
    }

    @Test
    fun saveFailureChargesNothing() = runTest {
        gallery.failure = GalleryException("storage full")
        val shutter = shutter()

        assertEquals(ShootResult.SaveFailed, shutter.shoot())

        assertEquals(0, shutter.roll.value.used)
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

        assertEquals(2, shutter.roll.value.used)
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
        assertEquals(1, shutter.roll.value.used)
    }

    @Test
    fun the36thExposureFinishesTheRollAndNothingMoreCanBeShot() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 35))

        assertEquals(ShootResult.Developed, shutter.shoot())
        assertTrue(shutter.roll.value.isFinished)

        val callsBefore = capturer.calls
        assertEquals(ShootResult.RollFinished, shutter.shoot())
        assertEquals(callsBefore, capturer.calls)
    }

    @Test
    fun loadNewRollReplacesAFinishedRollAndSavesIt() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 36))

        shutter.loadNewRoll()

        assertEquals(0, shutter.roll.value.used)
        assertEquals(0, store.saved.single().used)
    }

    @Test
    fun loadNewRollDoesNothingWhileARollIsLoaded() = runTest {
        val shutter = shutter(Roll(Films.Everyday400, Format.MM35, used = 5))

        shutter.loadNewRoll()

        assertEquals(5, shutter.roll.value.used)
        assertTrue(store.saved.isEmpty())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.cineroll.shoot.ShutterTest"`
Expected: FAIL at compile time with `Unresolved reference: Shutter`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/cineroll/shoot/Shutter.kt`:

```kotlin
package app.cineroll.shoot

import app.cineroll.develop.CapturedImage
import app.cineroll.develop.DevelopException
import app.cineroll.develop.Developer
import app.cineroll.film.Films
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

enum class ShootResult { Developed, Busy, RollFinished, CaptureFailed, DevelopFailed, SaveFailed }

/**
 * The shoot flow: capture, develop, save, then charge the Roll. The Roll is charged only after the
 * save succeeds, so a failure never costs an Exposure.
 */
class Shutter(
    private val capturer: Capturer,
    private val developer: Developer,
    private val gallery: Gallery,
    private val store: RollStore,
    initialRoll: Roll,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val _roll = MutableStateFlow(initialRoll)
    val roll: StateFlow<Roll> = _roll

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val taking = AtomicBoolean(false)

    suspend fun shoot(): ShootResult {
        val current = _roll.value
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

    /** Loads a fresh Roll, but only when the current one is finished. */
    fun loadNewRoll() {
        if (!_roll.value.isFinished) return
        val fresh = Roll.new(Films.Everyday400)
        _roll.value = fresh
        saveRoll(fresh)
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

Note: `saveRoll` runs on the caller's thread (a `SharedPreferences` commit is quick). `work` is only used for the heavy steps.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (13 from Task 1 plus 9 `ShutterTest`).

- [ ] **Step 5: Add the CHANGELOG entry and commit**

```markdown
- 2026-11-24: `Shutter` runs capture, develop, save, then charges the Roll. A failure at capture, develop or save uses no Exposure; a second tap while developing is ignored; the 36th Exposure finishes the Roll. See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [spec](docs/specs/2026-11-24-shoot-one-roll-design.md).
```

```bash
git add app CHANGELOG.md
git commit -m "feat: add the Shutter shoot flow"
```

---

### Task 5: Platform adapters and wiring (#5)

**Files:**
- Create: `app/src/main/java/app/cineroll/platform/MediaStoreGallery.kt`, `app/src/main/java/app/cineroll/platform/CameraXCapturer.kt`, `app/src/main/java/app/cineroll/ui/ShootViewModel.kt`
- Test: `app/src/androidTest/java/app/cineroll/platform/MediaStoreGalleryTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: `Gallery`, `GalleryException`, `Capturer`, `CaptureException`, `CapturedImage`, `Shutter`, `ShootResult`, `PrefsRollStore`, `GpuDeveloper`, `Films`, `Roll`.
- Produces:
  - `class MediaStoreGallery(context: Context) : Gallery` — writes `Pictures/Cineroll/CINEROLL_<timestamp>.jpg`.
  - `class CameraXCapturer(executor: Executor) : Capturer` with `fun bind(imageCapture: ImageCapture?)`.
  - `class ShootViewModel(app: Application) : AndroidViewModel` with `val capturer: CameraXCapturer`, `val shutter: Shutter`, `val message: StateFlow<String?>`, `fun shoot()`, `fun messageShown()`.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/app/cineroll/platform/MediaStoreGalleryTest.kt`:

```kotlin
package app.cineroll.platform

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreGalleryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun cineRollImageIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("Pictures/Cineroll/%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    @Test
    fun savesTheJpegIntoThePicturesCineRollAlbum() {
        val before = cineRollImageIds()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

        MediaStoreGallery(context).save(jpeg)

        val added = cineRollImageIds() - before
        try {
            assertEquals(1, added.size)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, added.single())
            val stored = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(jpeg, stored)
        } finally {
            added.forEach { id ->
                context.contentResolver.delete(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.platform.MediaStoreGalleryTest"`
Expected: FAIL at compile time with `Unresolved reference: MediaStoreGallery`.

- [ ] **Step 3: Write the adapters**

`app/src/main/java/app/cineroll/platform/MediaStoreGallery.kt`:

```kotlin
package app.cineroll.platform

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import app.cineroll.shoot.Gallery
import app.cineroll.shoot.GalleryException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves developed Exposures to `Pictures/Cineroll`. Needs no storage permission on Android 10+. */
class MediaStoreGallery(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : Gallery {

    override fun save(jpeg: ByteArray) {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now()))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CINEROLL_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cineroll")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw GalleryException("Gallery refused the new image")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw GalleryException("Could not open the new image")
            stream.use { it.write(jpeg) }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is GalleryException) e else GalleryException("Could not write the image", e)
        }
    }
}
```

`app/src/main/java/app/cineroll/platform/CameraXCapturer.kt`:

```kotlin
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
```

- [ ] **Step 4: Write the view model**

`app/src/main/java/app/cineroll/ui/ShootViewModel.kt`:

```kotlin
package app.cineroll.ui

import android.app.Application
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cineroll.develop.GpuDeveloper
import app.cineroll.film.Films
import app.cineroll.platform.CameraXCapturer
import app.cineroll.platform.MediaStoreGallery
import app.cineroll.roll.PrefsRollStore
import app.cineroll.roll.Roll
import app.cineroll.shoot.ShootResult
import app.cineroll.shoot.Shutter
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShootViewModel(app: Application) : AndroidViewModel(app) {

    private val tag = "Cineroll"

    val capturer = CameraXCapturer(ContextCompat.getMainExecutor(app))

    private val store = PrefsRollStore(app)

    val shutter = Shutter(
        capturer = capturer,
        developer = GpuDeveloper(),
        gallery = MediaStoreGallery(app),
        store = store,
        initialRoll = store.load() ?: newRoll(),
        log = { message, error -> Log.w(tag, message, error) },
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun shoot() {
        viewModelScope.launch {
            _message.value = when (shutter.shoot()) {
                ShootResult.CaptureFailed, ShootResult.DevelopFailed -> "Couldn't take the photo, try again"
                ShootResult.SaveFailed -> "Couldn't save the photo, check storage"
                ShootResult.Developed, ShootResult.Busy, ShootResult.RollFinished -> null
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    /** First launch: one 35mm Roll loaded with the built-in Film. */
    private fun newRoll(): Roll {
        val roll = Roll.new(Films.Everyday400)
        try {
            store.save(roll)
        } catch (e: IOException) {
            Log.w(tag, "Could not save the first Roll", e)
        }
        return roll
    }
}
```

- [ ] **Step 5: Run the tests and a build**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.platform.MediaStoreGalleryTest"
./gradlew :app:assembleDebug
```

Expected: both `BUILD SUCCESSFUL`; the gallery test passes (1 test).

- [ ] **Step 6: Add the CHANGELOG entry and commit**

```markdown
- 2026-11-24: Platform adapters: `MediaStoreGallery` saves to `Pictures/Cineroll`, `CameraXCapturer` takes the full-resolution JPEG, and `ShootViewModel` wires them to the `Shutter` and shows the spec's error messages. See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [ADR 0005](docs/adr/0005-native-android.md).
```

```bash
git add app CHANGELOG.md
git commit -m "feat: add gallery and camera adapters and wire the Shutter"
```

---

### Task 6: Screens and smoke test (#6)

**Files:**
- Create: `app/src/main/java/app/cineroll/ui/MainActivity.kt`, `app/src/main/java/app/cineroll/ui/Screens.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `README.md` (Status), `CHANGELOG.md`
- Test: `app/src/androidTest/java/app/cineroll/ui/ShootSmokeTest.kt`

**Interfaces:**
- Consumes: `ShootViewModel` (`shutter.roll`, `shutter.busy`, `shutter.loadNewRoll()`, `capturer`, `message`, `shoot()`, `messageShown()`).
- Produces: a launchable app. Test tags `"shutter"` (the shoot button) and `"counter"` (the `left/total` text).

- [ ] **Step 1: Write the failing smoke test**

`app/src/androidTest/java/app/cineroll/ui/ShootSmokeTest.kt`:

```kotlin
package app.cineroll.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    /** Leaves one developed Exposure in Pictures/Cineroll on the test device. */
    @Test
    fun shootingOneExposureMovesTheCounterFrom36To35() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("36/36").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("counter").assertTextEquals("36/36")

        compose.onNodeWithTag("shutter").performClick()

        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("35/36").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("counter").assertTextEquals("35/36")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.ui.ShootSmokeTest"`
Expected: FAIL at compile time with `Unresolved reference: MainActivity`.

- [ ] **Step 3: Write the screens**

`app/src/main/java/app/cineroll/ui/Screens.kt`:

```kotlin
package app.cineroll.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cineroll.platform.CameraXCapturer

@Composable
fun CineRollApp(viewModel: ShootViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val askForCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCameraPermission(context) }
    LaunchedEffect(Unit) {
        if (!granted) askForCamera.launch(Manifest.permission.CAMERA)
    }

    val roll by viewModel.shutter.roll.collectAsState()
    when {
        !granted -> PermissionScreen(
            onAllow = { askForCamera.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        roll.isFinished -> FinishedRollScreen(onLoadNewRoll = viewModel.shutter::loadNewRoll)
        else -> ViewfinderScreen(viewModel)
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cineroll needs the camera to take Exposures.")
        Button(onClick = onAllow, modifier = Modifier.padding(top = 24.dp)) { Text("Allow camera") }
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) { Text("Open settings") }
    }
}

@Composable
private fun FinishedRollScreen(onLoadNewRoll: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This Roll is finished.")
        Button(onClick = onLoadNewRoll, modifier = Modifier.padding(top = 24.dp)) { Text("Load new Roll") }
    }
}

@Composable
private fun ViewfinderScreen(viewModel: ShootViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val roll by viewModel.shutter.roll.collectAsState()
    val busy by viewModel.shutter.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

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
            Text(
                text = "${roll.left}/${roll.total}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).testTag("counter"),
            )
            Button(
                onClick = viewModel::shoot,
                enabled = !busy,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).testTag("shutter"),
            ) { Text("Shoot") }
        }
    }
}

private fun bindCamera(
    context: Context,
    owner: LifecycleOwner,
    previewView: PreviewView,
    capturer: CameraXCapturer,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        capturer.bind(imageCapture)
    }, ContextCompat.getMainExecutor(context))
}
```

`app/src/main/java/app/cineroll/ui/MainActivity.kt`:

```kotlin
package app.cineroll.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ShootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CineRollApp(viewModel)
            }
        }
    }
}
```

Replace the `<application ... />` element in `app/src/main/AndroidManifest.xml` with:

```xml
    <application
        android:allowBackup="false"
        android:label="Cineroll"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
```

- [ ] **Step 4: Run the smoke test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.cineroll.ui.ShootSmokeTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed. The emulator needs a working virtual back camera (AVD setting "Back camera: VirtualScene" or "Webcam0").

- [ ] **Step 5: Run the whole suite and try it by hand**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

Expected: all tests pass. By hand on a device: grant the camera, shoot a few Exposures, check `Pictures/Cineroll` in the gallery, force-stop and reopen (the counter keeps its value), deny the permission once to see the permission screen.

- [ ] **Step 6: Update the README status, add the CHANGELOG entry and commit**

In `README.md`, replace the line under `## Status`:

```markdown
First slice built: shoot one Roll of 36 Exposures on a 35mm Camera with one Film. See [`docs/specs/2026-11-24-shoot-one-roll-design.md`](docs/specs/2026-11-24-shoot-one-roll-design.md). Android only; open the folder in Android Studio and run the `app` module.
```

```markdown
- 2026-11-24: Viewfinder, permission and Roll finished screens, and a smoke test that shoots one Exposure (counter `36/36` to `35/36`). The first slice, shoot one Roll, is complete. See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [spec](docs/specs/2026-11-24-shoot-one-roll-design.md).
```

```bash
git add app README.md CHANGELOG.md
git commit -m "feat: add the viewfinder and Roll screens"
```

## Revisions

- 2026-11-24: Moved the whole build stack from the versions first written (Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.9, compileSdk and targetSdk 35, CameraX 1.4.1, Compose BOM 2024.10.01, coroutines 1.9.0, activity-compose 1.9.3, lifecycle 2.8.7, core-ktx 1.15.0, androidx.test 1.2.1 / 1.6.2 / 1.6.1) to the current stable versions in the Tech Stack line and Task 1, because the machine's Android Studio and SDK (API 37) are newer and the author is on the latest Android. Two code changes came with it: the `org.jetbrains.kotlin.android` plugin and `kotlinOptions` are gone (Android Gradle Plugin 9 has built-in Kotlin), and `LocalLifecycleOwner` is imported from `androidx.lifecycle.compose`.
- 2026-11-24: **Not yet verified on a real build.** The versions were read from developer.android.com, kotlinlang.org, gradle.org and GitHub on this date, but these points were not confirmed and Task 1's first build may need fixes: (1) which Kotlin version Android Gradle Plugin 9.5.0 bundles, and so which version the Compose compiler plugin must have (if Gradle reports a mismatch, use the version it names); (2) whether Gradle 9.6.0 is the right wrapper version (the plugin's notes give it as the minimum and default); (3) whether `targetSdk = 37` changes any behaviour this app relies on (camera, gallery saves); (4) the Compose BOM mapping of 2026.11.01 to library versions was inferred from the page order. Whoever runs Task 1 fixes these against the real build output and records what changed here.
- 2026-11-24: Task 1 built and its 13 unit tests pass. Result of the four unverified points: (1) the Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` 2.4.21 resolved and compiled with no mismatch error, so no change; (2) wrapper 9.6.0 worked, no change; (3) `targetSdk = 37` built without complaint, but its runtime behaviour for the camera and gallery saves is still untested until Tasks 5 and 6; (4) **changed**: Compose BOM `2026.11.01` maps to Compose 1.13.0, which requires `compileSdk` 37.1 or later, and this machine has only platform `android-37.0` (the build failed in `checkDebugAarMetadata`). The BOM is now `2026.09.00` (Compose 1.12.1) in the Tech Stack line and in both BOM lines of Task 1's `app/build.gradle.kts`, because that needs no new SDK platform. The other option is to install platform `android-37.1` with `sdkmanager` and set `compileSdk = 37` to `release(37) { minorApiLevel = 1 }`; this was not tried. Revisit when the SDK platform is updated.
- 2026-11-24: Task 2 built and its 4 instrumented tests pass on the emulator. **Changed**: the run command. `connectedDebugAndroidTest` in Android Gradle Plugin 9.5.0 rejects `--tests` (`Unknown command-line option '--tests'`), so run one class with `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.roll.PrefsRollStoreTest`. Tasks 3, 5 and 6 name the same `--tests` form for their instrumented tests and need the same change.
- 2026-11-24: Task 3 built and its 4 instrumented tests pass on the emulator (API 37) with the plan's code unchanged and the tolerance left at 4/255 (largest difference seen was 2.7, on blue in the bottom-left quadrant). **Changed**: the run command only. Use `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.develop.GpuDeveloperTest` instead of `--tests`, for the same reason as Task 2. The rotation test checks only the output size, not which way the image is turned; that is not verified.
- 2026-11-24: Found on a real Pixel 9 Pro after Task 5 was built, and fixed in Task 5's branch. The Task 5 code and tests above are the first version; the committed code differs in two ways. (1) `MediaStoreGallery` writes the EXIF date taken into the JPEG before publishing it (Android's media provider takes the date from the file's EXIF and the developed JPEG had none, so all saved photos had `datetaken = NULL`); the tests now use a real small JPEG and have a case for the date. (2) The save folder is `DCIM/Cineroll`, not `Pictures/Cineroll`, because Google Photos did not show `Pictures/Cineroll` on the front of Library; see [ADR 0007](../adr/0007-save-exposures-to-dcim.md). The Global Constraints line was updated to match.
- 2026-11-24: Task 6 built and its smoke test passes on the emulator (API 37); the whole suite passes (22 unit, 10 instrumented) with the screens' code unchanged. **Changed**: (1) the run command: use `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.ui.ShootSmokeTest` instead of `--tests`, as in Task 2. (2) `app/build.gradle.kts` gains `androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")` (the plan did not touch this file in Task 6): Compose's `ui-test-junit4` pulls in Espresso 3.5.0, which fails on API 37 with `NoSuchMethodException: android.hardware.input.InputManager.getInstance` before the test body runs. The permission-denied screen and the Roll finished screen are not exercised by the smoke test and were not checked by hand.

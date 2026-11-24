# Changelog

All notable changes to Cineroll are recorded in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- 2026-11-24: Moved the [shoot-one-Roll plan](docs/plans/2026-11-24-shoot-one-roll.md) and the [Android setup guide](docs/android-setup.md) from older build versions to current stable ones. Before: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, API 35, CameraX 1.4.1, Compose BOM 2024.10.01. Now: Android Gradle Plugin 9.5.0 with built-in Kotlin, Kotlin 2.4.21, Gradle 9.6.0, API 37, CameraX 1.7.0, Compose BOM 2026.11.01. Why: the installed Android Studio and SDK are newer, and the project's author runs the latest Android. Some points are not verified until Task 1 builds; see the plan's `Revisions`. No ADR changes: [ADR 0005](docs/adr/0005-native-android.md) names no versions.

### Added

- 2026-11-24: `Developer` bakes the Film into a captured JPEG with a GLES 3.0 shader (gains, saturation, contrast, lift, deterministic grain), applying the capture rotation. The Film look is pinned by a test against the CPU reference (`FilmMath`) at four sample points, within 4/255; the largest difference on the emulator was 2.7. 4 instrumented tests pass. The plan's code needed no fix; the only change against the plan is the run command (`-Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.develop.GpuDeveloperTest`, since AGP 9.5.0 has no `--tests`), recorded in the plan's `Revisions`. The spec's Testing now says sample pixels instead of a golden image (see its `Revisions`). See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [ADR 0006](docs/adr/0006-develop-on-the-gpu-after-capture.md), [spec](docs/specs/2026-11-24-shoot-one-roll-design.md).
- 2026-11-24: `RollStore` keeps the loaded Roll across app restarts (Film, Format, Exposures used); an unknown Film or an out-of-range count loads as no Roll. 4 instrumented tests pass on the emulator. One change against the plan: AGP 9.5.0's `connectedDebugAndroidTest` has no `--tests` option, so the test is run with `-Pandroid.testInstrumentationRunnerArguments.class=app.cineroll.roll.PrefsRollStoreTest`; recorded in the plan's `Revisions`. See [plan](docs/plans/2026-11-24-shoot-one-roll.md).
- 2026-11-24: Android project scaffold (Kotlin, Gradle, Compose, CameraX) with the `Film` and `Roll` domain: a 35mm Roll holds 36 Exposures and finishes at the 36th. Built on the Gradle 9.6.0 wrapper; 13 unit tests pass. One version change against the plan: Compose BOM `2026.11.01` (Compose 1.13.0) needs compileSdk 37.1 but the machine has platform 37.0, so the BOM is `2026.09.00` (Compose 1.12.1); recorded in the plan's `Revisions`. See [plan](docs/plans/2026-11-24-shoot-one-roll.md), [ADR 0005](docs/adr/0005-native-android.md), [ADR 0002](docs/adr/0002-each-camera-holds-its-own-roll.md).
- 2026-11-24: [Android machine setup guide](docs/android-setup.md) for a first-time Android developer on Ubuntu: JDK 17, Android Studio, SDK, Gradle, emulator or real phone, checks and troubleshooting. Linked from `README.md`; it is the prerequisite for the [shoot-one-Roll plan](docs/plans/2026-11-24-shoot-one-roll.md).
- 2026-11-24: Opened GitHub issues [#1](https://github.com/duytran/cineroll/issues/1) to [#6](https://github.com/duytran/cineroll/issues/6), one per task of the [shoot-one-Roll plan](docs/plans/2026-11-24-shoot-one-roll.md), and wrote the issue numbers next to the task headings, as [ADR 0003](docs/adr/0003-issue-and-pr-per-task.md) requires.
- 2026-11-24: [Plan: shoot one Roll](docs/plans/2026-11-24-shoot-one-roll.md), six tasks from project scaffold to the viewfinder screens. While planning, the [spec](docs/specs/2026-11-24-shoot-one-roll-design.md) was corrected: the UI smoke test said the counter goes `0/36` to `1/36` but the counter shows Exposures left, so it now says `36/36` to `35/36` (see its `Revisions`).
- 2026-11-24: [Spec: shoot one Roll](docs/specs/2026-11-24-shoot-one-roll-design.md), the first slice (one Camera, one Film, 36 Exposures), with [ADR 0005](docs/adr/0005-native-android.md) (native Android, Kotlin and CameraX) and [ADR 0006](docs/adr/0006-develop-on-the-gpu-after-capture.md) (plain viewfinder, develop on the GPU after capture).
- 2026-11-24: [ADR 0004](docs/adr/0004-model-per-workflow-phase.md): brainstorming runs on Opus 5.5 and plan writing on Sonnet 5.5; rule added to `CLAUDE.md` under "Which model to use".
- 2026-11-23: Git repository on `main` with a `.gitignore`, `README.md`, `CLAUDE.md` working rules (changelog, ADRs, git rules), and `docs/specs`, `docs/plans`, `docs/business-rules` folders.
- 2026-11-23: [ADR 0003](docs/adr/0003-issue-and-pr-per-task.md): one GitHub issue and one PR per plan task.
- 2026-11-22: `GLOSSARY.md` with the app's vocabulary: Film, Camera, Format, Lens, Roll, Finished Roll, Rewind, Diptych, Exposure, Contact Sheet.
- 2026-11-22: ADR 0001: save only the developed Exposure, not the original.
- 2026-11-22: ADR 0002: each Camera holds its own Roll.

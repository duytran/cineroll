# Changelog

All notable changes to Cineroll are recorded in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- 2026-11-24: [Plan: shoot one Roll](docs/plans/2026-11-24-shoot-one-roll.md), six tasks from project scaffold to the viewfinder screens. While planning, the [spec](docs/specs/2026-11-24-shoot-one-roll-design.md) was corrected: the UI smoke test said the counter goes `0/36` to `1/36` but the counter shows Exposures left, so it now says `36/36` to `35/36` (see its `Revisions`).
- 2026-11-24: [Spec: shoot one Roll](docs/specs/2026-11-24-shoot-one-roll-design.md), the first slice (one Camera, one Film, 36 Exposures), with [ADR 0005](docs/adr/0005-native-android.md) (native Android, Kotlin and CameraX) and [ADR 0006](docs/adr/0006-develop-on-the-gpu-after-capture.md) (plain viewfinder, develop on the GPU after capture).
- 2026-11-24: [ADR 0004](docs/adr/0004-model-per-workflow-phase.md): brainstorming runs on Opus 5.5 and plan writing on Sonnet 5.5; rule added to `CLAUDE.md` under "Which model to use".
- 2026-11-23: Git repository on `main` with a `.gitignore`, `README.md`, `CLAUDE.md` working rules (changelog, ADRs, git rules), and `docs/specs`, `docs/plans`, `docs/business-rules` folders.
- 2026-11-23: [ADR 0003](docs/adr/0003-issue-and-pr-per-task.md): one GitHub issue and one PR per plan task.
- 2026-11-22: `GLOSSARY.md` with the app's vocabulary: Film, Camera, Format, Lens, Roll, Finished Roll, Rewind, Diptych, Exposure, Contact Sheet.
- 2026-11-22: ADR 0001: save only the developed Exposure, not the original.
- 2026-11-22: ADR 0002: each Camera holds its own Roll.

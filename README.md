# Cineroll

A camera app that makes phone photos look like they were shot on film. You load a Film into a Camera and shoot a Roll, and the look is baked into each Exposure when you take it: no editing afterwards, just like real film.

## Status

Design stage. No app code yet; the domain language and first decisions are written down.

## Docs

- [`GLOSSARY.md`](GLOSSARY.md) — the app's vocabulary.
- [`docs/adr/`](docs/adr/) — design decisions.
- [`docs/specs/`](docs/specs/) — design specs.
- [`docs/plans/`](docs/plans/) — implementation plans.
- [`CHANGELOG.md`](CHANGELOG.md) — every change, newest first.
- [`CLAUDE.md`](CLAUDE.md) — working rules for Claude and other agents.

## Git rules

- Branch from `main`; commit docs together with the code they describe.
- Plans: one GitHub issue per task, one branch and PR per task that closes it (`task/<issue>-<slug>`, stacked when a task depends on the previous one). See [ADR-0003](docs/adr/0003-issue-and-pr-per-task.md).
- Every change needs a `CHANGELOG.md` entry (see `CLAUDE.md`).
- Never add a `Co-Authored-By:` line or any attribution trailer to a commit message.

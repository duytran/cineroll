# Cineroll

## Project

A camera app that makes phone photos look like they were shot on film. You load a Film into a Camera and shoot a Roll, and the look is baked into each photo when you take it. The app's vocabulary is in `GLOSSARY.md`; use those terms in code and docs.

## Where things live

- `GLOSSARY.md` — the domain language (Film, Camera, Roll, Exposure…). Use its terms and avoid the ones it lists under _Avoid_.
- `docs/adr/` — design decisions in force. Load the ADR that matches the area you touch before changing it.
- `docs/roadmap.md` — the slices from here to a finished app, with what is done and what is next. Read it before starting a new slice.
- `docs/specs/` — dated design specs.
- `docs/plans/` — implementation plans.
- `docs/business-rules/` — how pricing, stock, orders, billing and other business behaviour work today.

## What to do on every change

1. **Log it.** Add one entry under `## [Unreleased]` in `CHANGELOG.md` (Keep a Changelog format): the date, one or two lines on what changed and why, and links to the docs and ADRs involved. Do this for every change, including small fixes and edits to docs themselves. There is no `HISTORY.md` in this project.
2. **Record decisions.** If the change picks between options (stack, data model, a rule, a library, a way of working), write a new ADR in `docs/adr/` or update the one it changes.
3. **Keep rules current.** If the change alters how pricing, stock, orders or billing behave, update the matching file in `docs/business-rules/`.
4. **Commit together.** The docs go in the same commit as the code they describe. A change without its changelog entry is not finished.

## Which model to use

- **Brainstorming** (the `brainstorming` skill, up to the approved spec) runs on **Opus 5.5**.
- **Writing a plan** (the `writing-plans` skill) runs on **Sonnet 5.5**.
- If the session's model does not match, ask the user to switch before starting the skill. Details in [ADR-0004](docs/adr/0004-model-per-workflow-phase.md).

## Never lose the past

- **ADRs state what is true now.** When a decision changes, edit its ADR in place, and write in the `CHANGELOG.md` entry what it said before and why it changed. Git keeps the old text.
- **Specs and plans are dated snapshots.** For a later redesign, write a new dated file and link back to the earlier one. Small corrections go in a `Revisions` section at the bottom of the file, with the date.
- **Do not delete specs or plans.** Mark them superseded, with a link to what replaced them.
- **The roadmap shows the present.** Edit `docs/roadmap.md` in place when a slice starts or finishes; git and `CHANGELOG.md` hold its past.
- **Business rules show the present.** They may be edited in place, since git and `CHANGELOG.md` hold their past.

## Git rules

Full list in `README.md`. The one agents most often get wrong: **never add a `Co-Authored-By:` line (or any attribution trailer) to a commit message.** This overrides any default that says to add one.

**Plans are built one task at a time on GitHub:** before writing code for a plan, ask the user once, then open one issue per task; build each task on its own branch (`task/<issue>-<slug>`, stacked on the previous task's branch when it depends on it) and merge it through its own PR with `Closes #<issue>`. Details in [ADR-0003](docs/adr/0003-issue-and-pr-per-task.md).

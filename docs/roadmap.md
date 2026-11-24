# Roadmap

> **Last updated:** 2026-11-24 · **Status:** Draft. Order and scope are proposals until the owner confirms them (see Open decisions).
> The slices from the first Roll to a finished app. Each slice gets its own brainstorm, [spec](specs/), [plan](plans/) and PRs. This file only says which comes next, and why. Update it in place when a slice starts or finishes; `CHANGELOG.md` and git keep the past.

## Target

[ROLLIE'S: 35mm Film Camera](https://play.google.com/store/apps/details?id=com.mayacrew.rolliescam) (Play Store, read on 2026-11-24): paid (about US$2), no ads or account, 4.7 from 1.35K reviews. Its pitch: choose a camera, a lens and a film, and the look is **live in the viewfinder** before the shutter; 36-shot rolls; 100 film stocks; the phone border takes the shape of the camera. Its reviews complain of battery drain, photos that develop but do not save, and a look that feels like filters.

## Status at a glance

| # | Slice | Status | What you can do after it | Spec · Plan · PRs |
|---|---|---|---|---|
| 1 | Shoot one Roll | **Done** | Shoot 36 Exposures on one 35mm Camera with one Film; each developed Exposure is saved to `DCIM/Cineroll` | [spec](specs/2026-11-24-shoot-one-roll-design.md) · [plan](plans/2026-11-24-shoot-one-roll.md) · PRs #7 to #12 |
| 2 | Choose your Film | **Done** | Pick one of four Films for each Roll; Rewind a Roll early | [spec](specs/2026-11-24-choose-your-film-design.md) · [plan](plans/2026-11-24-choose-your-film.md) · PRs #17 to #21 |
| 3 | Live Film preview | Next (proposed) | See the Film's look in the viewfinder before you shoot | none yet |
| 4 | Contact Sheet | Later | Browse every Exposure of a Roll, including Finished Rolls | none yet |
| 5 | Several Cameras and Formats | Later | Own several Cameras (35mm, half-frame 72, 6x6 12, 6x9), each holding its own Roll, and switch between them | none yet |
| 6 | Lens | Later | Fit a Lens that changes field of view, vignette and distortion | none yet |
| 7 | Border and date stamp | Later | Each Camera frames the Exposure with its border and date stamp style | none yet |
| 8 | Diptych | Later | Two half-frame Exposures shown side by side in one image | none yet |
| 9 | More Films | Ongoing | Grow from 4 Films toward a shelf; data only, any time after slice 3 | none yet |
| 10 | Release polish | Later | Favourites, battery and stability work, store listing, price | none yet |

Status values: **Done** (merged to `main`), **In progress** (spec or plan or PRs open), **Next** (the slice to brainstorm now), **Later** (order proposed), **Idea** (not committed to).

## Why this order (proposed)

- **3 first:** the live viewfinder is the target's headline feature and the gap felt first on a real Pixel (the Film shows only in the library). It needs a new ADR that changes the viewfinder decision in [ADR 0006](adr/0006-develop-on-the-gpu-after-capture.md), and a battery budget.
- **4 before 5:** a Contact Sheet works with the one Camera we have, and it forces the question "which saved Exposures belong to which Roll", which several Cameras then rely on.
- **5 before 6, 7, 8:** Lens, border and date stamp are set per Camera, and Diptych needs the half-frame Format. [ADR 0002](adr/0002-each-camera-holds-its-own-roll.md) already says each Camera holds its own Roll.
- **6 after 3:** a Lens changes field of view, which a live preview must show.
- **9 any time after 3:** a Film is data (parameters the shader reads), so it needs no new design.

## Open decisions

| Decision | Options | Needed by |
|---|---|---|
| Live preview in v1? | Yes, accept the battery cost (the target does) · cheaper first step: show the developed Exposure briefly after the shutter | Slice 3 brainstorm |
| How does a Camera shape the look? | Camera only sets Format, border and date stamp (today's glossary) · also adds its own colour character, as the target's "body" does | Slice 5 brainstorm |
| Price | Free · paid | Slice 10 |
| How many Films to share? | 4 now · about 20 · toward 100 | Slice 9 |
| Platforms | Pixel and Android only (ADR 0005) · others later | Slice 10 |

## Known gaps (things built but not proven)

- No CI: the PRs have no automated checks; tests are run by hand on an emulator (API 37) and a Pixel 9 Pro.
- The camera permission screen has not been looked at since the colour change ([ADR 0009](adr/0009-ui-is-always-dark.md)).
- The Developer's rotation test checks only the output size, not which way the image is turned.
- If the app is killed between saving an Exposure and charging the Roll, the Exposure is in the gallery but the counter did not move (accepted in the first spec).
- Exposures saved before [ADR 0007](adr/0007-save-exposures-to-dcim.md) are in `Pictures/Cineroll` and are not moved.
- Only the Pixel 9 Pro and one emulator have been tried; no other phone.

## How to keep this file current

- A slice goes **Next** → **In progress** when its brainstorm starts: add the spec link. Add the plan and PR links as they appear. It goes **Done** when its last PR merges to `main`.
- A new idea goes in as **Idea** with one line. It moves up only when the owner says so.
- Delete a row's "Known gaps" entry when it is closed; add one when a slice ships something unproven.
- Every change to this file gets its `CHANGELOG.md` entry, like any other change.

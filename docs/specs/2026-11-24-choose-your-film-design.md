# Choose your Film: design

> **Date:** 2026-11-24 · **Status:** Approved
> Second slice of Cineroll: pick one of four Films when loading a Roll, and Rewind a Roll to end it early. Follows [Shoot one Roll](2026-11-24-shoot-one-roll-design.md).

## Scope

**In:** four built-in Films, a Film picker screen, Rewind, the Film name in the viewfinder.

**Out (later slices):** new shader parameters (split toning, highlight roll-off), Film previews or sample images in the picker, user-made Films, several Cameras, Contact Sheet, live Film preview.

## Decisions

- Screens follow the Roll state in `CineRollApp`; no navigation library: [ADR 0008](../adr/0008-screen-follows-roll-state.md).
- New Films use only the existing `Film` parameters (RGB gains, saturation, contrast, lift, grain). No shader change: a Film stays data, per [ADR 0006](../adr/0006-develop-on-the-gpu-after-capture.md).
- The picker shows whenever no Roll is loaded: first launch, and after "Load new Roll".
- A Roll keeps its Film until it is finished. To change Film mid-Roll, you Rewind and load a new Roll.

Assumptions (veto-able):

- Film names are invented, not real film brands.
- The picker has no cancel: with no Roll loaded there is nothing to go back to.
- After Rewind, the Finished Roll screen shows first, the same as when the 36th Exposure is used.
- Every new Roll is 35mm (36 Exposures); the Format does not change in this slice.

## Films

| Film | Description (shown in the picker) | Character |
|---|---|---|
| Everyday 400 | Warm and easy, with soft blacks. | Unchanged from slice 1. |
| Daylight 100 | Cool and clean, with fine grain. | Blue-leaning gains, slight extra contrast and saturation, little lift, low grain. |
| Faded 800 | Muted colour, lifted blacks, heavy grain. | Saturation below 1, high lift, high grain. |
| Mono 400 | Black and white, high contrast. | Saturation 0, high contrast, medium grain. |

Exact parameter values are set in the plan, inside the ranges `Film` already checks.

## Components

| Unit | Change |
|---|---|
| `Film` | New `description` field (not blank). |
| `Films` | Adds Daylight 100, Faded 800 and Mono 400; `all` lists the four in the order above. |
| `Roll` | New `ended` flag (default `false`) and `rewind()`. `isFinished` is `used >= total \|\| ended`. `rewind()` on a Finished Roll throws. |
| `RollStore` | Saves and loads `ended`; a missing value loads as `false`, so a Roll saved by slice 1 still loads. New `clear()` removes the saved Roll. |
| `Shutter` | `roll` becomes `StateFlow<Roll?>`. `loadNewRoll()` is replaced by `load(film)` (new 35mm Roll, saved and published) and `clear()` (drops the Finished Roll, publishes `null`). New `rewind()`, refused while an Exposure is developing. Shooting with no Roll loaded does nothing. On start, no saved Roll means `null`, not a default Roll. |

Screens, chosen in `CineRollApp` from the Roll:

| Roll | Screen |
|---|---|
| `null` | **Film picker:** a list of the four Films, each with name and description. Tapping one calls `load(film)`. |
| Finished | **Roll finished:** as in slice 1; "Load new Roll" calls `clear()`. |
| Loaded | **Viewfinder:** as in slice 1, plus the Film name next to the counter and a Rewind button. Rewind is disabled while an Exposure is developing. |

Rewind opens a confirm dialog: "Rewind? The 23 Exposures left will be lost." with Rewind and Cancel. The camera permission screen still comes before all of these.

## Data flow

1. First launch: nothing is saved, `roll` is `null`, the picker shows.
2. The user taps a Film. `Shutter.load(film)` creates `Roll(film, 35mm)`, saves it, publishes it. The viewfinder shows "Mono 400 · 36/36".
3. Shooting works as in slice 1.
4. The Roll finishes in one of two ways:
   - Rewind, confirm: `Shutter.rewind()` sets `ended`, saves, publishes. Unused Exposures are lost.
   - The 36th Exposure: `expose()` finishes the Roll, as in slice 1.
5. The Finished Roll screen shows. "Load new Roll" calls `Shutter.clear()`: the saved Roll is removed and the picker shows.

## Error handling

Store failures follow slice 1's rule: log and carry on with the in-memory state.

| Failure | Result |
|---|---|
| Save fails in `load(film)` | The Roll is loaded in memory. Logged. If the app restarts before the next successful save, the picker shows again. |
| Save fails in `rewind()` | The Roll is finished in memory. Logged. After a restart it comes back loaded, with the same count. |
| `clear()` fails | The picker shows anyway. Logged. After a restart, the old Finished Roll screen comes back. |
| Saved Film no longer exists | Loads as no Roll; the picker shows (existing `RollStore` behaviour). |
| Rewind tapped while developing | The button is disabled; `rewind()` also refuses if called. |
| Shutter tapped with no Roll | Cannot happen from the UI; `Shutter` does nothing and does not touch the camera. |

## Testing

| Unit | How |
|---|---|
| `Roll` | Unit: `rewind()` finishes the Roll and keeps `used`; `rewind()` on a Finished Roll throws; `expose()` after Rewind throws. |
| `Films` | Unit: all four are valid, names unique, descriptions not blank. |
| `RollStore` | Instrumented: `ended` survives save and reload; saved data without `ended` loads as `false`; `clear()` makes `load()` return `null`. |
| `Shutter` | Unit with fakes: `load` publishes and saves; `rewind` charges no Exposure, saves `ended`, is refused while busy; shooting with no Roll does nothing and never calls the camera; `clear` publishes `null`; a store failure in `load`, `rewind` or `clear` still updates the in-memory Roll. |
| `Developer` | Instrumented: one extra sample-pixel check for Mono 400 against `FilmMath`, grain off, and R = G = B at the sample points. |
| UI | Smoke test: fresh install shows the picker; pick Mono 400; viewfinder shows "Mono 400" and `36/36`; shoot once, `35/36`; Rewind and confirm; Finished Roll screen; "Load new Roll" shows the picker. |

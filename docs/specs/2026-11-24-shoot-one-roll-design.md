# Shoot one Roll: design

> **Date:** 2026-11-24 · **Status:** Approved
> First slice of Cineroll: one Camera, one Film, shoot a Roll of 36 Exposures and save each developed Exposure to the gallery.

## Scope

**In:** one 35mm Camera, one built-in Film, a plain viewfinder, an Exposure counter, saving developed Exposures to the gallery, a Roll that survives closing the app, a Finished Roll screen with "Load new Roll".

**Out (later slices):** several Cameras, switching Cameras, Rewind, Contact Sheet, Lens, Diptych, border and date stamp, live Film preview, more Films, a Film picker.

## Decisions

- Native Android (Kotlin, CameraX), Pixel phones first: [ADR 0005](../adr/0005-native-android.md).
- Capture first, then develop on the GPU. The viewfinder is plain: [ADR 0006](../adr/0006-develop-on-the-gpu-after-capture.md).
- Only the developed Exposure is saved: [ADR 0001](../adr/0001-save-only-the-developed-exposure.md).
- A Roll belongs to one Camera and its Exposure count comes from the Format (35mm = 36): [ADR 0002](../adr/0002-each-camera-holds-its-own-roll.md).

Assumptions (veto-able): each developed Exposure is saved to the gallery under a `Cineroll` album; the counter always shows Exposures left as `23/36`; at 36 the Roll becomes a Finished Roll and the app asks for a new one; the loaded Roll survives closing the app; minimum Android version is chosen in the plan.

## Components

| Unit | Does | Depends on |
|---|---|---|
| `Film` | Data class: name, tone curve, colour shift, grain amount. One built-in instance. | nothing |
| `Roll` | Film, Format (35mm, 36 Exposures), Exposures used, state (loaded or finished). `expose()` uses one Exposure and finishes the Roll at 36. | `Film` |
| `RollStore` | Saves and loads the one loaded Roll. | `Roll` |
| `Developer` | Takes a captured JPEG and a `Film`, returns the developed JPEG. The GPU shader is hidden behind this interface. | `Film` |
| `Shutter` | The shoot flow: capture with CameraX, develop, save to the gallery, record the Exposure on the Roll. | `Roll`, `RollStore`, `Developer` |

Two screens:

- **Viewfinder:** plain CameraX preview, shutter button, `23/36` counter. The shutter is disabled while an Exposure is being developed.
- **Roll finished:** shown for a Finished Roll, with a "Load new Roll" button.

## Data flow

1. The user taps the shutter; the button disables at once.
2. `Shutter` checks the Roll is loaded; if not, it stops.
3. CameraX captures a full-resolution JPEG into memory.
4. `Developer` applies the Film and returns the developed JPEG.
5. `Shutter` writes the developed JPEG to the gallery through `MediaStore`.
6. Only after the write succeeds, `Roll.expose()` runs and `RollStore` saves the new count.
7. The counter updates, the shutter re-enables, and the captured original is dropped.

**The Roll is charged after the save, never before.** A failure in steps 3 to 5 uses no Exposure. If the app is killed between steps 5 and 6, the Exposure is in the gallery but the counter did not move: one free Exposure, accepted and not repaired in this slice. The 36th Exposure follows the same flow; `expose()` then finishes the Roll.

## Error handling

Every failure leaves the Roll untouched; the shutter re-enables and a short message appears.

| Failure | Result |
|---|---|
| Camera permission denied | Screen explaining why, with a button to open settings. No viewfinder. |
| Capture fails | "Couldn't take the photo, try again". No Exposure used. |
| Develop fails (shader error, out of memory) | Same message. No Exposure used. |
| Gallery write fails | "Couldn't save the photo, check storage". No Exposure used. |
| `RollStore` fails after a successful save | Logged; carry on. Same free-Exposure case as above. |
| No saved Roll on first launch | Create one 35mm Roll loaded with the built-in Film. |
| App closed mid-develop | Nothing saved, no Exposure used. The shot is lost; accepted. |

Out of scope: retries, a queue of pending Exposures, crash reporting.

## Testing

| Unit | How |
|---|---|
| `Roll` | Unit tests, no Android: `expose()` counts up, finishes at 36, refuses once finished; a new Roll starts at 0 of 36. |
| `Film` | Unit test that the built-in Film's parameters are in range. |
| `RollStore` | Instrumented: save, reload, same count and state; first launch with nothing saved. |
| `Developer` | Golden image on a device or emulator: a fixed input through the built-in Film matches a stored output within a small tolerance. |
| `Shutter` | Unit tests with fakes for camera, `Developer`, gallery and `RollStore`: success charges the Roll once; failure at capture, develop or save charges nothing; a failed `RollStore` write after a good save does not block the next shot; two quick taps take one Exposure. |
| UI | One smoke test: grant permission, tap the shutter, counter goes `0/36` to `1/36`. |

CameraX and `MediaStore` themselves are not unit tested.

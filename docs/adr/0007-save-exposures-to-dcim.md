---
status: accepted
---

# Save Exposures to DCIM/Cineroll

Each developed Exposure is saved in the phone's `DCIM/Cineroll` folder. We chose this because `DCIM` is where Android keeps camera photos, and Google Photos on a Pixel shows photos from there in its main views. Photos saved under `Pictures/Cineroll` showed only under Library, Device folders, and not on the front of Library.

## Considered Options

- **Save to `Pictures/Cineroll`.** The first version did this. It saves and indexes correctly, but Google Photos treated it as an ordinary app folder, so the Exposures were hard to find. Rejected.
- **Ask the user to turn on "Back up" for the folder in Google Photos.** It would make them appear, but it uploads every Exposure to the user's Google account. That is the user's own choice to make, not a thing an app should depend on. Rejected.

## Consequences

- The album is still named `Cineroll`; only its parent folder changed from `Pictures` to `DCIM`.
- Exposures saved before this change are in `Pictures/Cineroll` and stay there; the app does not move them.
- The date taken is written into each file's EXIF (see the CHANGELOG entry for 2026-11-24, "Saved Exposures had no date taken"), because gallery apps sort by it.
- Checked by hand on a Pixel 9 Pro with Google Photos. Other gallery apps were not tried.

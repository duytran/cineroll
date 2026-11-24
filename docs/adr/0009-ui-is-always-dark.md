---
status: accepted
---

# The UI is always dark

Every screen uses Material's dark colour scheme on a `Surface`, whatever the phone's light or dark setting. We chose this because the manifest's window theme (`Theme.Material.NoActionBar`) is dark, while Compose's `MaterialTheme` defaults to light colours, so screens with no background of their own (the Roll finished screen, the Film picker, the camera permission screen) drew black text on a dark window. A viewfinder is also easier to judge against a dark surround, and the status bar icons stay readable.

## Considered Options

- **Follow the phone's setting** (`isSystemInDarkTheme()` picks a light or dark scheme). Rejected for now: the window theme is dark in both settings, so light mode would also need a light window theme and dark status bar icons, for a camera app that is mostly a black viewfinder.
- **Set a background colour on each screen.** Rejected: it fixes the symptom screen by screen, and the next screen added would have the same bug.

## Consequences

- `MainActivity` wraps the app in `MaterialTheme(colorScheme = darkColorScheme())` and a full-size `Surface`; new screens get a readable background and text colour without setting their own.
- The app ignores the phone's light setting. Following it would be a new decision (see the first option).

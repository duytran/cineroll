---
status: accepted
---

# The screen follows the Roll state, with no navigation library

`CineRollApp` picks the screen from the loaded Roll: no Roll shows the Film picker, a Finished Roll shows the Roll finished screen, and a loaded Roll shows the viewfinder. The camera permission screen comes before all three. There are no routes and no back stack. We chose this because every screen in the app so far is a direct result of the Roll's state, so a single `when` cannot get out of step with it, and it is how slice 1 already works.

## Considered Options

- **Navigation Compose with routes.** Gives a back stack and deep links. Rejected for now: the routes would have to be kept in step with the Roll by hand, and it adds a dependency before any screen needs it. Revisit when the Contact Sheet or Camera switching adds screens you browse to, not ones the Roll forces.
- **Film picker as a sheet over the Roll finished screen.** Smallest UI change. Rejected because it does not cover first launch, where there is no Finished Roll to sit on.

## Consequences

- `Shutter.roll` is `Roll?`; `null` means no Roll is loaded, and that is what shows the picker.
- The system back button leaves the app from any of these screens; there is nothing to go back to.
- Screens that are not driven by the Roll (a Contact Sheet, settings) will need a new decision.

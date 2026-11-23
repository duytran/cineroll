---
status: accepted
---

# Save only the developed Exposure, not the original

When you take an Exposure, the Film is baked into it and only that finished JPEG is saved. The clean original from the phone camera is thrown away. We chose this because committing to a Film before you shoot is the whole point of a film camera: you can't change your mind later, and that is part of the appeal.

## Considered Options

- **Keep the original privately inside the app.** This would let you re-develop a photo with a different Film later, and it would make a future editor mode easier. We rejected it for v1 because it undermines committing to a Film, and it roughly doubles storage.
- **Save both copies to the gallery.** Rejected because it fills the gallery with duplicate, unedited photos.

## Consequences

- Every Exposure taken under this decision is permanent. If a later version starts keeping originals, it only helps photos taken after that change.
- A future "import and edit" mode would work on photos from the gallery, not on Cineroll's own originals.

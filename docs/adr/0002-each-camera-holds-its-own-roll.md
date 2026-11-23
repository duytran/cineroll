---
status: accepted
---

# Each Camera holds its own Roll

Each Camera holds at most one loaded Roll. Switching Cameras pauses one Roll and resumes the other, like owning several real cameras. The Exposure count comes from the Camera's Format: 36 for 35mm, 72 for half-frame, 12 for 6x6. We chose this because the most-cited complaint about ROLLIE'S is that switching cameras resets the roll, and that its shot counts don't match the film format.

## Considered Options

- **One active Roll for the whole app.** Switching Camera would either finish the Roll or force a Rewind. Rejected because it punishes trying out another Camera.
- **Rolls independent of the Camera.** You could change Camera mid-Roll. Rejected because the Format, and so the Exposure count, would stop making sense mid-Roll.

## Consequences

- A Roll is linked to both a Film and a Camera, and several Rolls can be loaded at the same time (one per Camera).
- If Lens swapping comes in v2, it changes nothing for the Roll: a Lens change doesn't touch the Roll.

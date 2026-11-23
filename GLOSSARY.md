# Cineroll

A camera app that makes phone photos look like they were shot on film. You load a Film into a Camera and shoot a Roll, and the look is baked into each photo when you take it.

## Language

**Film**:
A named look (its colour, tone and grain) that is applied to every Exposure on a Roll.
_Avoid_: Filter, preset, film simulation, LUT

**Camera**:
A virtual camera body. It sets the Format, the border and the date stamp style, and it holds at most one loaded Roll.
_Avoid_: Mode, style

**Format**:
The frame shape and size of a Camera (35mm, half-frame, 6x6, 6x9). The Format sets how many Exposures a Roll holds.
_Avoid_: Aspect ratio, crop

**Lens**:
A virtual lens fitted to a Camera. It sets the field of view and optical character, such as vignette and distortion.
_Avoid_: Zoom, focal setting

**Roll**:
One Film loaded into one Camera, with a fixed number of Exposures set by the Camera's Format. A Roll is either loaded or finished.
_Avoid_: Session, album, batch

**Finished Roll**:
A Roll that can no longer be shot, because every Exposure is used or it was ended early. It can still be browsed as a Contact Sheet.
_Avoid_: Archived roll, old roll

**Rewind**:
Ending a loaded Roll early. The unused Exposures are lost and it becomes a Finished Roll.
_Avoid_: Unload, eject, reset

**Diptych**:
Two consecutive half-frame Exposures shown side by side in one image, the way they sit on a real half-frame print.
_Avoid_: Pair, collage, split

**Contact Sheet**:
A grid view of every Exposure on one Roll, in the order they were taken.
_Avoid_: Gallery, album

**Exposure**:
One photo taken on a Roll. It uses up one of the Roll's shots.
_Avoid_: Shot, photo, picture, frame

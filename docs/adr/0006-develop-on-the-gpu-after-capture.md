---
status: accepted
---

# Develop each Exposure on the GPU after capture, with a plain viewfinder

The viewfinder shows the plain camera image. When the shutter is pressed, CameraX captures a full-resolution JPEG, a GPU shader applies the Film (tone curve, colour shift, grain), and the developed JPEG is saved. A Film is data (parameters the shader reads), not shader code. We chose this because it keeps the viewfinder simple, fits ADR 0001's idea of committing to a Film before you shoot, and is fast enough at full resolution.

## Considered Options

- **Develop on the CPU.** Simplest to build and test. Rejected because per-pixel work with grain on a 12MP+ image takes seconds and a lot of memory, so it would need rewriting later.
- **Hook into CameraX's effects pipeline.** Most integrated, and would make a live preview easy. Rejected for v1 as the most complex and least documented option, and overkill with a plain viewfinder.
- **Live Film preview.** Rejected for v1: it needs a second real-time GPU path whose result must match the saved JPEG.

## Consequences

- The `Developer` unit hides all GPU code behind a small interface, and its output is pinned by a golden-image test.
- Adding a Film means adding data, not shader code.
- Adding a live preview later is a new decision; it would not change how saved Exposures are developed.

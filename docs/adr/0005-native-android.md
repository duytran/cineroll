---
status: accepted
---

# Build Cineroll as a native Android app

Cineroll is a native Android app written in Kotlin with CameraX, on Pixel phones first. We chose this because the app's whole value is the quality and speed of the developed Exposure, and native gives the most control over the camera and the GPU.

## Considered Options

- **Flutter.** One codebase for Android and iOS. Rejected for v1 because camera and shader work go through plugins or platform code, so there is more glue and less control, and iOS is not a goal yet.
- **React Native.** Rejected because real-time-quality image processing is hardest to get right behind a JS bridge.

## Consequences

- Android only for now. An iOS version would be a separate codebase.
- Image processing can use the GPU directly (see [ADR 0006](0006-develop-on-the-gpu-after-capture.md)).

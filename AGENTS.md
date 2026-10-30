# AGENTS.md

## What this is

Android photo editor (`com.aliahad.photoeffects`, single `:app` module, Kotlin + Compose,
minSdk 36 / target 37) that reimplements PREQUEL-style effects: 25 presets across six families
(Dusty, Halation, Yesterday, Sensual, Kuji, Focal). Every effect exists twice — an AGSL
`RuntimeShader` for the live preview and a deterministic CPU mirror used for thumbnails, JVM
tests and the Save export. Saving mirrors PREQUEL's export sheet: resolution tiers (never above
the source), Low/Medium/High JPEG (40/75/95), and Override/Keep metadata.

Future goal: close the remaining gap to PREQUEL (`com.prequel.app` 2.5.0) while staying a
self-contained, sideloadable APK.

## Build and test

- `./gradlew :app:assembleDebug --offline`
- `./gradlew :app:testDebugUnitTest --offline` — run for every effect or export change.

Instrumented tests exist but cannot run on the test device: its ROM rejects test-APK installs
(`INSTALL_FAILED_USER_RESTRICTED`). Verify behavior through the app UI instead.

## Invariants

- Change a GPU shader and its CPU mirror together; exports and tests use the CPU path, so drift
  silently degrades saved photos.
- Every `PhotoEffect` must declare `exportBytesPerPixel`; `ExportBudget` caps the planned export
  size by heap. Never bypass the budget or export the editor preview bitmap — `PhotoSaver`
  re-decodes the source at the planned size.
- `docs/` holds reverse-engineering evidence and is gitignored by design. Keep the GitHub repo
  (`aliahadmd/Photoeffects`) private and app-source-only.
- Do not commit, push, or create releases unless the user explicitly asks.

## Device checks

adb lives at `/Volumes/essd/android/sdk/platform-tools` (not on PATH). The phone is 1440×3200;
exports land in `/sdcard/Pictures/PhotoEffects`. Use `adb exec-out screencap -p` plus
`uiautomator dump` for coordinates, and pull exported files to inspect dimensions and EXIF.

# AGENTS.md

## What this is

**LiEffects** Android photo editor (`com.aliahad.photoeffects` kept for in-place upgrades, single `:app` module, Kotlin + Compose,
minSdk 36 / target 37) with 35 presets across ten effect families
(Motes, Lumen, Daymark, Velour, Paperlight, Prism, Nocturne, Marquee, Solstice, Screenprint), 40 catalogue-driven
filters, and an Adjust layer with 8 categories / 59 controls (Light, Color, HSL, Details, Stylize,
Refocus, Optical, Blur). Every look exists twice — an AGSL `RuntimeShader` for the live preview and a deterministic
CPU mirror used for thumbnails, JVM tests and the Save export. The render order is
Filter → Adjust → Effect → Canvas. Saving offers resolution tiers (never above the source),
Low/Medium/High JPEG (40/75/95), and Override/Keep metadata.

Keep the app self-contained and sideloadable, with its own catalogue names and consistent
live preview, thumbnails and export.

## Build and test

- `./gradlew :app:assembleDebug --offline`
- `./gradlew :app:testDebugUnitTest --offline` — run for every effect or export change.

Instrumented tests exist but cannot run on the test device: its ROM rejects test-APK installs
(`INSTALL_FAILED_USER_RESTRICTED`). Verify behavior through the app UI instead.

## Invariants

- Change a GPU shader and its CPU mirror together; exports and tests use the CPU path, so drift
  silently degrades saved photos. Adjust controls share one stage and one plan; keep
  `AdjustParams`, `AdjustPlan`, the CPU passes and the AGSL passes in sync.
- Keep Filter first. Filter shaders bind `uSource`/`uOriginal` only when they are stage 0;
  Adjust is stage 1 and Effect is stage 2. Adjust has no master intensity (composed at 100).
  Canvas applies geometry after the complete look, including frames and date overlays.
- Catalogue IDs are saved in sessions and Undo history. When renaming a preset or family,
  update the migration in `LegacyCatalogueNames` and its saved-session tests.
- Every `PhotoEffect` must declare `exportBytesPerPixel`; `ExportBudget` caps the planned export
  size by heap. Never bypass the budget or export the editor preview bitmap — `PhotoSaver`
  re-decodes the source at the planned size. Adjust's value is plan-dependent (24/36/56/68
  B/px) and composition adds one intermediate allowance per extra stage.
- Adjust noise must use bounded `FilterGrain`-style hashing, not per-pixel trigonometry; the
  spatial passes must use the same taps/radii in CPU and AGSL.
- The bottom panel is progressive (`PanelStage.Categories → Items → Edit`). Keep only the
  current stage mounted, preserve the header/bottom back path, and let the photo reclaim the
  animated panel height. For Effects/Filters, a single tap auditions a preset and stays in
  Items; tapping the selected preset again (or the header Edit action) opens Edit. Adjust
  control selection opens Edit directly.
- `docs/` holds local validation artifacts and is gitignored by design. Keep the GitHub repo
  (`aliahadmd/Photoeffects`) private and app-source-only.
- Do not commit, push, or create releases unless the user explicitly asks.

## Device checks

adb lives at `/Volumes/essd/android/sdk/platform-tools` (not on PATH). The phone is 1440×3200;
exports land in `/sdcard/Pictures/PhotoEffects`. Use `adb exec-out screencap -p` plus
`uiautomator dump` for coordinates, and pull exported files to inspect dimensions and EXIF.

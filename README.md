# Photo effects

An Android photo editor with a **PREQUEL-style Effects tab** implementing **six effect
families** — **5 "dusty" effects** (`Dusty #1 … #5`), **3 HALATION variants**
(`Halation #1 … #3`), **1 YESTERDAY preset** (warm faded film + dot-matrix date stamp),
**5 SENSUAL variants** (`Sensual #1 … #5`), **5 KUJI variants** (`Kuji #1 … #5`, paper-framed
prints) and **6 FOCAL variants** (`Focal #1 … #6`, fitted colour LUTs + #1's portrait soft
focus) — built from a reverse-engineering study of the PREQUEL Android app (com.prequel.app
2.5.0).

* Kotlin + Jetpack Compose, minSdk 36, targetSdk 37.
* Each family has a GPU AGSL `RuntimeShader` preview **and** a deterministic CPU mirror
  (thumbnails / unit tests / Save export).
* Features: photo picker + bundled demo photo, live-rendered effect thumbnails, 0–100
  intensity slider, press-and-hold before/after, undo, save to `Pictures/PhotoEffects`.

> The PREQUEL reverse-engineering research and verification evidence are not published in this
> repository, so `docs/` links below will not resolve here.

## Build & run

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aliahad.photoeffects/.MainActivity
```

Tests: `./gradlew :app:testDebugUnitTest --offline` (53 JVM tests, including dusty golden
byte-identity and per-effect fitted-target checks).

## Where things are

| Path | What |
|------|------|
| `app/src/main/java/com/aliahad/photoeffects/effect/EffectRegistry.kt` | shelves + registry |
| `app/src/main/java/com/aliahad/photoeffects/effect/DustyFamily.kt` | the 5 dusty presets — retune here |
| `app/src/main/java/com/aliahad/photoeffects/effect/HalationFamily.kt` | the 3 halation presets — retune here |
| `app/src/main/java/com/aliahad/photoeffects/effect/YesterdayFamily.kt` | the single YESTERDAY preset (all fitted constants) — retune here |
| `app/src/main/java/com/aliahad/photoeffects/effect/SensualFamily.kt` | the 5 sensual presets (fitted constants) — retune here |
| `app/src/main/java/com/aliahad/photoeffects/effect/KujiFamily.kt` | the 5 kuji presets (fitted constants + frame asset per variant) — retune here |
| `app/src/main/java/com/aliahad/photoeffects/effect/FocalFamily.kt` | the 6 focal presets (LUT index + #1 soft focus) — retune here |
| `app/src/main/java/com/aliahad/photoeffects/effect/*Agsl.kt` | per-family GPU shaders |
| `app/src/main/java/com/aliahad/photoeffects/effect/DotMatrixDate.kt` | dot-matrix date stamp generator (shared by GPU overlay + CPU export) |
| `app/src/main/java/com/aliahad/photoeffects/effect/FilmLook.kt` | cubic tone curves / gain-offset helpers |
| `app/src/main/java/com/aliahad/photoeffects/effect/cpu/` | deterministic CPU mirrors (thumbnails/tests/export) |
| `app/src/main/java/com/aliahad/photoeffects/ui/editor/` | editor UI + Effects panel |
| `app/src/main/assets/blurmask.jpg` | PREQUEL's recovered masked-blur mask (SENSUAL) |
| `app/src/main/assets/kuji_frame_{1..5}.png` | PREQUEL's recovered KUJI paper-frame overlays |
| `app/src/main/assets/focal_luts.png` | the six fitted FOCAL 33³ colour LUTs (594×396 atlas, generated) |
| `app/src/main/assets/demo_photo.jpg` | bundled demo photo (also the PREQUEL comparison source) |
| `docs/prequel-research/README.md` | **evidence report**: how PREQUEL's Effects work + our implementations |
| `docs/prequel-research/device*/` | live device investigation (screenshots, dumps, findings) |
| `docs/prequel-research/static*/` | APK static analysis (recovered recipes, shaders, citations) |
| `docs/prequel-research/spec/` | implementation specs (dusty, halation, yesterday, sensual, kuji, focal) |
| `docs/prequel-research/app*/` | app screenshots/notes/verification evidence |

## The effect families

| Family | Cards | Look | Fidelity vs PREQUEL (same-photo) |
|--------|-------|------|----------------------------------|
| DUSTY | 5 | film grades + procedural dust flecks, grain, scratches, vignette; PREQUEL casts teal / blue / olive / neutral-warm / warm-brown | **PASS** — per-variant grades fitted to PREQUEL's exports (means within 2.3 levels) |
| HALATION | 3 | red-orange / cool cyan / blue-violet halation fringe around highlights (edge-gated additive bloom) | **PASS** — fringe reaches PREQUEL's collar-chroma band; `#3` violet slightly under |
| YESTERDAY | 1 | warm faded camcorder film look, strong vignette, grain, amber dot-matrix date stamp (`DD MM 'YY`, vertical bottom-left) | **PASS** |
| SENSUAL | 5 | dreamy soft-focus + masked variable blur + fitted warm/bright grades + film grain/texture | **PASS** (one marginal case) |
| KUJI | 5 | paper-framed prints: per-variant tone/saturation fit + monochrome grain + the recovered frame overlay (rough edges, ink strokes, rounded #5) | **PASS** — export means within 0.9 levels; frames recompose PREQUEL's exactly |
| FOCAL | 6 | colour-only grades fitted as six 33³ LUTs; #1 adds a portrait-ellipse soft focus (sharp centre, blurred edges) | **PASS** — export means within 0.6 levels; MAD 2.2–3.5/255; geometry NCC 0.999 |

Per-family verdicts, numbers and evidence:
[`docs/prequel-research/app/verification/variant-fidelity-report.md`](docs/prequel-research/app/verification/variant-fidelity-report.md),
[`docs/prequel-research/device-packs/variant-exports-2026-10-27/`](docs/prequel-research/device-packs/variant-exports-2026-10-27/README.md),
and the KUJI/FOCAL reports in
[`docs/prequel-research/app/verification/packs/`](docs/prequel-research/app/verification/packs/).

The YESTERDAY and SENSUAL families were **rewritten** after an earlier AI-harness attempt got
them wrong; they are now fitted against PREQUEL's own full-resolution exports of the same demo
photo (`docs/prequel-research/device-packs/exports/`). Verification:
[`docs/prequel-research/app/verification/packs/verification-report.md`](docs/prequel-research/app/verification/packs/verification-report.md).
DUSTY and HALATION were verified the same way on 2026-10-27; both were then retuned (per-variant
DUSTY grade refit, HALATION edge-gated fringe) and now pass — see the report above.

## Limitations

* PREQUEL's preset catalog/filters/textures and the date-stamp font are server-side; we use
  fitted analytic approximations (tone curves, grain/mottle, standard 5×7 dot font) that match
  the same-photo reference within the documented tolerances, not byte-identical copies.
* No PREQUEL assets/binaries are used except the recovered procedural mask image
  (`blurmask.jpg`, a rendered gradient mask).

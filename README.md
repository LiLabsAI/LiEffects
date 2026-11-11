# Photo effects

An Android photo editor with live colour grading, film effects and full-resolution JPEG export.
Built with Kotlin and Jetpack Compose, minSdk 36 / targetSdk 37.

## Editing

- 34 original filter recipes: Lucent, Nightfall, Marzipan, Lantern, Estuary and Mulberry with three variants each; Driftglass with six; Graphite with eight; Black Tie and Old Money with one each.
- Recipe-specific controls: Filter / Sharpen, Driftglass’s Filter / Intensity / Grain, Lantern’s Filter / Grain, Graphite’s variant-specific tone / aberration controls, and Estuary / Mulberry’s Filter strength.
- Driftglass Intensity controls horizontal motion softness; colour strength and grain remain independent.
- An Adjust layer with eight categories and 59 controls: Light, Color, HSL, Details, Stylize,
  Refocus, Optical and Blur.
- One optional Effect is applied after the selected filter and Adjust layer.
- 35 Effects across Motes, Lumen, Daymark, Velour, Paperlight, Prism, Nocturne, Marquee,
  Solstice and Screenprint.
- Solstice adds Ember, Tidal and Rosette light leaks; Screenprint adds Carbon, Duet and
  Terracotta halftone ink looks. Each has its own palette and spatial treatment.
- Canvas: Original plus Square, Story, Frame, both Post and Classic orientations, Portrait
  and Landscape, with pan/pinch zoom, 90° rotation, flip and straightening.
- Current-photo thumbnails, press-and-hold comparison, undo and reset.
- Source-limited export resolution, JPEG quality and metadata options.

## Rendering architecture

The 34 Filters share one parameterized processing engine. Recipe data lives in
`app/src/main/resources/filters/catalogue.properties`, independently of the rendering code.
The versioned loader validates identifiers, variants, field names, numeric ranges and tonal
endpoints. New recipes using the existing operations require catalogue changes only.

Each recipe controls exposure gain, contrast, saturation, tonal endpoints, channel gains,
shadow/highlight tinting, signed tonal adjustments, radial chromatic separation, sharpening,
soft focus and seeded monochrome grain. AGSL uniforms drive the GPU preview. A deterministic
Kotlin renderer performs the corresponding operations for thumbnails and export. Filter
strength blends the colour grade independently of sharpening, soft focus and grain. Driftglass
starts at Filter 70 / Intensity 70 / Grain 20. Its directional pass combines horizontal
triangular motion blur, narrow vertical softness and headroom-limited local detail. Intensity
mainly changes the motion distance; the detail kernel stays constant. Spatial distances are
normalized to image width, while grain uses its own image-size normalization. Noise is deterministic and uses bounded arithmetic without
trigonometry or reference textures. These are display-referred
RGB grades. Lantern starts at Filter 70 / Grain 40 and uses three authored palettes
(teal/warm, amber/plum and olive/gold) with independent procedural grain and no spatial blur.
Graphite supplies monochrome and toned-print styles with recipe-specific Filter, Grain,
Highlights, Shadows and Aberration controls. Highlights and Shadows range from −100 to100;
zero is neutral. Radial channel separation samples a pre-graded intermediate layer (one ARGB
frame, or one offscreen GPU layer) at opposing channel offsets, then adds grain.
Estuary adds three authored colour-only palettes (clean teal, deep petrol and matte cyan),
starting at Filter 100 with no grain or spatial stages.
Mulberry adds three colour-only wine/plum/rose palettes at Filter 100, also with no grain
or spatial stages.
Black Tie and Old Money add two single-variant dark-luxury palettes (midnight charcoal and warm
espresso) at Filter 100, with no grain or spatial stages.
Nocturne (Gilded, Afterhours) and Marquee (Premiere, Midnight) are asset-free analytic effect
families: one AGSL pass with a matching deterministic CPU renderer and no textures or LUTs.
Marquee is a dark-blue cinema grade — skin-exempt cool white balance, a filmic toe and highlight
shoulder, a blue-biased matte black floor, headroom-safe navy/amber split toning, a normalized
subtractive falloff and midtone-gated grain.
Solstice (Ember, Tidal, Rosette) screens normalized elliptical colour leaks over the source,
with a soft flare core and bounded film grain. Screenprint (Carbon, Duet, Terracotta) maps
lightness to circular ink coverage on tinted paper; Duet adds an offset second ink screen
with subtractive overprinting. Screen pitch scales with the 1600-pixel long edge and has a
two-pixel minimum for tiny previews. Antialiased dot edges use the same widths and geometry
on CPU and GPU. Both families use one pass, no external assets and a 16 B/pixel export budget.
The current pipeline does not provide a RAW or HDR working space; picked images are normalized
to sRGB.

The bottom panel is a progressive disclosure surface, not a permanently tall stack. It has three
stages: **Categories → Items → Edit**. At Categories only the shelf/category row is mounted; after
choosing a category the cards or control chips replace it. For Effects and Filters, one tap
auditions a preset and **stays in the card browser**, so presets can be compared quickly; tapping
the already-selected preset again opens Edit, and the header also exposes explicit Edit/Clear
actions. An Adjust category opens its control chips, and choosing a control goes directly to
Edit. `AnimatedContent` slides/fades between stages and `animateContentSize` returns the freed
height to the photo. The header chevron and the bottom back button walk the stages in reverse.
The photo uses the space released by the compact active panel.

The Adjust layer is one shared stage rather than a preset shelf. It exposes 59 controls across
eight categories, with an analytic implementation and no external LUTs or textures:
Light and Color pointwise corrections including Whites, six-band HSL hue/saturation/lightness,
detail unsharp/clarity/dehaze/colour denoise, stylize grain/dust/vignette/vintage, a vertical tilt-shift Refocus,
optical fisheye/RGB split/aberration, and mist/glow/gaussian/masked/zoom/radial/directional
blur. Signed controls use −100…100, unipolar 0…100, and Refocus position/range default to 50.
The stage has no master intensity; the panel uses category/control chips and one active slider,
with HSL band/component chips. Its GPU plan is colour/HSL → optional detail → optional
blur/refocus → optical/stylize finish; the CPU renderer uses the same order. Noise uses the
bounded 1600-scale hash from the filter grain engine, and spatial taps/radii match the AGSL
passes. A neutral colour prepass is omitted. Transparent Adjust intermediates retain straight
RGB precision with original coverage supplied separately, and every pass starts at exact texel
centres. JPEGs retain hardware linear sampling. Detail/blur pass bits become active on first non-neutral use and use a cache-stable
topology while dragging. The panel provides reset-all and undo; the Adjust stage is composed
between the filter and effect (`Filter → Adjust → Effect`).

Canvas is a nondestructive final geometry pass after the look: **Filter → Adjust → Effect →
Canvas**. Formats use exact width:height ratios (1:1, 9:16, 16:9, 4:5, 5:4, 3:4, 4:3, 2:3,
3:2); Original restores the source aspect. A shared affine model stores normalized source
coordinates, zoom, quarter turns, flip and a −45°…45° straightening angle. Crop bounds remain
inside the source, including rotated corners. Quarter turns swap paired formats. Ratio changes
retain the focal point where bounds allow. The Canvas panel uses Categories → Items → Edit;
one tap auditions a format, a second opens Zoom. Rotate opens rotation/flip and straightening.
Reset affects Canvas alone, and each completed pan, pinch or slider gesture adds one Undo step.

The GPU-backed preview applies the shared matrix and clip around the existing render subtree;
it does not read pixels back or rebuild the effect graph during crop gestures. Comparison keeps
the framing. Export applies the inverse matrix with the existing premultiplied CPU sampler:
software Canvas bitmap filtering has different fractional precision on the target phone.
Thumbnails apply the same framing and include geometry in their cache key. Save plans use the
cropped dimensions for resolution choices, while budgeting the entire decoded/rendered source
plus 12 B/pixel for geometry input/output arrays and destination. Zoom cannot turn the editor
preview into a higher-resolution export. Canvas parameters and navigation are included in the
versioned saved session and Undo snapshots; older sessions restore an unchanged Canvas.

The preview is an explicit **offscreen pass graph**. Each filter or effect stage records into its
own `RenderNode`; the next pass consumes that layer as its `content` shader. Previously every
stage was bound as a child shader of the next stage, so a blur tap in the final stage re-ran the
entire preceding shader. Costs now add instead of multiplying. Driftglass’s directional focus is
split into a horizontal 49-tap pass and a vertical finish pass; Graphite grades once before
aberration. The preview renders at a stable internal long edge (up to 1600 px) and is scaled to
the card, so panel animations do not rebuild the graph. Each graph owns its mutable shader
instances, while decoded assets are cached process-wide. Integer sharpening taps use a point
sampled source on the preview's pixel grid; fractional blur and aberration taps remain linear.

The editor decodes a 1920-px working bitmap; export still re-decodes the original at the planned
size. Thumbnails share one scaled source, render the current shelf, are cached by
photo/preset/other-stage/geometry, debounce rapid control changes and stop cooperatively between rows.
The comparison gesture retains the rendering graph. The ambient backdrop is a tiny pre-scaled
copy instead of a live full-screen blur, and the date stamp is rasterized once per size/date
instead of drawing hundreds of gradients each frame.

One immutable edit composition supplies both backends with the same
filter → adjust → effect order and independent strengths. Export re-decodes the original source
at the planned size;
it does not save the preview or substitute it when the original is unavailable. A render budget
includes intermediate and native/JNI buffers and caps resolution
for expensive effects. Directional filters reserve 32 bytes per pixel, including their
floating-point intermediate; the aberration path adds one graded ARGB frame. JPEG Low / Medium /
High use quality 40 / 75 / 95. Metadata can be replaced with fresh capture tags or copied from
the source where available.

The shared running-sum/prefix-sum blur kernels have an optional NDK C++ path (`app/src/main/cpp`,
arm64-v8a). It is a drop-in accelerator: the Kotlin implementation remains the reference and the
only path in JVM tests, and an on-device diagnostic verifies native float/ARGB results match the
reference exactly before the timings are trusted. Native allocations are checked and can fall
back to Kotlin; both paths cooperate with cancellation. This is the start of CPU export
acceleration, with the deterministic Kotlin renderer retained as its reference.

The editor's ViewModel survives activity recreation. Saved state retains the source URI, all
edits, navigation and bounded undo history across process recreation, with a persistent photo
picker grant where supported. Undo history is observable independently of slider changes, and
late photo-load completions cannot replace a newer selection.

## Build and verify

```bash
./gradlew :app:assembleDebug --offline
./gradlew :app:testDebugUnitTest :app:lintDebug --offline
```

The JVM suite covers deterministic rendering, existing effect baselines, catalogue validation,
independent controls, Adjust categories/spatial stages, three-stage composition and export
planning. `FilterGpuInstrumentedTest` and `AdjustGpuInstrumentedTest` compare filter and Adjust
shaders, composition and control endpoints against their CPU renderers through real GPU readback
on an emulator. Focused regressions cover parallel optical repeatability, transparent spatial
sampling, sharpening across transparent edges, source-grid resizing, Prism borders, export
allocation retries, session restoration, late photo loads and cancelled bitmap ownership.
Canvas coverage includes format geometry, corner containment, centroid-preserving pinch,
pixel-exact integer crops/rotations/flips, transparent interpolation, cancellation and export
budgets for the full source behind a small crop.

The connected USB phone rejects test APK installation. Use its app UI for controls, comparison
and export checks. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

On-device diagnostics are built into the app because test-APK installation is blocked:

```bash
adb shell am force-stop com.aliahad.photoeffects
adb shell am start -n com.aliahad.photoeffects/.MainActivity --ez render_diagnostics true
adb logcat -s RenderDiagnostics PhotoEffectsGpu
```

The debug-only harness runs separately from editor rendering, compares every GPU program
against its CPU mirror, verifies native blur parity, and logs CPU/GPU timings at 640 px for
representative presets. Exact filter/Adjust checks and characterized legacy effect envelopes
have separate counters and explicit limits. The Daymark date-stamp region is excluded from
shader-only readback. Focused endpoint and repeatability fixtures exercise the reviewed bugs.
GPU readback timings include setup and synchronization; they are not preview frame times.
Canvas diagnostics compare final hardware affine drawing with software output on opaque and
transparent fixtures, with their own counters and strict error limits.

## Source map

| Path under `app/src/main/` | Responsibility |
| --- | --- |
| `resources/filters/catalogue.properties` | Versioned artistic recipes and catalogue order |
| `java/com/aliahad/photoeffects/filter/FilterCatalogue.kt` | Portable recipe loader and validation |
| `java/com/aliahad/photoeffects/filter/FilterRegistry.kt` | Catalogue shelves and CPU/GPU effect construction |
| `java/com/aliahad/photoeffects/filter/FilterAgsl.kt` | Shared GPU grade, tonal and spatial processing |
| `java/com/aliahad/photoeffects/filter/CpuFilterRenderer.kt` | Deterministic CPU grade, spatial sampling and grain |
| `java/com/aliahad/photoeffects/filter/FilterColor.kt` | Pointwise grade and signed highlight/shadow curves |
| `java/com/aliahad/photoeffects/filter/FilterGrain.kt` | Shared deterministic grain field, mirrored in AGSL |
| `java/com/aliahad/photoeffects/filter/DirectionalFocus.kt` | CPU and AGSL directional blur with local detail |
| `java/com/aliahad/photoeffects/filter/FilterSoftFocus.kt` | Premultiplied bilinear sampling and local detail |
| `java/com/aliahad/photoeffects/adjust/AdjustParams.kt` | 59-control Adjust model, plans and uniform mapping |
| `java/com/aliahad/photoeffects/adjust/AdjustAgsl.kt` | Colour/HSL, detail, blur/refocus and finish passes |
| `java/com/aliahad/photoeffects/adjust/cpu/CpuAdjustRenderer.kt` | Deterministic Adjust CPU mirror |
| `java/com/aliahad/photoeffects/ui/editor/AdjustPanel.kt` | Adjust categories, HSL chips and active slider |
| `java/com/aliahad/photoeffects/canvas/CanvasParams.kt` | Exact formats, bounded affine geometry and gesture math |
| `java/com/aliahad/photoeffects/canvas/CanvasPixels.kt` | Deterministic premultiplied crop/rotation sampler |
| `java/com/aliahad/photoeffects/ui/editor/CanvasPhotoViewport.kt` | Hardware affine preview, crop grid and gesture handling |
| `java/com/aliahad/photoeffects/ui/editor/CanvasPanel.kt` | Canvas formats, zoom, rotation and straightening |
| `java/com/aliahad/photoeffects/ui/editor/EditorPanelChrome.kt` | Progressive panel header and stage transitions |
| `java/com/aliahad/photoeffects/effect/EditComposition.kt` | Filter → Adjust → Effect stage order and allowance |
| `java/com/aliahad/photoeffects/effect/gpu/GpuRenderGraph.kt` | Offscreen RenderNode pass graph and asset binding |
| `java/com/aliahad/photoeffects/effect/gpu/GpuEffectRenderer.kt` | AGSL compilation cache, uniforms and shader inputs |
| `java/com/aliahad/photoeffects/effect/gpu/GpuReadback.kt` | HardwareRenderer readback for diagnostics/tests |
| `java/com/aliahad/photoeffects/effect/cpu/NativeBlur.kt` | Optional NDK blur backend with Kotlin fallback |
| `cpp/photoeffects.cpp` | C++ prefix-sum float and integer box blur kernels |
| `java/com/aliahad/photoeffects/diagnostics/RenderDiagnostics.kt` | In-app parity, native-blur and timing harness |
| `java/com/aliahad/photoeffects/effect/` | Effect families, parameter models and GPU programs |
| `java/com/aliahad/photoeffects/effect/cpu/` | Effect CPU renderers and asset samplers |
| `java/com/aliahad/photoeffects/photo/` | Source loading, export planning, EXIF and MediaStore saving |
| `java/com/aliahad/photoeffects/ui/editor/` | Editor state, tools, controls and live preview |
| `java/com/aliahad/photoeffects/ui/editor/EditorViewModel.kt` | Retained editor, saved session and photo-picker grants |
| `java/com/aliahad/photoeffects/ui/editor/EditorSession.kt` | Observable bounded undo and portable session codec |
| `java/com/aliahad/photoeffects/ui/editor/LatestPhotoLoad.kt` | Latest-request decoding and stale-result ownership |
| `java/com/aliahad/photoeffects/photo/ExportRetry.kt` | Allocation-only export retries down to the final floor |

## Quality and provenance

The 34 Filters are authored analytic grades inspired by digital-camera, low-light and creamy
colour and soft-focus film styles. They contain no extracted reference-app LUTs or images, and do not claim an
exact visual match to another product. Their names are catalogue labels; processing is shared
and driven by recipe data. Older session identifiers are migrated on read, including Undo
history and selected shelves; new sessions use the current catalogue names.

The effect pipelines include legacy reference-derived data.
`blurmask.jpg` and `paperlight_frame_1.png` through `paperlight_frame_5.png` are reference-derived assets;
`prism_luts.png` contains grades fitted from reference exports. Those older assets are not
claimed as original artwork. Historical research provenance is preserved in an archive outside this project.

Broader visual validation across skin tones, lighting, source sizes and devices still remains.
The 640-px diagnostic baseline shows that noise-heavy CPU effects (Motes/Velour) are dominated
by per-pixel trigonometric hashing and still need a noise-texture or full native-kernel pass
before 12 MP exports are fast; the native blur work is a first step, not the end state. Adjust
spatial controls use the same 9-tap support in CPU and AGSL; optical resampling remains a
per-pixel approximation at extreme edges, and advanced controls (Brilliance, Push-pull, HDR,
Ultrasharp, Midtone, Vintage, Fisheye and zoom/radial/directional blur) still need visual tuning.
Further transparent-edge visual validation and 16-bit/high-precision intermediates remain
future work. The photo picker may strip source GPS before the editor receives it; Keep metadata
can copy only the tags actually available to the app.

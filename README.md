# Photo effects

An Android photo editor with live colour grading, film effects and full-resolution JPEG export.
Built with Kotlin and Jetpack Compose, minSdk 36 / targetSdk 37.

## Editing

- 32 original filter recipes: G7X, Dim, Vanilla, Cine Film, Teal Tone and Burgundy with three variants each; Insomnia with six; Chicago with eight.
- Recipe-specific controls: Filter / Sharpen, Insomnia’s Filter / Intensity / Grain, Cine Film’s Filter / Grain, Chicago’s variant-specific tone / aberration controls, and Teal Tone / Burgundy’s Filter strength.
- Insomnia Intensity controls horizontal motion softness; colour strength and grain remain independent.
- An Adjust layer with eight categories and 57 controls: Light, Color, HSL, Details, Stylize,
  Refocus, Optical and Blur.
- One optional Effect is applied after the selected filter and Adjust layer.
- 25 Effects across Dusty, Halation, Yesterday, Sensual, Kuji and Focal.
- Current-photo thumbnails, press-and-hold comparison, undo and reset.
- Source-limited export resolution, JPEG quality and metadata options.

## Rendering architecture

The 32 Filters share one parameterized processing engine. Recipe data lives in
`app/src/main/resources/filters/catalogue.properties`, independently of the rendering code.
The versioned loader validates identifiers, variants, field names, numeric ranges and tonal
endpoints. New recipes using the existing operations require catalogue changes only.

Each recipe controls exposure gain, contrast, saturation, tonal endpoints, channel gains,
shadow/highlight tinting, signed tonal adjustments, radial chromatic separation, sharpening,
soft focus and seeded monochrome grain. AGSL uniforms drive the GPU preview. A deterministic
Kotlin renderer performs the corresponding operations for thumbnails and export. Filter
strength blends the colour grade independently of sharpening, soft focus and grain. Insomnia
starts at Filter 70 / Intensity 70 / Grain 20. Its directional pass combines horizontal
triangular motion blur, narrow vertical softness and headroom-limited local detail. Intensity
mainly changes the motion distance; the detail kernel stays constant. Spatial distances are
normalized to image width, while grain uses its own image-size normalization. Noise is deterministic and uses bounded arithmetic without
trigonometry or reference textures. These are display-referred
RGB grades. Cine Film starts at Filter 70 / Grain 40 and uses three authored palettes
(teal/warm, amber/plum and olive/gold) with independent procedural grain and no spatial blur.
Chicago supplies monochrome and toned-print styles with recipe-specific Filter, Grain,
Highlights, Shadows and Aberration controls. Highlights and Shadows range from −100 to100;
zero is neutral. Radial channel separation samples a pre-graded intermediate layer (one ARGB
frame, or one offscreen GPU layer) at opposing channel offsets, then adds grain.
Teal Tone adds three authored colour-only palettes (clean teal, deep petrol and matte cyan),
starting at Filter 100 with no grain or spatial stages.
Burgundy adds three colour-only wine/plum/rose palettes at Filter 100, also with no grain
or spatial stages.
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
This mirrors PREQUEL's full-bleed photo plus compact one-slider panel while removing our previous
simultaneous category/cards/controls stack.

The Adjust layer is one shared stage rather than a preset shelf. It exposes 57 controls across
eight categories, with an original analytic implementation (no PREQUEL LUTs or assets):
Light and Color pointwise corrections, six-band HSL hue/saturation/lightness, detail
unsharp/clarity/dehaze, stylize grain/dust/vignette/vintage, a vertical tilt-shift Refocus,
optical fisheye/RGB split/aberration, and mist/glow/gaussian/masked/zoom/radial/directional
blur. Signed controls use −100…100, unipolar 0…100, and Refocus position/range default to 50.
The stage has no master intensity; the panel uses category/control chips and one active slider,
with HSL band/component chips. Its GPU plan is colour/HSL → optional detail → optional
blur/refocus → optical/stylize finish; the CPU renderer uses the same order. Noise uses the
bounded 1600-scale hash from the filter grain engine, and spatial taps/radii match the AGSL
passes. Detail/blur pass bits become active on first non-neutral use and use a cache-stable
topology while dragging. The panel provides reset-all and undo; the Adjust stage is composed
between the filter and effect (`Filter → Adjust → Effect`).

The preview is an explicit **offscreen pass graph**. Each filter or effect stage records into its
own `RenderNode`; the next pass consumes that layer as its `content` shader. Previously every
stage was bound as a child shader of the next stage, so a blur tap in the final stage re-ran the
entire preceding shader. Costs now add instead of multiplying. Insomnia’s directional focus is
split into a horizontal 49-tap pass and a vertical finish pass; Chicago grades once before
aberration. The preview renders at a stable internal long edge (up to 1600 px) and is scaled to
the card, so panel animations do not rebuild the graph. Compiled shaders and decoded assets are
cached process-wide.

The editor decodes a 1920-px working bitmap; export still re-decodes the original at the planned
size. Thumbnails share one scaled source, are cached by photo/preset/other-stage, debounce rapid
control changes and stop cooperatively between rows. The ambient backdrop is a tiny pre-scaled
copy instead of a live full-screen blur, and the date stamp is rasterized once per size/date
instead of drawing hundreds of gradients each frame.

One immutable edit composition supplies both backends with the same
filter → adjust → effect order and independent strengths. Export re-decodes the original source
at the planned size;
it does not save the preview. A heap budget includes intermediate buffers and caps resolution
for expensive effects. Directional filters reserve 32 bytes per pixel, including their
floating-point intermediate; the aberration path adds one graded ARGB frame. JPEG Low / Medium /
High use quality 40 / 75 / 95. Metadata can be replaced with fresh capture tags or copied from
the source where available.

The shared box/prefix-sum blur kernels have an optional NDK C++ path (`app/src/main/cpp`,
arm64-v8a). It is a drop-in accelerator: the Kotlin implementation remains the reference and the
only path in JVM tests, and an on-device diagnostic verifies native float/ARGB results match the
reference exactly before the timings are trusted. This is the start of CPU export acceleration,
not a replacement for the deterministic Kotlin export oracle.

## Build and verify

```bash
./gradlew :app:assembleDebug --offline
./gradlew :app:testDebugUnitTest :app:lintDebug --offline
```

The JVM suite covers deterministic rendering, existing effect baselines, catalogue validation,
independent controls, Adjust categories/spatial stages, three-stage composition and export
planning. `FilterGpuInstrumentedTest` and `AdjustGpuInstrumentedTest` compare filter and Adjust
shaders, composition and control endpoints against their CPU renderers through real GPU readback
on an emulator. There are 142 JVM tests and 11 GPU parity tests, including mixed-alpha edge
sampling.

The connected USB phone rejects test APK installation. Use its app UI for controls, comparison
and export checks. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

On-device diagnostics are built into the app because test-APK installation is blocked:

```bash
adb shell am force-stop com.aliahad.photoeffects
adb shell am start -n com.aliahad.photoeffects/.MainActivity --ez render_diagnostics true
adb logcat -s RenderDiagnostics PhotoEffectsGpu
```

The harness compares every GPU program against its CPU mirror, verifies native blur parity,
and logs CPU/GPU timings at 640 px for representative presets. Legacy stochastic/approx blur
families are logged for inspection; filters have transparent-edge coverage in the instrumented
suite.

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
| `java/com/aliahad/photoeffects/adjust/AdjustParams.kt` | 57-control Adjust model, plans and uniform mapping |
| `java/com/aliahad/photoeffects/adjust/AdjustAgsl.kt` | Colour/HSL, detail, blur/refocus and finish passes |
| `java/com/aliahad/photoeffects/adjust/cpu/CpuAdjustRenderer.kt` | Deterministic Adjust CPU mirror |
| `java/com/aliahad/photoeffects/ui/editor/AdjustPanel.kt` | Adjust categories, HSL chips and active slider |
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

## Quality and provenance

The 32 Filters are authored analytic grades inspired by digital-camera, low-light and creamy
colour and soft-focus film styles. They contain no extracted reference-app LUTs or images, and do not claim an
exact visual match to another product. Their names are catalogue labels; processing is shared
and driven by recipe data.

Earlier Effects were developed through PREQUEL reference investigation and fitting. Legacy
`blurmask.jpg` and `kuji_frame_1.png` through `kuji_frame_5.png` are reference-derived assets;
`focal_luts.png` contains grades fitted from reference exports. Those older assets are not
claimed as original artwork. Detailed research provenance remains in the private, gitignored
`docs/` directory.

Broader visual validation across skin tones, lighting, source sizes and devices still remains.
The 640-px diagnostic baseline shows that noise-heavy CPU effects (Dusty/Sensual) are dominated
by per-pixel trigonometric hashing and still need a noise-texture or full native-kernel pass
before 12 MP exports are fast; the native blur work is a first step, not the end state. Adjust
spatial controls use the same 9-tap support in CPU and AGSL; optical resampling remains a
per-pixel approximation at extreme edges, and advanced controls (Brilliance, Push-pull, HDR,
Ultrasharp, Midtone, Vintage, Fisheye and zoom/radial/directional blur) still need visual tuning.
Alpha semantics for legacy transparent inputs and 16-bit/high-precision intermediates are also
future work. The photo picker may strip source GPS before the editor receives it; Keep metadata
can copy only the tags actually available to the app.

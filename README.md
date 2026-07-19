# OCR Studio

An offline-first Android app for high-accuracy Arabic OCR of scanned PDF books — including
large, degraded, old-scan books — with an image preprocessing pipeline, rule-based and optional
on-device LLM correction, validation scoring, a manual review screen, full-text search, and
multi-format export.

The app runs 100% offline. The only network access is a one-time, user-initiated download of
OCR/LLM model assets (Tesseract Arabic traineddata, PaddleOCR ONNX models, LiteRT-LM correction
models) — never anything else.

## Requirements

- Android Studio (Ladybug or newer) with AGP 8.5+
- JDK 17
- Android SDK: compileSdk 35, minSdk 26, targetSdk 35

## Building

```
./gradlew assembleDebug
```

> **Note on this repository's origin:** this project was generated in a sandboxed environment
> without access to the Android SDK or to Google's Maven repository (`dl.google.com` was not
> reachable), so `assembleDebug` could not be executed there. The Gradle/Kotlin DSL
> configuration was validated for syntactic correctness (`gradle wrapper`, `gradle help`
> project-graph evaluation), and the Android-independent business logic (`RuleEngine`,
> `Normalization`, `ConfusionMap`, `ValidationScorer`) was extracted into a throwaway plain
> Kotlin/JVM Gradle project and its unit tests were run successfully there. A full
> `assembleDebug` should be run in Android Studio (or any environment with the Android SDK and
> normal internet access) before shipping — see "Known risks" below for the handful of spots
> most likely to need a tweak.

## Project structure

Multi-module Gradle project (Kotlin DSL), version catalog at `gradle/libs.versions.toml`:

```
:app                 Compose UI (Material 3), Hilt wiring, navigation, screens
:core:common         Shared models, AppResult, ValidationScorer, dispatchers, constants
:core:database       Room entities/DAOs, FTS5 search index, PageRepository
:core:ui             Theme (RTL-aware), shared composables (diff view, chips, badges)
:engine:pdf          Pdfium-backed PDF rendering (SAF, DPI-based, auto-downsampled)
:engine:image        OpenCV preprocessing pipeline (deskew, denoise, binarize, ...)
:engine:ocr          OcrEngine interface + TesseractEngine + PaddleOcrEngine (ONNX)
:engine:parser       ParserProfile interface + Generic/MujamMufahras/Hadith/Tafsir profiles
:engine:correction   RuleEngine (dictionary + confusion-map) + LiteRT-LM corrector (flagged)
:engine:export       Streaming export plugins: SQLite, JSON, TXT, Markdown, CSV, XML
:worker              WorkManager batch pipeline, asset/model downloader, job recovery
```

## Architecture highlights

### Processing pipeline

For each page: `render → preprocess → OCR (+ escalation) → parse → correct → validate →
persist → checkpoint → recycle bitmaps`. Batches of `batchSize` pages (10–50, user-configurable)
are processed by one `BatchWorker` (`CoroutineWorker`) each, chained sequentially via
`WorkManager.beginUniqueWork(jobId, APPEND_OR_REPLACE, ...)`. Every worker runs as a
foreground/expedited job with a live notification (current page, batch X/Y, pages/min, ETA,
error count).

Memory discipline: only one bitmap and one OCR result are ever held in memory; bitmaps are
recycled immediately after use; the OCR engine for a batch is initialized once and closed once;
`onTrimMemory`/`onLowMemory` triggers a graceful pause at the next page boundary (the last
completed page is already checkpointed, so nothing is lost or reprocessed).

Crash recovery: `JobRecoveryManager` runs on app start, finds any `BookJob` left `RUNNING` whose
WorkManager chain died with the previous process, and re-enqueues it starting at
`currentPage + 1` — completed pages are never reprocessed.

### OCR engines

- **TesseractEngine** — the guaranteed path (`ara`, `OEM_LSTM_ONLY`, configurable PSM), with
  per-word confidence extracted via `ResultIterator`.
- **PaddleOcrEngine** — a full PP-OCR pipeline (DB detection + CRNN/SVTR recognition + CTC
  decode + RTL line assembly) over ONNX Runtime Android. Pages below the review threshold are
  automatically escalated to PaddleOCR (when its models are downloaded) and the better-scoring
  result is kept, with the winning engine recorded per page.

Both the PaddleOCR/ONNX path and the LiteRT-LM correction path are isolated behind interfaces
(`OnnxOcrRuntime`, `LlmCorrector`) with a **no-op fallback always compiled in**, so the app
always builds and runs on the Tesseract + rule-based-correction path even if those optional
artifacts aren't resolvable in a given environment. Toggle them via `gradle.properties`:

```properties
ocrstudio.enablePaddleOcr=true   # real onnxruntime-android artifact, on by default
ocrstudio.enableLiteRtLm=false   # coordinates less certain — flip on once verified locally
```

### Correction

Layer 1 (always on): dictionary-guided normalization (tatweel removal, ligature collapsing,
ی/ى and ه/ة and hamza-form variants, Arabic-Indic digit normalization) plus an OCR
confusion-map (ب/ت/ث/ن, ر/ز, د/ذ, ح/ج/خ, ع/غ, ص/ض, ط/ظ, س/ش) — a candidate substitution is only
ever accepted if the original word is absent from the dictionary and the candidate is present.

Layer 2 (optional): chunked (~1500 chars, paragraph-aligned) correction via a LiteRT-LM model,
with mandatory defensive post-processing — output is rejected (falling back to the rule-engine
text) if it differs in length by more than 15% or contains Latin sentences.

### Search

FTS5 virtual tables (`unicode61 remove_diacritics=2`) kept in sync by `PageRepository` inside
the same transaction as each page's write, so full-text search is diacritics-insensitive by
construction.

## Testing

```
./gradlew test
```

`RuleEngineTest` (normalization + confusion-map correctness) and `ValidationScorerTest` (the
`0.5*ocr + 0.3*dictionary + 0.2*parser`, `<0.80` review-threshold formula) live in
`engine/correction` and `core/common` respectively and were verified independently in a plain
Kotlin/JVM sandbox during development (see the build note above).

## Known risks to double-check in Android Studio

Since this was built without the Android SDK or a full network path to Google's Maven repo,
a few spots carry real but bounded risk and are worth a first look if `assembleDebug` fails:

- **LiteRT-LM artifact/API** (`engine/correction/src/liteRtEnabled`): the exact Maven
  coordinates and `Engine`/`Session` API shape used there are a best-effort reconstruction. It's
  off by default (`ocrstudio.enableLiteRtLm=false`); flip it on only after confirming the real
  API surface, or leave it off — the app is fully functional without it.
- **ONNX Runtime Android execution providers** (`PaddleOnnxOcrRuntime`): only `addNnapi()` is
  used; `addXnnpack` was intentionally left out since its exact signature varies by ORT version.
- **Compose Material 3 API surface** (BOM `2024.10.01`): a couple of call sites use the newer
  lambda-based `LinearProgressIndicator(progress = { ... })` overload — if Android Studio
  resolves an older BOM transitively, these are a one-line fix to the classic float-parameter
  overload.
- **`pdfium-android`, `tesseract4android`, `opencv` artifact versions** in
  `gradle/libs.versions.toml` are pinned to versions believed current at write time; bump them
  if AGP/Gradle flags an unresolved version.

## Acceptance criteria checklist

1. `./gradlew assembleDebug` — structurally verified (Gradle/Kotlin DSL config, module graph,
   dependency wiring); needs a real SDK+network environment to confirm, see above.
2. First-launch download → pick PDF → end-to-end job run → DB rows with text/confidence/score
   and a live notification: implemented (`AssetDownloadManager`, `NewJobWizardScreen`,
   `BatchWorker`).
3. Kill-and-resume without reprocessing: implemented (`JobRecoveryManager` + checkpoint-per-page
   transaction).
4. Bounded memory on huge PDFs: implemented (one bitmap/one OCR result in flight, immediate
   recycle, auto-downsample >4000px renders).
5. Diacritics-insensitive FTS: implemented (`unicode61 remove_diacritics=2`).
6. JSON + SQLite export with matching page counts: implemented (streaming exporters, page count
   returned and recorded in `ExportRecord`).
7. Rule-based-only correction works with no LLM model downloaded: implemented (`NoOpCorrector`
   is the default binding).
8. RTL/Arabic locale: `android:supportsRtl="true"`, full English + Arabic `strings.xml`.
9. PaddleOCR escalation after models are downloaded: implemented (`EngineRegistry
   .recognizeWithEscalation`).

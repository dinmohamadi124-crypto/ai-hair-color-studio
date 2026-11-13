# AI Hair Color Studio

A fully on-device, offline Android app built with Kotlin, Jetpack Compose, CameraX, and MediaPipe Tasks Vision for real-time and photo-based virtual hair coloring in **bilingual Persian (RTL) & English (LTR)**.

## Features

- **100% Offline & Private**: No `INTERNET` permission, no network calls, and no cloud APIs.
- **Bilingual Persian & English**: Complete English (`values/strings.xml`) and Persian (`values-fa/strings.xml`) localization, automatic RTL/LTR layout mirroring, `Vazirmatn` typography for Persian and system default font for English, and an in-app Home screen language toggle persisted via DataStore and applied immediately with `AppCompatDelegate.setApplicationLocales` (defaulting to device locale).
- **Real-Time Hair Segmentation**: Uses MediaPipe `ImageSegmenter` with the bundled `hair_segmentation.tflite` model (`app/src/main/assets/hair_segmentation.tflite`).
  - Runs inference on a 256×256 downscaled copy.
  - Upscales the confidence mask with bilinear filtering and applies a 2px Gaussian blur feather for natural blending.
  - Detects empty hair masks and notifies the user without recoloring skin or background.
- **Natural-Texture HSL Recoloring**: Preserves original hair luminance/lightness and strand detail while transferring the target shade's hue and saturation inside the hair mask only.
- **30 Curated Hair Shades**: Complete palette from Jet Black (`#1A1A1A`) to Fire Orange (`#FF5F1F`) with localized English and Persian names.
- **Smart Skin-Tone Suggestions**: Uses MediaPipe `FaceDetector` (`blaze_face_short_range.tflite`) to sample cheek skin tone and recommend the 3 most flattering shades with Persian reasons (with natural-shade fallback when no face is detected).
- **Live Camera**: Front-camera CameraX preview with real-time hair recoloring, intensity slider, press-and-hold original comparison, and PNG capture to `Pictures/AI Hair Color Studio`.
- **Photo Studio**: Zero-permission Android Photo Picker (`PickVisualMedia`), draggable Before/After split divider, intensity slider, and PNG export via `MediaStore`.

## Build & Run Steps

1. Open the project in Android Studio (Meerkat or newer) with JDK 17+ / Android SDK 34+.
2. Verify bundled MediaPipe models are present under `app/src/main/assets/`:
   - `hair_segmentation.tflite`
   - `blaze_face_short_range.tflite`
3. Build from the command line using Gradle:
   ```bash
   gradle assembleDebug
   ```
4. Run local unit tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```

# JPG Tool (`:jpgtool`)

A small stand-alone Android app, separate from the GCFL archive apps:

1. **Compress JPGs to a size you choose.** Enter a max file size (KB or MB). For each photo the app
   finds the highest JPEG quality that fits (binary search, ~7 encodes). Only if even the lowest
   quality is too big does it reduce the resolution, step by step, until it fits.
   Optional: a max longest side in pixels; keep photos that are already small enough untouched.
2. **Watermark with your logo.** Pick any image as the logo (a transparent PNG looks best; it is kept
   for next time), then choose position (3×3 grid), size (% of photo width), opacity and distance
   from the edge. The preview updates live on the first picked photo.

Pick many photos at once, pick a **whole folder** (optionally with its subfolders, up to 5000 photos),
or share photos from a gallery app to **JPG Tool**. Results are saved as `<name>_small.jpg` to
`Pictures/JPG Tool`, or to **any folder you choose** (remembered; the system folder picker grants
lasting access to it). Originals are never changed. Results can be shared straight from the app. Photos are
rotated upright from their EXIF orientation; other metadata (including GPS location) is not copied.
All settings are remembered. UI in English and Korean.

## Build

```
./gradlew :jpgtool:assembleDebug      # → jpgtool/build/outputs/apk/debug/jpgtool-debug.apk
./gradlew :jpgtool:testDebugUnitTest  # size search, watermark placement, real Bitmap pipeline (Robolectric)
```

GitHub Actions uploads the debug APK as the `jpg-tool-debug-apk` artifact. minSdk 26 (Android 8.0).

## Code

```
Logic.kt           Settings, Anchor, watermark placement, quality binary search (pure Kotlin, unit tested)
Folders.kt         folder picker (SAF): list photos in a folder tree, save into a chosen folder
ImageProcessor.kt  decode (EXIF rotation, memory-safe sampling) → watermark → compress → save (MediaStore)
MainViewModel.kt   state, saved settings, live preview, batch processing
MainActivity.kt    Compose UI, photo/logo pickers, share in/out
```

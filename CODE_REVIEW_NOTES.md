# Code Review & Refactoring Notes

> Senior-engineer review pass over the Kotlin codebase. Date: 2026-05-16.
> Build verified with `./gradlew assembleDebug` + `./gradlew spotlessApply`.

This document records **what was changed and why**, plus **known issues left
open**, so the work is auditable and the open items aren't forgotten.

---

## 1. Critical bugs fixed

### 1.1 Room database rebuilt on every call — `SaveItemDatabase.kt`
`getInstance()` used double-checked locking but **never assigned the built
database back to `instance`**. Every `SaveItemRepository(...)` constructed a new
DB connection.

- Fixed: `Room.databaseBuilder(...).build().also { instance = it }`.
- Also switched to `context.applicationContext` to avoid holding a component
  `Context` in a process-lifetime singleton.

### 1.2 `rm -rf` shelled out on every launch — `FileUtils.kt`
`clearApplicationData()` ran `Runtime.getRuntime().exec("rm -rf $path")` inside
`MainActivity.onCreate` **on every start**.

- Fixed: one-time cleanup gated by a `SharedPreferences` flag
  (`dmp_prefs` / `legacy_files_cleared`), using `File.deleteRecursively()` on a
  single-thread `Executor`. No shell, runs once per install.

### 1.3 `startService` targeted the wrong class — `MediaPlaybackService.kt`
The intent referenced `android.service.media.MediaBrowserService` (the framework
*abstract class*) instead of `MediaPlaybackService::class.java`, so the service
was never promoted to a *started* service.

- Fixed: `Intent(this, MediaPlaybackService::class.java)`.

### 1.4 NPE-prone media-ID parsing — `MainActivity.kt`
`metadata.getString(METADATA_KEY_MEDIA_ID).toLong()` only caught
`NumberFormatException`, not the `NPE` from a null ID.

- Fixed: `...getString(...)?.toLongOrNull() ?: -1L`.

---

## 2. Performance / lifecycle issues fixed

| Issue | Before | After |
|---|---|---|
| Progress ticker | re-posted every **10 ms** (~100 Hz), handler+runnable recreated per play | single reusable handler/runnable, **250 ms** interval |
| Broadcast receivers | 3 receivers registered on **every** `onPlayFromUri`, unregistered once → leak | registered once in `onCreate`, unregistered in `onDestroy`, idempotency guard `receiversRegistered` |
| Notification channel | recreated on every `getNotification()` call | `createNotificationChannel()` once in `onCreate` |
| Resource release | `MediaPlayer`/`MediaSession` not reliably released; `MediaMetadataRetriever` & `Cursor`s leaked on exceptions | proper `release()` + `finally`; `Cursor` wrapped in `.use {}`; `isPlayerReleased` flag |
| Resume re-seek | `onMetadataChanged` re-queried DB & re-seeked on every metadata re-delivery (config change) | guarded by `lastResumedId` |

---

## 3. Code-smell / API cleanups

- `AsyncTask` (deprecated) → coroutines (`serviceScope`) / `Executor`.
- `Handler()` (deprecated no-arg) → `Handler(Looper.getMainLooper())`.
- `throw RuntimeException` on unreadable file (crashed the service) → logged
  gracefully.
- `MediaStore.Video.*` constants were used to read **audio** files, and a
  **video** content-URI was built for an audio track → corrected to
  `MediaStore.Audio.*`.
- Embedded album art: `BitmapFactory.decodeByteArray(picture, 0, picture!!.size)`
  threw on tracks with no embedded art → null-safe with MediaStore album-art
  URI fallback.
- `MainActivity`: the ~25× repeated
  `MediaControllerCompat.getMediaController(this@MainActivity)...` →
  `mediaController` / `transportControls` accessor properties.
- The 38-line playback-speed `when` and the repeat-mode block → small helpers
  (`cyclePlaybackSpeed()`, `speedLabelRes()`, `cycleRepeatMode()`).
- Deprecated APIs: `resources.getColor` → `ContextCompat.getColor`;
  `intent.extras.get(EXTRA_STREAM)` → `IntentCompat.getParcelableExtra`;
  permission calls via `ActivityCompat`.
- Permission result only checked `grantResults[0]` → now checks all granted.
- `Audio` → immutable `data class`.
- `MainViewModel.repository` & `SaveItemRepository` ctor param → non-null;
  `SaveItemDao.getSaveItem` correctly typed `SaveItem?`.
- Removed a duplicate `androidx.core:core-ktx` dependency in `app/build.gradle`.
- Noisy `Log.e("vishnu", ...)` debug logging removed from `MainActivity`;
  remaining logs use a proper `"DMP"` tag.

---

## 4. Files touched

```
app/build.gradle
app/src/main/java/phone/vishnu/dialogmusicplayer/Audio.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/AudioUtils.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/FileUtils.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/MainActivity.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/MainViewModel.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/MediaPlaybackService.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/SaveItemDao.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/SaveItemDatabase.kt
app/src/main/java/phone/vishnu/dialogmusicplayer/SaveItemRepository.kt
```

`AndroidManifest.xml` was intentionally **not** changed — Spotless wanted a
whitespace-only reformat of a pre-existing comment block; out of scope for this
pass.

---

## 5. Known issues deliberately LEFT OPEN

These need their own change / decision and were not touched:

1. **Track identity is matched purely by `DURATION`** (`AudioUtils.extractId` /
   `fetchMetadata`). Two different songs with the same length resolve to the
   same MediaStore `_ID` → wrong metadata and a wrong saved resume position.
   Proper fix needs a real identity key (URI / content hash) and a Room schema
   migration of `SaveItem`.

2. **`MainActivity.onDestroy` force-stops playback** (`transportControls?.stop()`
   — the original "hack!" comment). This is a product decision: should music
   continue when the dialog is dismissed vs. truly finished? Left as-is.

3. **`MediaButtonActionReceiver`** is largely redundant given
   `MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS`. Harmless; left in.

4. **`MediaScannerConnection.scanFile` callback is async** but its result is
   read synchronously right after (`contentUri.get()`), so that fallback path
   in `AudioUtils.getMetaData` almost always sees `null`. Pre-existing; a real
   fix means making the scan synchronous or restructuring the fallback.

5. **`requestLegacyExternalStorage="true"`** in the manifest is ignored on
   API 30+. Cosmetic.

---

## 6. Recommended next refactor

- ~~Adopt ViewBinding / move state into the ViewModel~~ — **superseded by the
  Jetpack Compose migration below (§7).**
- Consider migrating off the legacy `MediaBrowserServiceCompat` /
  `MediaSessionCompat` stack to **Media3** (`androidx.media3`), which is the
  current supported library.

---

## 7. Jetpack Compose migration (done)

The whole UI layer was migrated from XML Views to Jetpack Compose.

### Build setup
- Kotlin `2.0.0` → `2.0.21` (Compose Compiler plugin requires a matching
  Kotlin version; `2.0.0`'s plugin was not available, `2.0.21` is the closest).
- KSP `2.0.0-1.0.23` → `2.0.21-1.0.27` (must track the Kotlin version).
- Added the `org.jetbrains.kotlin.plugin.compose` plugin (`2.0.21`) — mandatory
  with Kotlin 2.0+.
- `app/build.gradle`: `buildFeatures { compose true }`, Compose BOM
  `2024.12.01`, `compose.ui`, `compose.foundation`, `material3`,
  `activity-compose`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`.
- `lifecycle-viewmodel-ktx` bumped `2.8.3` → `2.8.6`.

### New files
- `PlayerUiState.kt` — immutable UI-state snapshot the screen renders.
- `DmpTheme.kt` — `MaterialTheme` wrapper; dynamic color on API 31+, brand-red
  accent otherwise (replaces the old `ColorUtils` + XML dynamic-color theme).
- `PlayerScreen.kt` — the screen as composables: album art, marquee title /
  artist (`Modifier.basicMarquee()` — replaces the `ScrollingMovementMethod`
  hack), Material3 `Slider`, transport controls.

### Architecture change
- `MainViewModel` now owns a `StateFlow<PlayerUiState>`. The
  `MediaControllerCompat.Callback` in `MainActivity` maps callbacks into that
  state; the Composable collects it with `collectAsStateWithLifecycle()`.
- `MainActivity` uses `setContent { ... }`; it keeps only the media-browser
  plumbing and the dialog-window flags. All the `findViewById` / `lateinit`
  view fields and the manual view mutation are gone.

### Removed
- `activity_main.xml` and `layout-v23/activity_main.xml`.
- `ColorUtils.kt`.
- The (already `visibility="gone"`) playback-speed UI was not carried over — it
  was dead UI in the XML version. The service still supports speed changes.

### Not verifiable here
`assembleDebug` passes, but **the dialog window sizing, dynamic theming, marquee
animation and the tap-outside-to-background gesture need a visual check on a
device/emulator** — those can't be confirmed from a build alone.

### Notes / possible follow-ups
- `styles.xml` still defines the `AppTheme` (the floating-dialog window — still
  needed) plus now-unused `roundedImageView` / `sliderLabelStyle` styles and the
  `bottom_sheet_background` drawable. Left in place; harmless, `shrinkResources`
  drops them from release builds.
- The album art uses a fixed 220.dp square; the old `ShapeableImageView` was
  `wrap_content`. Adjust to taste.

---

## 8. Post-migration playback fixes (device-tested)

After the migration the app launched but **would not play** (no audio, no
metadata, no album art). Diagnosed on a real device (Android 15) via logcat.

Two separate bugs:

### 8.1 Permission check regressed in the refactor
`onRequestPermissionsResult` required *every* requested permission to be
granted. On Android 13+ the app requests `READ_MEDIA_AUDIO` **and**
`POST_NOTIFICATIONS`; denying the (optional) notification permission left
`initTasks()` uncalled, so nothing connected. Fixed: playback now starts as
soon as the **essential audio permission** is available — `essentialPermission()`
/ `hasEssentialPermission()` — and `POST_NOTIFICATIONS` is requested separately
without gating playback.

### 8.2 Playback gated on audio focus
`MediaPlaybackService` did `if (!requestFocus()) return` in the prepared
listener. On the test device `requestAudioFocus()` returned
`AUDIOFOCUS_REQUEST_FAILED`, so playback silently never started. Fixes:
- `AudioFocusRequest` now sets `setUsage(AudioAttributes.USAGE_MEDIA)` (it only
  set `CONTENT_TYPE_MUSIC` before — the system logged `AA=USAGE_UNKNOWN`).
- Playback no longer aborts when focus is denied — the user explicitly opened a
  file to play it. Focus is still requested, and `onAudioFocusChange` still
  handles ducking / pause-on-loss. A denial just logs a warning.
- The `onPlayFromUri` prepared block is wrapped in `try/catch` and a
  `setOnErrorListener` was added, so a failure logs instead of silently dying.

Verified on device: file plays, position advances, metadata + embedded album
art render in the Compose UI.

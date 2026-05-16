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

## 6. Recommended next refactor (not done here)

The app is still a mixed-concern single `Activity` using `findViewById`.
Natural next step, best done as its own reviewable PR:

- Adopt **ViewBinding** (`buildFeatures { viewBinding true }`), drop the 11
  `lateinit` view fields + `initViews()` `findViewById` block.
- Move playback-state → UI-state mapping into `MainViewModel`, exposing a single
  observable UI state instead of the Activity reacting to raw
  `MediaControllerCompat.Callback` events.
- Consider migrating off the legacy `MediaBrowserServiceCompat` /
  `MediaSessionCompat` stack to **Media3** (`androidx.media3`), which is the
  current supported library.

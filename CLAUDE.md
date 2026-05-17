# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DialogMusicPlayer is a minimal Android music player that displays as a dialog. It handles audio files via `VIEW`/`SEND` intents — it is not a library browser. Licensed under GPLv3.

Package: `phone.vishnu.dialogmusicplayer` (debug suffix: `.debug`)
Min SDK 21, Target SDK 35, Kotlin 2.0, Java 21. The app code is Kotlin + Jetpack
Compose; only the boilerplate test stubs remain in Java.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (minified + shrunk)
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumentation tests
./gradlew spotlessCheck          # Check formatting (Java, Kotlin, XML)
./gradlew spotlessApply          # Auto-fix formatting
```

## Code Formatting

Spotless is configured in the root `build.gradle`:
- **Java**: Google Java Format (AOSP style), import order: `android, androidx, com, java, phone`, unused imports removed (only the test stubs are Java)
- **Kotlin**: ktlint 0.49.1, trailing whitespace trimmed
- **XML**: tabs for indentation, trailing whitespace trimmed
- All source files must have the GPLv3 license header from `spotless-header`

## Architecture

Single-module app (`app/`), single-activity (`MainActivity`), Kotlin codebase. MVVM:
`MainActivity` maps `MediaControllerCompat` callbacks into a `StateFlow<PlayerUiState>`
on `MainViewModel`; the Compose UI renders that state.

**UI layer (Kotlin + Jetpack Compose):**
- `MainActivity` — Dialog-styled activity that receives audio URIs via intent, connects to `MediaPlaybackService` through `MediaBrowserCompat`, controls playback via `MediaControllerCompat`, and hosts the Compose UI with `setContent`
- `PlayerScreen` — the player UI as composables (album art, marquee title/artist, slider, transport + speed controls)
- `PlayerUiState` — immutable snapshot the screen renders; `DmpTheme` — Material3 theme wrapper (dynamic color on API 31+)

**Playback layer (Kotlin):**
- `MediaPlaybackService` — `MediaBrowserServiceCompat` that manages `MediaPlayer`, audio focus, media session, and foreground notification. Communicates state back to `MainActivity` via media session callbacks.

**Persistence layer (Kotlin):**
- Room database for saving/resuming playback position per track
- `SaveItem` (entity, keyed by track ID) → `SaveItemDao` → `SaveItemDatabase` → `SaveItemRepository` → `MainViewModel` (coroutines + LiveData)

**Utilities (Kotlin):**
- `AudioUtils` — metadata extraction from audio URIs
- `FileUtils` — one-time cleanup of legacy internal-storage files

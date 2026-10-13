# Cleanup TODO

Dead-code audit from 2026-10-13. Callers were found with `codegraph callers` for every function in
`app/src/main`, and each candidate was double-checked with a text search across `app/src`
(main and test), which catches references like `viewModel::x` that the call graph misses.
Nothing here has been removed yet.

## Bug to fix first

- **No "Reading"/"Completed" badge on the library card.** The home grid uses `ModernMangaCard`
  in `ui/screens/HomeScreen.kt`, which shows no read status. The badge logic
  (`manga.isCompleted` → "Completed", else "Reading" when `lastReadTime > 0`) was added to
  `ui/components/MangaCard.kt`, which nothing uses. Move it to `ModernMangaCard`.
  The "Reading" filter itself already excludes completed manga.

## Safe to delete (no references anywhere)

| What | Where | Why it's dead |
|---|---|---|
| `MangaCard.kt`, `ContinueReadingCard.kt` | `ui/components/` | Whole files unused; Home uses `ModernMangaCard` and a play button |
| Reader fit-mode setting: `ReaderFitMode` enum, `_readerFitMode` / `readerFitMode` / `setReaderFitMode`, `KEY_READER_FIT`, `MangaViewModel.setReaderFitMode` | `data/Models.kt`, `data/preferences/PreferencesManager.kt`, `ui/MangaViewModel.kt` | Never read, no UI; the reader always fits the screen |
| Volume-keys setting: `_volumeKeysNavigation` / `volumeKeysNavigation` / `setVolumeKeysNavigation`, `KEY_VOLUME_KEYS`, `MangaViewModel.setVolumeKeysNavigation` | `data/preferences/PreferencesManager.kt`, `ui/MangaViewModel.kt` | Never read, no UI, no key handling anywhere |
| `continueReading`, `loadSampleManga`, `disableAppLock`, `changePin`, `hasPin` | `ui/MangaViewModel.kt` | Never called (screens use `resumeReading`; the PIN dialog calls `PreferencesManager` directly; `repository.loadSampleManga` is still used by `init`) |
| `getMangaById`, `getProgressForChapter`, `getAllChapterProgressOnce`, `deleteManga` | `data/db/MangaDao.kt` | Never called |
| `insertSessions`, `getSessionsBetween`, `getSessionsBetweenOnce`, `getSessionsForManga`, `getTotalDurationBetween`, `getCompletedChaptersCountBetween`, `getPagesProgressedBetween`, `getLongestSession`, `getLongestSessionOnce` | `data/db/StatisticsDao.kt` | Never called; stats are computed in code from all sessions. Keep `getAllSessionsOnce` (used by a test) |

Removing DAO queries doesn't change the database schema, so no Room migration is needed.

## AI Studio template leftovers

- **Firebase/Secrets build plugins:** `alias(libs.plugins.secrets)` and `alias(libs.plugins.google.services)`
  in both `build.gradle.kts` files, the `secrets {}` and `googleServices {}` blocks and the
  `MissingGoogleServicesStrategy` import in `app/build.gradle.kts`,
  `implementation(platform(libs.firebase.bom))`, `.env.example` (only a commented-out Gemini key),
  and `googleServices.missing.passthrough` in `gradle.properties`. The app makes no Firebase or Gemini calls.
  Check whether `buildConfig = true` is still needed (only `ExampleInstrumentedTest` reads `BuildConfig`).
- **~20 commented-out dependency lines** in `app/build.gradle.kts` (Retrofit, OkHttp, Moshi, Firebase,
  CameraX, Navigation, DataStore, Credentials, Accompanist, Play Services Location) and their entries in
  `gradle/libs.versions.toml`.

## Decide, not dead

- **`data/storage/SampleMangaGenerator.kt`:** on first launch with no folder chosen, it generates two demo
  manga (`MangaRepository.loadSampleManga`, called from `MangaViewModel.init`). Keep for a demo library,
  remove for a clean empty start (the home screen already has a "Select Manga Folder" empty state).
- **Template tests:** `ExampleUnitTest` (`2 + 2`) and `androidTest/ExampleInstrumentedTest`. Harmless noise.

## After cleaning up

- `./gradlew :app:testDebugUnitTest` (no wrapper script in the repo: use a Gradle 9.3.1 install)
- Release build with R8 (`:app:assembleRelease`) to make sure nothing removed was needed via reflection
- Re-run `codegraph sync` and repeat the callers scan to catch anything newly orphaned

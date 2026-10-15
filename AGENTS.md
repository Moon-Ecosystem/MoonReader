# AGENTS.md

Guidance for AI coding agents working in this repo.

## Project

Moon Reader (formerly MangaReader) is an offline Android reader for local `.cbz`/`.zip` comic archives, with optional AniList tracking (the only network use).

- Kotlin, Jetpack Compose (Material 3), Room, Coil 2.7.
- AGP 9.1, Gradle 9.3.1, minSdk 24, targetSdk 36.
- Single module: `app`. Package `com.example`; applicationId `com.aistudio.mangareader.kvmzqp`. The project started as an AI Studio export.
- Don't rename the leftover `mangareader` identifiers: the applicationId, the `manga_reader_database` / `manga_reader_prefs` file names (renaming wipes user data).
- Only ZIP-based archives (`.cbz`, `.zip`) can be read. `MangaScanner` also lists `.cbr`/`.rar`/`.7z` files in the library, but their pages can't be opened yet.
- Pinch zoom goes up to 5×. Double-tap toggles between 1× and 2× around the tap point.

## Layout

```
app/src/main/java/com/example/
  MainActivity.kt           entry point; handles "Open with" VIEW intents for .cbz files
  MoonReaderApp.kt          Application class (Coil ImageLoader setup)
  data/
    Models.kt, StatisticsModels.kt
    db/                     Room: AppDatabase, Entities, MangaDao, StatisticsDao
    repository/             MangaRepository, StatisticsRepository
    preferences/            PreferencesManager (settings, PIN)
    storage/                SAF scanning (MangaScanner), ZIP reading (ChannelZip, CbzArchive),
                            Coil page fetcher (CbzPageFetcher), ComicInfo.xml, covers, natural sort
  ui/
    MangaApp.kt             top-level navigation
    MangaViewModel.kt       the single app ViewModel (library, reader state, reading sessions)
    screens/                Home, ChapterList, Reader, Search, Settings, Statistics
    components/             shared composables
    theme/
app/src/test/               JVM/Robolectric unit tests
docs/                       V1_SPECIFICATION.md, CLEANUP_TODO.md (dead-code audit)
```

## Build and test

There is no `gradlew` in the repo, so use a system Gradle 9.3.1 with JDK 21 and an Android SDK that has platform 36.1.

```sh
gradle :app:testDebugUnitTest                              # unit tests (CI runs these)
gradle :app:processDebugResources :app:compileDebugKotlin  # compile check
```

- `:app:assembleDebug` needs `debug.keystore` at the repo root. It is not committed, so without it the build fails at `validateSigningDebug`. That failure is unrelated to code changes.
- Release signing reads the `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` env vars (key alias `upload`).
- Test results are written to `app/build/test-results/testDebugUnitTest/*.xml`.

## Git workflow

- Work on `dev` and open PRs from `dev` to `main`.
- Every push or merge to `main` runs `.github/workflows/release.yml`, which runs the tests, builds a signed APK and publishes a GitHub release `build-N`. Don't push to `main` directly.
- Don't push or open PRs unless asked.
- Use Conventional Commits: `feat(reader): ...`, `fix(stats): ...`, `docs: ...`.
- Never commit stray local files: `.codegraph/`, test `.cbz` archives, screen recordings.

## Conventions and gotchas

- **Room schema:** `AppDatabase` is at version 3 with explicit migrations (`MIGRATION_1_2`, `MIGRATION_2_3`).
  - If you change an entity, bump the version and add a migration.
  - `fallbackToDestructiveMigration()` is there only as a safety net. Relying on it wipes the user's library and stats.
- **Storage:** library access goes through the Storage Access Framework (persisted tree URIs) only, with no broad storage permissions. Pages are streamed out of the archive and never unzipped to disk.
- **External files:** files opened via "Open with" use chapter ids prefixed `external:` (`Chapter.isExternal`). No progress or reading sessions are saved for them.
- **Reading stats:**
  - A session is recorded in `MangaViewModel.endReadingSession()`, which runs on chapter change and on `ON_STOP`.
  - `StatisticsRepository.recordSession` merges a session into the previous one when it is the same chapter and the gap is 5 minutes or less.
- **AniList tracking** (`data/tracking/`):
  - Login uses AniList's implicit grant (client id `10495`, redirect `moonreader://anilist-auth`, which must match the client's settings on AniList). `MainActivity` is `singleTask` so the redirect reaches the running app through `onNewIntent`.
  - The token lives in the `anilist_auth` prefs file, which is excluded from backups.
  - Links are stored in `manga_tracks`. `withRead` only ever raises progress. Volume-named files update `progressVolumes`.
  - Offline progress is marked `pendingSync` and retried on app start.
  - Network calls use `HttpURLConnection` and `org.json`. Don't add a network library for this.
  - Search queries come from `TitleCleaner`, which strips release tags from file names.
- **FileProvider:** the authority is `${applicationId}.fileprovider`, and its paths are in `res/xml/file_paths.xml`. It is used to copy a page to the clipboard.
- **Compose test tags:** UI elements use `Modifier.testTag(...)`, for example `option_copy`. Keep the existing tags when you change a composable.
- Match the surrounding code style: Compose state lives in the ViewModel as `StateFlow`, and screens collect it.
- When you add or change a user-facing feature, update its entry in `README.md`.
- Before deleting code, check `docs/CLEANUP_TODO.md`; some files (e.g. `MangaCard.kt`, `SampleMangaGenerator.kt`) are known to be unused.

<div align="center">

<img src=".github/assets/logo.png" alt="Moon Reader logo" width="96"/>

# Moon Reader

### An offline reader for your local Manga and Comics

Read `.cbz`, `.cbr` and `.7z` manga and comic archives on Android. No ads, no accounts, no telemetry.

[![Release](https://img.shields.io/github/v/release/Moon-Ecosystem/MoonReader?label=Release&labelColor=27303D&color=0877d2)](https://github.com/Moon-Ecosystem/MoonReader/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Moon-Ecosystem/MoonReader/release.yml?labelColor=27303D)](https://github.com/Moon-Ecosystem/MoonReader/actions/workflows/release.yml)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?labelColor=27303D)](#download)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue?labelColor=27303D)](LICENSE)

</div>

## Download

Grab the latest signed APK from [Releases](https://github.com/Moon-Ecosystem/MoonReader/releases/latest). Every merge to `main` publishes a new build.

_Requires Android 7.0 (API 24) or higher._

## Features

- **Your files, nothing else.** Pick a folder with Android's folder picker and Moon Reader finds every series inside it. No storage permissions, nothing unzipped to disk.
- **Three reading modes:** right-to-left (manga), left-to-right and vertical webtoon scroll.
- **Page counter:** a small, faded page number at the bottom while the reader menu is hidden. Turn it off in Settings → Reader.
- **Smooth zoom:** pinch up to 5×, pan with fling, double-tap to zoom 2× where you tap.
- **Page actions:** long-press a page to set it as the series cover, copy it, or save it to your gallery.
- **Open with Moon Reader:** read a `.cbz` or `.cbr` straight from your file manager or browser without adding it to the library.
- **Library tools:** ComicInfo.xml metadata, natural chapter sorting, combinable filters (Reading, Not started or Completed, plus favorites only), sorting, search, custom covers and a Continue Reading shortcut. Long-press a series to mark it read or unread, add or remove it from favorites, or put it in collections.
- **Series page:** Continue picks up where you left off, next to your AniList status once you've logged in. The filter button shows only unread, in-progress, read or bookmarked chapters, and sorts by number or last read. Long-press a chapter to mark it read or unread, rename it, or remove it from the series — the archive file is kept, and the filter menu's **Removed** option shows removed chapters so they can be restored.
- **Collections:** group series into your own collections, shown as tabs on the home screen with a count on each; swipe sideways to move between them. A series can be in several. Long-press a tab to rename or delete it; deleting never removes the series. Settings → Library & collections lists them all, with buttons to create, rename and delete.
- **Continue Reading widget:** the series you're partway through on your home screen (one cover on a small widget, up to four on a wide one). Tap one to open it at your saved page. Covers are hidden while the PIN lock is on.
- **Reading statistics:** time read, streaks, chapters and pages read, and personal records. Can be turned off or reset.
- **[AniList](https://anilist.co/) tracking (optional):** link a series and finished chapters update your list. Progress read offline syncs on the next launch.
- **Backup and restore:** save reading progress, read chapters, statistics, favorites, collections, chapter renames, custom covers, AniList links and settings to one `.zip` from Settings, and restore it after a reinstall or on another phone. Pick your library folder first; progress is matched to the same files inside it. Comic files, your PIN and the AniList login aren't included.
- **Settings:** one short list grouped into Library, Look & reading, Tracking and App. Each row shows its current value and opens its own page.
- **Themes and lock:** Material 3 with accent presets or dynamic color, black/gray/light reader backgrounds, and an optional PIN lock.

## Supported formats

| Format                | Status                                                    |
| :-------------------- | :-------------------------------------------------------- |
| `.cbz`, `.zip`        | ✅ Supported                                              |
| `.cbr`, `.rar`, `.7z` | ✅ Supported (RAR4 and RAR5); page turns can lag in big solid archives |

Pages can be any common image format (`.jpg`, `.png`, `.webp`, …).

## Organizing your library

Moon Reader detects series in either layout:

```text
Manga/                          Comics/
└── Solo Leveling/              ├── Horimiya - Vol 1.cbz
    ├── cover.jpg   (optional)  ├── Horimiya - Vol 2.cbz
    ├── Chapter 01.cbz          └── Horimiya - Vol 3.cbz
    └── Chapter 02.cbz
```

Files named as volumes (`Vol 3`, `v03`) update the volume count on AniList instead of chapters.

## Privacy

- Folder access is limited to the folders you pick. The app never requests `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`; `WRITE_EXTERNAL_STORAGE` is only requested on Android 9 and below, to save pages.
- The only network use is AniList, and only after you log in. The login token stays on the device and is excluded from backups.
- No analytics, ads or crash reporting.

## Building from source

You need JDK 21, Gradle 9.3.1 and an Android SDK with platform 36.1. The repo has no Gradle wrapper, so use a system `gradle` or Android Studio.

```sh
git clone https://github.com/Moon-Ecosystem/MoonReader.git
cd MoonReader
gradle :app:testDebugUnitTest   # unit tests
gradle :app:assembleDebug       # debug APK (needs debug.keystore at the repo root)
```

Built with Kotlin, Jetpack Compose (Material 3), Room and Coil. Pages are read straight out of the archive by a small random-access ZIP reader.

## Contributing

Pull requests are welcome; for larger changes, open an issue first. Development happens on `dev`, and `main` is release-only since every merge to it ships a build. Commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## Disclaimer

Moon Reader hosts no content. It only reads files you already have on your device.

## License

Distributed under the [MIT License](LICENSE).

# 📖 MangaReader

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="UI: Jetpack Compose" />
  <img src="https://img.shields.io/badge/Design-Material%20Design%203-7C3AED?style=for-the-badge" alt="Design: Material 3" />
  <img src="https://img.shields.io/badge/Database-Room%20SQLite-47A248?style=for-the-badge&logo=sqlite&logoColor=white" alt="Database: Room" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License: MIT" />
</p>

<p align="center">
  <strong>A fast, local-first, privacy-focused manga & comic reader built natively for Android.</strong><br />
  Read your local <code>.cbz</code> and <code>.zip</code> comic archives with no ads, no accounts and no tracking.
</p>

---

## 🌟 Highlights

- **⚡ Offline & Private:** No accounts, no analytics. The only network use is optional AniList tracking. Your library stays on your device.
- **📂 Storage Access Framework:** Pick any folder on your device or SD card. Subfolders and archives are scanned automatically, with persistent permission.
- **🖼️ Streamed Pages:** Pages are read straight out of the archive on demand. Nothing is unzipped to disk.
- **🔍 Mihon-style Zoom:** Pinch-zoom up to 5×, centred on your fingers. Pan 1:1 with fling, and double-tap to zoom to 2× where you tapped.
- **📤 Open with MangaReader:** Open a `.cbz` straight from your file manager or browser without adding it to the library.
- **🖼️ Long-Press Page Actions:** Set the page as the cover, copy it to the clipboard or save it to your gallery.
- **📊 Reading Statistics:** Reading time, streaks, chapters and pages read, and personal records.
- **🔗 AniList Tracking:** Optional, Mihon-style. Link a series and finishing chapters updates your AniList list.
- **🔒 App Lock:** Protect your library with a 4-digit PIN.

---

## ✨ Features

### 📖 Reader
- **3 Reading Modes:**
  - **Right to Left (Manga):** Japanese reading order with tap-to-turn.
  - **Left to Right:** Western comic layout.
  - **Vertical Continuous (Webtoon):** Seamless vertical scroll for manhwa and webcomics.
- **Immersive Mode:** The status bar hides while you read.
- **Zoom & Pan:** Pinch to zoom (1×–5×). Panning follows your finger 1:1 and flings. Double-tap toggles between 1× and 2× around the tap point.
- **Long-Press Menu:** Hold in the centre of a page to:
  1. *Set as cover:* updates the series thumbnail immediately.
  2. *Copy:* puts the page image on the clipboard.
  3. *Save:* saves the full-resolution page to your gallery through `MediaStore`.
- **Navigation Footer:** Progress slider with a page badge (`p. 14 / 45`) and previous/next chapter buttons.
- **Background Themes:** Pure Black, Dark Gray and Light.
- **Keep Screen On:** Optional toggle that stops the screen sleeping while you read.
- **Resume Anywhere:** Reading progress is saved per chapter.

### 📚 Library
- **Automatic Discovery:** Scans folders recursively to find series, subfolders and chapter archives.
- **ComicInfo.xml Support:** Reads series metadata from the ComicRack format (used by Mihon, Komga and Kavita).
- **Natural Chapter Sorting:** `Ch. 1`, `Ch. 2`, …, `Ch. 9`, `Ch. 10`.
- **Continue Reading:** A floating button on the Home screen jumps back to your last chapter.
- **Filter Tabs:** All, Reading, Favorites and Unread.
- **Sorting:** Sort by last read, title, total chapters or unread chapters.
- **Search:** Filters the library in real time.
- **Custom Covers:** Set a cover from inside the reader, or pick any image with the Android Photo Picker.

### 📊 Statistics
- Total reading time, current streak, chapters read, pages read, active days and series read.
- Records: most active day, longest single session, and your best day for pages and for chapters.
- Every chapter you read is recorded, including ones reached with next/previous chapter.
- A session is saved when you leave the reader or send the app to the background.
- Returning to the same chapter within 5 minutes continues the previous session.
- **Inactivity Auto-Pause:** The timer can pause when you stop interacting with the reader.
- Tracking can be turned off, and statistics can be reset from Settings.

### 🔗 AniList Tracking
- Log in from **Settings → Tracking**; the login happens in your browser and the token stays on the device (excluded from backups).
- Tap **AniList** on a series to search and link it. The search is pre-filled with the ComicInfo series name or a cleaned-up folder name: `Frankenstein (2018) (Digital) (1r0n)` → `Frankenstein`. Results from the same year are listed first.
- Edit status, chapters, volumes, score and start/finish dates from the same sheet.
- Finishing a chapter raises AniList progress, never lowers it:
  - Planning becomes Reading.
  - Reaching the last chapter marks the series Completed.
  - Files named as volumes (`Vol 3`, `v03`) update the volume count instead.
- Progress read offline is sent the next time the app starts.

### 🎨 Appearance & Security
- Material 3 with accent presets: Night Blue, Crimson Red, Cyber Purple, Emerald, Warm Rust, Coral Pink and System Dynamic.
- 4-digit PIN lock that locks the app again when you switch away.

---

## 🛠️ Architecture & Tech Stack

Single-activity **MVVM** app: Compose screens collect `StateFlow`s from one `MangaViewModel`, which talks to repositories backed by Room and the Storage Access Framework.

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Single activity; edge-to-edge setup, "Open with" intents
├── MangaReaderApp.kt                # Application class; Coil ImageLoader with the CBZ page fetcher
├── data/
│   ├── Models.kt                    # Domain models (Manga, Chapter, ReadingDirection, ReaderTheme, …)
│   ├── StatisticsModels.kt          # Statistics domain models
│   ├── db/                          # Room: AppDatabase (+ migrations), Entities, MangaDao, StatisticsDao
│   ├── preferences/                 # PreferencesManager (theme, PIN, reading prefs)
│   ├── repository/                  # MangaRepository, StatisticsRepository
│   └── storage/                     # MangaScanner (SAF), ChannelZip/CbzArchive (ZIP reading),
│                                    # CbzPageFetcher (Coil), ComicInfoParser, CoverImages, natural sort
└── ui/
    ├── MangaApp.kt                  # Root scaffold, navigation and transitions
    ├── MangaViewModel.kt            # App state (library, reader, reading sessions)
    ├── components/                  # Shared widgets (PIN lock, continue-reading card, …)
    ├── screens/                     # Home, ChapterList, Reader, Search, Settings, Statistics
    └── theme/                       # Material 3 colors and typography
```

### Tech Stack
- **Language:** [Kotlin](https://kotlinlang.org/) with Coroutines and Flow
- **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3
- **Database:** [Room](https://developer.android.com/training/data-storage/room) 2.7 with KSP
- **Image Loading:** [Coil](https://coil-kt.github.io/coil/) 2.7 with a custom fetcher that decodes pages straight from the archive
- **Files:** Storage Access Framework, `MediaStore` and `FileProvider`
- **Archives:** A custom random-access ZIP reader over the file channel. Only the page you are viewing is read.

---

## 📦 Supported Formats

| Format | Extension | Status |
| :--- | :--- | :--- |
| **Comic Book ZIP** | `.cbz` | ✅ Supported |
| **ZIP** | `.zip` | ✅ Supported |
| **Comic Book RAR / RAR / 7-Zip** | `.cbr`, `.rar`, `.7z` | ⚠️ Listed in the library, but pages can't be opened yet |
| **Images inside archives** | `.jpg`, `.png`, `.webp`, … | ✅ Supported |

---

## 🚀 Getting Started

### Install
Download the latest APK from the repository's [Releases](../../releases) page. Every merge to `main` publishes a new signed build.

Requires Android 7.0 (API 24) or newer.

### Build from Source

Prerequisites: JDK 21, Gradle 9.3.1 and an Android SDK with platform 36.1. The repo has no Gradle wrapper, so use a system `gradle`, or open the project in Android Studio.

```bash
git clone https://github.com/Varun-666/MangaReader.git
cd MangaReader

gradle :app:testDebugUnitTest   # run unit tests
gradle :app:assembleDebug       # build a debug APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

> The debug build is signed with `debug.keystore` at the repo root. It isn't committed, so create one with `keytool` or point the `debugConfig` signing config at your own keystore.

---

## 📂 Folder Structure

MangaReader detects series laid out either way:

**Option A: One folder per series**
```text
MyManga/
└── Solo Leveling/
    ├── cover.jpg              # Optional series cover
    ├── Chapter 01.cbz
    ├── Chapter 02.cbz
    └── Chapter 03.cbz
```

**Option B: Archives directly in a folder**
```text
Comics/
├── Horimiya - Vol 1.cbz
├── Horimiya - Vol 2.cbz
└── Horimiya - Vol 3.cbz
```

---

## 🔒 Privacy & Permissions

- **No Broad Storage Permissions:** The app never asks for `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`. `WRITE_EXTERNAL_STORAGE` is requested only on Android 9 and below, to save pages to the gallery.
- **Scoped Folder Access:** Android's folder picker (`OpenDocumentTree`) grants access only to the folder you choose.
- **Gallery Export:** Pages are saved through `MediaStore.Images`.
- **Network Only for AniList:** The app has the `INTERNET` permission solely for AniList tracking, and contacts AniList only after you log in. No analytics SDKs, ad networks or telemetry.

---

## 📄 License

Distributed under the **MIT License**.

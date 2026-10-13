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
  <strong>An ultra-fast, local-first, privacy-focused manga & comic reader engineered natively for Android.</strong><br />
  Read your local <code>.cbz</code>, <code>.zip</code>, and comic archives with zero ads, zero tracking, and hardware-accelerated rendering.
</p>

---

## 🌟 Highlights

- **⚡ 100% Offline & Private:** No accounts, no analytics, no external network requests. Your library stays securely on your device.
- **📂 Seamless Storage Access (SAF):** Point to any folder on your device or SD card. Reads subdirectories and archives automatically with persistent permissions.
- **🖼️ Native Decompression:** High-performance, on-demand streaming extraction for `.cbz`, `.zip`, `.cbr`, `.rar`, and `.7z` archives without unzipping entire series onto your disk.
- **🔍 Multi-Touch Pinch Zoom:** Smooth zooming from 1.0× to 4.5× with fluid panning and double-tap zoom toggling.
- **📱 True Immersive Full-Screen:** Mobile status bar automatically vanishes when entering reader mode for distraction-free reading.
- **🖼️ Long-Press Page Actions:** Tap and hold anywhere in the center to set any page as the manga's cover art or export full-res pages directly to your device's Gallery/Photos app.
- **🎨 Sleek & Compact UI:** Built with Material Design 3, dynamic theme accents, OLED black backgrounds, and ultra-slim headers/footers designed for maximum screen real estate.
- **🔒 App Lock Security:** Protect your private reading library with a built-in 4-digit PIN security keypad.

---

## ✨ Features

### 📖 Reader Engine
- **3 Reading Orientations:**
  - **Manga (Right-to-Left):** Authentic Japanese reading flow with tap-to-turn.
  - **Paged (Left-to-Right):** Standard Western comic book layout.
  - **Webtoon (Continuous Vertical):** Seamless vertical scroll for Korean manhwa and webcomics.
- **Immersive Mode:** System status bar hides automatically upon entering reading mode, maximizing visible artwork area.
- **Interactive Multi-Touch Zoom:** Pinch in and out to zoom into fine details (up to 4.5×). Double-tap quickly toggles between 1× and 2.5× zoom.
- **Center Tap-and-Hold Menu:** Long-press in the center of any page to:
  1. *Set Current Page as Cover* — updates the series thumbnail immediately.
  2. *Save Page to Gallery* — saves the pristine, full-resolution image to device storage via Android `MediaStore`.
- **Sleek Navigation Footer:** Compact 28dp progress slider with live page badge indicator (`p. 14 / 45`) and quick chapter skip buttons.
- **Background Themes:** Pure OLED Black (`#000000`), Dark Obsidian (`#141418`), and Paper Light (`#F7F7F7`).
- **Screen Wake Lock:** Optional toggle to keep the screen on while reading.

### 📚 Library & Management
- **Automatic Archive Discovery:** Deep recursive folder scanning identifies all series, subfolders, and chapter archives.
- **Natural Chapter Sorting:** Alphanumerically orders chapters correctly (`Ch. 1`, `Ch. 2`, `Ch. 9`, `Ch. 10`).
- **One-Tap Resume Reading:** Circular floating action button on the Home screen to jump straight back into your last read position.
- **Library Filter Tabs:** Filter between **Latest**, **Reading** (in-progress), **Favorites**, and **Unread**.
- **Instant Search:** Client-side real-time filtering across all indexed series.
- **Zero-Lag Chapter Listing:** Pure text chapter badges (`#1`, `#2`, `#140`) for 120Hz smooth scrolling without image decoding stutter.
- **Custom Cover Art:** Set covers from inside the reader or choose any image file from device storage via Android Photo Picker (`PickVisualMedia`).

### 🛡️ Privacy & Storage
- **4-Digit PIN Vault:** Gate entry to the app with PIN verification and instant auto-lock when switching apps.
- **Zero Dangerous Permissions:** Built strictly with modern Android APIs (SAF & MediaStore), requiring zero broad storage permissions.
- **Cache Management:** Monitor decompression cache size in Settings and flush cache files with a single tap.

---

## 🛠️ Architecture & Tech Stack

The application is built following **Clean Architecture** and the **MVVM (Model-View-ViewModel)** pattern:

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Single-activity container with edge-to-edge system setup
├── data/                            # DATA LAYER
│   ├── Models.kt                    # Domain models (Manga, Chapter, ReadingDirection, ReaderTheme)
│   ├── db/                          # Room Database (Entities, DAOs, Migrations)
│   │   ├── MangaDao.kt
│   │   ├── MangaDatabase.kt
│   │   └── MangaEntity.kt
│   ├── preferences/                 # PreferencesManager (Theme, PIN, Reading Direction)
│   ├── repository/                  # MangaRepository (Single source of truth combining SAF & Room)
│   └── storage/                     # CbzExtractor & MangaScanner (Archive streaming engine)
├── ui/                              # UI LAYER (Jetpack Compose + MVVM)
│   ├── MangaApp.kt                  # Root scaffold, navigation bar & animated transitions
│   ├── MangaViewModel.kt            # Unidirectional state holder (StateFlow & Coroutines)
│   ├── components/                  # Reusable UI widgets (PIN dialog, lock screen)
│   ├── screens/                     # Feature screens
│   │   ├── HomeScreen.kt            # Library grid, compact header & resume FAB
│   │   ├── ChapterListScreen.kt     # Series details, cover changer & chapter list
│   │   ├── ReaderScreen.kt          # Immersive reader, pinch zoom & tap-and-hold sheet
│   │   ├── SearchScreen.kt          # Real-time library search
│   │   └── SettingsScreen.kt        # App lock, theme presets & cache controls
│   └── theme/                       # Material 3 Theme (Colors, Type, Shapes)
```

### Tech Stack Details:
- **Language:** [Kotlin](https://kotlinlang.org/) (Coroutines, StateFlow, Flows)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3
- **Local Database:** [Room](https://developer.android.com/training/data-storage/room) 2.6 with Kotlin Symbol Processing (KSP)
- **Image Loading:** [Coil Compose](https://coil-kt.github.io/coil/) 2.7 with memory caching & subcomposition
- **File Management:** Android Storage Access Framework (SAF) + MediaStore API
- **Archive Engine:** Native `ZipInputStream` & buffered file streams with deterministic cache hashing

---

## 📦 Supported Formats

| Format | Extension | Support Status |
| :--- | :--- | :--- |
| **Comic Book ZIP** | `.cbz` | ✅ Full Native Support |
| **Standard ZIP** | `.zip` | ✅ Full Native Support |
| **Comic Book RAR** | `.cbr` | ✅ Full Archive Discovery |
| **RAR Archive** | `.rar` | ✅ Full Archive Discovery |
| **7-Zip Archive** | `.7z` | ✅ Full Archive Discovery |
| **Standalone Images** | `.jpg`, `.png`, `.webp` | ✅ Supported for covers & chapters |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** or higher
- **Android Device / Emulator** running Android 8.0 (API 26) or higher

### Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/manga-reader.git
   cd manga-reader
   ```

2. **Open in Android Studio:**
   - Open Android Studio, select **Open**, and navigate to the cloned project root.
   - Allow Gradle to sync dependencies.

3. **Build the APK:**
   ```bash
   # Linux / macOS
   gradle assembleDebug

   # Windows
   gradle.bat assembleDebug
   ```

4. **Install onto your connected device:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📂 Folder Structure Example

MangaReader automatically detects series structured in either format:

**Option A: Series Folder with Chapters**
```text
MyManga/
└── Solo Leveling/
    ├── cover.jpg              # Optional series cover
    ├── Chapter 01.cbz
    ├── Chapter 02.cbz
    └── Chapter 03.cbz
```

**Option B: Direct Archives in Folder**
```text
Comics/
├── Horimiya - Vol 1.cbz
├── Horimiya - Vol 2.cbz
└── Horimiya - Vol 3.cbz
```

---

## 🔒 Privacy & Permissions

MangaReader complies strictly with Google Play Developer Program policies:
- **No Broad Storage Permissions:** Does not request `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`.
- **Scoped Document Trees:** Uses Android's native directory picker (`OpenDocumentTree`) to access only the folder you choose.
- **Scoped Media Export:** Uses Android's `MediaStore.Images` API to save pages directly to your Photos folder without requiring write permissions on modern Android versions.
- **Zero Network Traffic:** The app contains no analytics SDKs, ad networks, or background telemetry.

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

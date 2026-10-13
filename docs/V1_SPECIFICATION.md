# Solo Manga Reader — Version 1.0.0 Product & Technical Specification

> **Platform:** Android 8.0+ (API 26 to API 35)  
> **Status:** Production V1.0.0 Release  
> **Architecture:** Modern MVVM + Clean Architecture + Jetpack Compose  
> **Generated PDF File:** `SOLO_MANGA_READER_V1_SPECIFICATION.pdf` (in root directory and `docs/`)

---

## 1. Executive Summary
**Solo Manga Reader V1.0.0** is an ultra-fast, 100% offline, privacy-first manga, manhwa, and comic book reader engineered natively for Android. The application solves critical pain points in modern mobile comic readers by removing intrusive advertising, eliminating network tracking, and providing instant local streaming decompression of `.CBZ` and `.ZIP` archives through Android's modern Storage Access Framework (SAF).

---

## 2. Complete Technology Stack
| Layer / Role | Technology | Description & Value |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.0+ | Modern Kotlin DSL, Coroutines, StateFlow |
| **UI Toolkit** | Jetpack Compose + M3 | Material Design 3 dynamic colors, responsive grids |
| **State Architecture** | MVVM | Unidirectional Data Flow (`ViewModel` + `StateFlow`) |
| **Persistence Database** | Room 2.6 (KSP) | SQLite ORM with reactive Flow observers & atomic upserts |
| **Preferences Storage** | SharedPreferences | Fast key-value persistence for theme, folder URI & PIN |
| **Archive Engine** | `ZipInputStream` / `BufferedInputStream` | High-throughput streaming decompression |
| **File Access** | Storage Access Framework (SAF) | Secure document tree access (`content://`) & local paths |
| **Image Pipeline** | Coil Compose 2.7 | Bitmap subcomposition, disk and memory caching |
| **Sorting Engine** | NaturalOrderComparator | Alphanumeric sequence sorting (e.g., Ch 1, 2, 9, 10) |
| **Security Vault** | 4-Digit PIN Lock | Private reading history encryption & keypad |

---

## 3. Comprehensive Feature Matrix

### 3.1 Library & Home Screen
- **Dynamic 5-Slide Animated Carousel:** Auto-advancing 4.2-second hero carousel with smooth `FastOutSlowInEasing` transitions, active touch-pause, and dynamic pill indicators across 5 themes (*Read More Live More*, *Solo Action Hits*, *Reader Engine*, *CBZ Storage*, *PIN Security*).
- **Tactile Manga Card Grid:** Multi-column adaptive grid displaying high-resolution covers, chapter counters, unread badges, and press animations.
- **Continue Reading Quick-Action Card:** Prominent hero widget showcasing the last opened manga, chapter title, page progress, and a one-tap resume action.
- **Library Filter Tabs:** Instant client-side filtering by **All**, **In Progress**, **Completed**, and **Favorites**.

### 3.2 CBZ Archive & Scanner Engine
- **In-Folder Cover Detection:** Automatically detects standalone image files (`cover.jpg`, `cover.png`, `folder.jpg`, `poster.webp`, etc.) located inside the manga directory.
- **Defensive Cover Persistence:** Custom covers selected via the app or found in the folder are strictly preserved and never overwritten by subsequent folder scans.
- **Natural Chapter Ordering:** Intelligent alphanumeric ordering ensuring chapters are ordered logically (`Ch. 1`, `Ch. 2`, `Ch. 9`, `Ch. 10`).
- **Selective Stream Extraction:** Pages are decompressed on-demand to isolated application cache with deterministic hashing.

### 3.3 Immersive Full-Screen Reader
- **3 Reading Orientations:** Right-to-Left (traditional Japanese manga), Left-to-Right (Western comics), and Continuous Vertical Webtoon scroll.
- **4 OLED Eye-Care Themes:** Pure OLED Black (`#000000`), Obsidian Midnight (`#121218`), Warm Sepia (`#2A241E`), and Paper White (`#F8F9FA`).
- **Interactive Multi-Touch Zoom:** Pinch-to-zoom scaling up to 4.0x, double-tap quick toggle, and smooth panning across splash pages.
- **Precision Floating Scrubber:** Bottom slider with page indicator and instant seek jumps across chapters.
- **Auto-State Memory:** Automatically records page positions and read statuses to Room asynchronously.

### 3.4 Customization & Security Vault
- **4-Digit PIN Lock Screen:** Private vault gating app entry with numeric pad and shake feedback.
- **5 Material 3 Dynamic Accent Presets:** Night Blue, Crimson Red, Cyber Purple, Emerald Teal, and Amber Gold.
- **Cache Management:** Real-time disk cache calculation and one-tap cache flush.
- **Custom Artwork Picker:** Select custom covers from device storage via Android Photo Picker (`PickVisualMedia`).

---

## 4. Room Database Schema
### Table: `manga_library` (`MangaEntity`)
- `id` (PK, TEXT): Folder URI / Unique identifier string
- `title` (TEXT): Series title
- `folderUriString` (TEXT): Root folder URI
- `coverPath` (TEXT, Nullable): File path to custom or cached cover image
- `isFavorite` (INTEGER): Favorite bookmark flag
- `lastReadChapterId` (TEXT, Nullable): ID of most recently opened chapter
- `lastReadChapterTitle` (TEXT, Nullable): Formatted chapter title
- `lastReadPage` (INTEGER): Last read page index (0-based)
- `lastReadTime` (INTEGER): Timestamp in milliseconds
- `totalChapters` (INTEGER): Total number of valid chapters

### Table: `chapter_progress` (`ChapterProgressEntity`)
- `chapterId` (PK, TEXT): Chapter URI / file path
- `mangaId` (TEXT): Parent manga ID
- `chapterTitle` (TEXT): Formatted chapter title
- `lastReadPage` (INTEGER): Highest page reached
- `totalPages` (INTEGER): Total verified pages in CBZ
- `isRead` (INTEGER): Completion flag
- `isBookmarked` (INTEGER): Chapter bookmark
- `lastUpdatedTime` (INTEGER): Timestamp of write

---

## 5. Google Play Policy Compliance
- **Zero Dangerous Permissions:** Operates without `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`.
- **System Photo Picker:** Uses zero-permission `PickVisualMedia` for custom cover selection.
- **Dynamic Code Loading (DCL) Ban:** No dynamic executable code loading; 100% native Kotlin binaries.
- **Privacy:** 100% local processing; zero tracking or telemetry.

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.Chapter
import com.example.data.ChapterFilter
import com.example.data.Manga
import com.example.ui.MangaViewModel
import com.example.ui.visibleChapters
import com.example.ui.components.stableStatusBars
import com.example.ui.components.TrackingSheet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListScreen(
    manga: Manga,
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BackHandler {
        viewModel.closeManga()
    }

    val chapters by viewModel.currentChapters.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortChaptersAscending.collectAsStateWithLifecycle()
    // Real metadata from the first chapter's ComicInfo.xml; null when the archive has none
    val info by viewModel.selectedMangaInfo.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showFullCover by remember { mutableStateOf(false) }
    if (showFullCover && !manga.coverPath.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showFullCover = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showFullCover = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(manga.coverPath),
                    contentDescription = "Cover for ${manga.title}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    var isSummaryExpanded by remember(manga.id) { mutableStateOf(false) }
    val track by viewModel.selectedTrack.collectAsStateWithLifecycle()
    var showTrackingSheet by remember { mutableStateOf(false) }
    if (showTrackingSheet) {
        TrackingSheet(manga = manga, viewModel = viewModel, onDismiss = { showTrackingSheet = false })
    }

    // Photo Picker for changing individual manga cover image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomCoverFromUri(context, manga.id, uri)
        }
    }

    val readCount = chapters.count { it.isRead }
    val aniListUser by viewModel.preferencesManager.aniListUserName.collectAsStateWithLifecycle()
    val removed by viewModel.removedChapters.collectAsStateWithLifecycle()

    // Long-press a chapter to rename or remove it; the dialogs are held here so one is open at a time
    var chapterToRename by remember { mutableStateOf<Chapter?>(null) }
    var chapterToRemove by remember { mutableStateOf<Chapter?>(null) }
    chapterToRename?.let { chapter ->
        RenameChapterDialog(
            chapter = chapter,
            onDismiss = { chapterToRename = null },
            onRename = { title ->
                viewModel.renameChapter(chapter, title)
                chapterToRename = null
            }
        )
    }
    chapterToRemove?.let { chapter ->
        RemoveChapterDialog(
            chapter = chapter,
            onDismiss = { chapterToRemove = null },
            onRemove = {
                viewModel.deleteChapter(chapter)
                chapterToRemove = null
            }
        )
    }

    // What the list shows; starts over for each series
    var chapterFilter by rememberSaveable(manga.id) { mutableStateOf(ChapterFilter.ALL) }
    var bookmarkedOnly by rememberSaveable(manga.id) { mutableStateOf(false) }
    var showRemoved by rememberSaveable(manga.id) { mutableStateOf(false) }
    var byLastRead by rememberSaveable(manga.id) { mutableStateOf(false) }
    val filtered = chapterFilter != ChapterFilter.ALL || bookmarkedOnly || showRemoved
    // Removed chapters come last: they are offered back, not part of the series any more
    val shownChapters = visibleChapters(chapters, chapterFilter, bookmarkedOnly, byLastRead) +
        (if (showRemoved) removed else emptyList())
    var showFilterMenu by remember { mutableStateOf(false) }

    // The bar is see-through over the backdrop and turns solid once the list scrolls under it
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Find next chapter to read
    val nextChapter = chapters.firstOrNull { it.id == manga.lastReadChapterId }
        ?: chapters.firstOrNull { !it.isRead }
        ?: chapters.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                // The title is shown large beside the cover
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.closeManga() },
                        modifier = Modifier.testTag("chapter_list_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showFilterMenu = true },
                            modifier = Modifier.testTag("chapter_filter_button")
                        ) {
                            // Tinted while filtering; the chips above the list say which filters are on
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = if (filtered) "Filter and sort chapters (filter on)" else "Filter and sort chapters",
                                tint = if (filtered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Stays open so several options can be set in one go, like the library's menu
                        DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                    MenuSectionLabel("Show")
                                    ChapterFilter.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.displayName) },
                                            leadingIcon = { RadioButton(selected = option == chapterFilter, onClick = null) },
                                            onClick = { chapterFilter = option },
                                            modifier = Modifier.testTag("chapter_filter_${option.name}")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Bookmarked") },
                                        leadingIcon = { Checkbox(checked = bookmarkedOnly, onCheckedChange = null) },
                                        onClick = { bookmarkedOnly = !bookmarkedOnly },
                                        modifier = Modifier.testTag("chapter_filter_bookmarked")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Removed") },
                                        leadingIcon = { Checkbox(checked = showRemoved, onCheckedChange = null) },
                                        onClick = { showRemoved = !showRemoved },
                                        modifier = Modifier.testTag("chapter_filter_removed")
                                    )
                                }
                                VerticalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                    MenuSectionLabel("Sort by")
                                    // Tapping Number again flips its direction
                                    ChapterSortOption(
                                        label = "Number",
                                        active = !byLastRead,
                                        ascending = sortAscending,
                                        onClick = { if (byLastRead) byLastRead = false else viewModel.toggleSortOrder() },
                                        modifier = Modifier.testTag("chapter_sort_number")
                                    )
                                    ChapterSortOption(
                                        label = "Last read",
                                        active = byLastRead,
                                        ascending = null,
                                        onClick = { byLastRead = true },
                                        modifier = Modifier.testTag("chapter_sort_last_read")
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite(manga) },
                        modifier = Modifier.testTag("manga_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (manga.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (manga.isFavorite) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // More Options Menu for changing cover
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change Cover Image") },
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Re-scan Chapters") },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoStories, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.scanLibrary()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior,
                // Default insets follow reported status bar visibility, which can stay "hidden" after the reader
                windowInsets = WindowInsets.stableStatusBars.union(TopAppBarDefaults.windowInsets)
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        // The backdrop runs up behind the top bar, so only the bottom inset pads the list
        val topBarHeight = innerPadding.calculateTopPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .testTag("chapter_list_view")
        ) {
            // Hero: blurred cover backdrop with the cover, title and counts
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarHeight + 220.dp)
                ) {
                    // Blurred background artwork
                    if (!manga.coverPath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(manga.coverPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(28.dp)
                        )
                    }

                    // Scrim that fades into the page background, so the backdrop has no visible bottom edge
                    val pageBackground = MaterialTheme.colorScheme.background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to pageBackground.copy(alpha = 0.25f),
                                    0.55f to pageBackground.copy(alpha = 0.6f),
                                    1f to pageBackground
                                )
                            )
                    )

                    // Hero Content Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Large Cover Image with "Change Cover" badge
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 155.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = !manga.coverPath.isNullOrBlank()) { showFullCover = true }
                                .testTag("cover_image")
                        ) {
                            if (!manga.coverPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = File(manga.coverPath),
                                    contentDescription = "Cover for ${manga.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.Center)
                                )
                            }

                            // Tap to change cover badge icon
                            Surface(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .testTag("change_cover")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Change Cover",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Title, Subtitle, Tags
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Only facts from the files: chapter count, plus ComicInfo.xml fields when present
                            Text(
                                text = (if (chapters.size == 1) "1 chapter" else "${chapters.size} chapters") + " · $readCount read",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("chapter_counts")
                            )

                            val credits = info?.let { i ->
                                listOfNotNull(
                                    i.writer,
                                    i.artist?.takeIf { it != i.writer },
                                    i.year?.toString(),
                                    i.publisher
                                ).joinToString(" · ")
                            }.orEmpty()
                            if (credits.isNotEmpty()) {
                                Text(
                                    text = credits,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val genres = info?.genres.orEmpty()
                            if (genres.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    genres.forEach { GenreChip(label = it) }
                                }
                            }
                        }
                    }
                }
            }

            // Summary, then the AniList and Continue buttons
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    val summary = info?.summary
                    if (summary != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isSummaryExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSummaryExpanded = !isSummaryExpanded }
                        )
                        Text(
                            text = if (isSummaryExpanded) "Show less" else "Read more",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { isSummaryExpanded = !isSummaryExpanded }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // AniList only for users who logged in; tracking is optional and stays out of the way otherwise
                        if (aniListUser != null) {
                            TrackButton(
                                status = track?.let { t -> t.status?.displayName ?: "Linked" },
                                progress = track?.let { t ->
                                    if (t.totalChapters > 0) "${t.progress} / ${t.totalChapters} ch" else "${t.progress} ch"
                                },
                                onClick = { showTrackingSheet = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = { nextChapter?.let(viewModel::selectChapter) },
                            enabled = nextChapter != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("primary_read_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (manga.lastReadChapterTitle != null) "Continue" else "Start",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // The filters that are on, each removable with its ✕
                    if (filtered) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("chapter_active_filters")
                        ) {
                            if (chapterFilter != ChapterFilter.ALL) {
                                FilterChipWithClear(chapterFilter.displayName, { chapterFilter = ChapterFilter.ALL })
                            }
                            if (bookmarkedOnly) {
                                FilterChipWithClear("Bookmarked", { bookmarkedOnly = false })
                            }
                            if (showRemoved) {
                                FilterChipWithClear("Removed", { showRemoved = false })
                            }
                        }
                    }

                    if (filtered && shownChapters.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                                .testTag("chapter_filter_empty")
                        ) {
                            Text(
                                text = "No chapters match these filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                chapterFilter = ChapterFilter.ALL
                                bookmarkedOnly = false
                                showRemoved = false
                            }) { Text("Clear filters") }
                        }
                    }
                }
            }

            items(shownChapters, key = { it.id }) { chapter ->
                ChapterItemModern(
                    chapter = chapter,
                    manga = manga,
                    onChapterClick = { if (!chapter.isRemoved) viewModel.selectChapter(chapter) },
                    onBookmarkClick = { viewModel.toggleBookmark(chapter) },
                    onSetRead = { isRead -> viewModel.markChapterRead(chapter, isRead) },
                    onRename = { chapterToRename = chapter },
                    onRemove = { chapterToRemove = chapter },
                    onRestore = { viewModel.restoreChapter(chapter) }
                )
            }
        }
    }
}

@Composable
private fun GenreChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** AniList status and progress for a linked series ([status] non-null), or "Track" to link it. */
@Composable
private fun TrackButton(status: String?, progress: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier
            .height(50.dp)
            .testTag("tracking_button")
    ) {
        // Brand-blue "AL" mark; the app ships no AniList logo asset
        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E2A3A), modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("AL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF3DB4F2))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (status == null) {
            Text("Track", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        } else {
            Column {
                Text(status, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Text(progress, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ChapterSortOption(
    label: String,
    active: Boolean,
    ascending: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        trailingIcon = if (active && ascending != null) {
            {
                Icon(
                    imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (ascending) "Ascending" else "Descending",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        onClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class) // combinedClickable, for the long-press menu
@Composable
private fun ChapterItemModern(
    chapter: Chapter,
    manga: Manga,
    onChapterClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onSetRead: (Boolean) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit
) {
    // Long-press opens a menu to mark the chapter read/unread without opening it; only the read flag
    // changes, so the saved page position is kept. A removed chapter only offers to come back.
    var menuOpen by remember { mutableStateOf(false) }
    val readActionLabel = if (chapter.isRead) "Mark as Unread" else "Mark as Read"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (chapter.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .alpha(if (chapter.isRemoved) 0.6f else 1f)
            .combinedClickable(
                onClick = onChapterClick,
                onLongClickLabel = readActionLabel,
                onLongClick = { menuOpen = true }
            )
            .testTag("chapter_item_${chapter.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleek Chapter Number Badge (Pure text & shape, zero image decode overhead for 120Hz smooth scrolling)
            val chapterNumText = if (chapter.chapterNumber > 0) {
                if (chapter.chapterNumber % 1.0 == 0.0) "#${chapter.chapterNumber.toInt()}" else "#${chapter.chapterNumber}"
            } else {
                "#"
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (chapter.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(width = 46.dp, height = 38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = chapterNumText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (chapter.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chapter Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (chapter.isRead || chapter.isRemoved) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (chapter.isRead || chapter.isRemoved) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = when {
                        chapter.isRemoved -> "Removed from series"
                        chapter.isRead -> "Completed"
                        chapter.lastReadPage > 0 -> "Page ${chapter.lastReadPage + 1}"
                        else -> "Unread"
                    },
                    fontSize = 11.sp,
                    color = if (!chapter.isRemoved && chapter.lastReadPage > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // A removed chapter is not part of the series: only the restore action applies to it
            if (!chapter.isRemoved) {
                // Bookmark icon
                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Bookmark",
                        tint = if (chapter.isBookmarked) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Circular Play Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        if (chapter.isRemoved) {
            DropdownMenuItem(
                text = { Text("Restore to series") },
                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRestore()
                },
                modifier = Modifier.testTag("chapter_restore_${chapter.id}")
            )
        } else {
            DropdownMenuItem(
                text = { Text(readActionLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = if (chapter.isRead) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle,
                        contentDescription = null
                    )
                },
                onClick = {
                    menuOpen = false
                    onSetRead(!chapter.isRead)
                },
                modifier = Modifier.testTag("chapter_mark_read_${chapter.id}")
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
                modifier = Modifier.testTag("chapter_rename_${chapter.id}")
            )
            DropdownMenuItem(
                text = { Text("Remove from series") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
                modifier = Modifier.testTag("chapter_remove_${chapter.id}")
            )
        }
    }
    }
}

/** Renames a chapter in the list; the archive keeps its own file name. */
@Composable
private fun RenameChapterDialog(
    chapter: Chapter,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chapter") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Chapter name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chapter_rename_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(title) },
                modifier = Modifier.testTag("chapter_rename_confirm")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("chapter_rename_cancel")) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("chapter_rename_dialog")
    )
}

/**
 * Confirms taking a chapter out of the series. The archive file is never deleted, and the chapter can be
 * brought back from the list's "Removed" filter.
 */
@Composable
private fun RemoveChapterDialog(
    chapter: Chapter,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from series?") },
        text = {
            Text(
                "\"${chapter.title}\" is taken out of this series and its reading progress is cleared. " +
                    "The file stays on your device, and you can restore the chapter from the list's filter menu."
            )
        },
        confirmButton = {
            TextButton(onClick = onRemove, modifier = Modifier.testTag("chapter_remove_confirm")) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("chapter_remove_cancel")) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("chapter_remove_dialog")
    )
}

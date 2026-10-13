package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.example.data.Manga
import com.example.ui.MangaViewModel
import com.example.ui.components.stableStatusBars
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
    var showMenu by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    // Photo Picker for changing individual manga cover image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomCoverFromUri(context, manga.id, uri)
        }
    }

    val readCount = chapters.count { it.isRead }
    val progressPercent = if (chapters.isNotEmpty()) (readCount.toFloat() / chapters.size) else 0f

    // Find next chapter to read
    val nextChapter = chapters.firstOrNull { it.id == manga.lastReadChapterId }
        ?: chapters.firstOrNull { !it.isRead }
        ?: chapters.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = manga.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
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
                                text = { Text(if (sortAscending) "Sort Descending" else "Sort Ascending") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleSortOrder()
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
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                // Default insets follow reported status bar visibility, which can stay "hidden" after the reader
                windowInsets = WindowInsets.stableStatusBars.union(TopAppBarDefaults.windowInsets)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("chapter_list_view")
        ) {
            // Hero Header Section matching Figma design
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
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

                    // Dark gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0E0E12).copy(alpha = 0.5f),
                                        Color(0xFF0E0E12)
                                    )
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
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
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
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
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

                            Text(
                                text = "${chapters.size} Chapters • Offline Series",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Genre Tags
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TagChip(label = "Action")
                                TagChip(label = "Fantasy")
                                TagChip(label = "Manga")
                            }
                        }
                    }
                }
            }

            // Info Card & Synopsis
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Metadata card (Status, Chapters, Progress)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetaCol(label = "Status", value = "Ongoing")
                            MetaCol(label = "Chapters", value = "${chapters.size}")
                            MetaCol(label = "Read", value = "$readCount")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expandable Synopsis
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                    ) {
                        Text(
                            text = "In a world where hunters battle deadly monsters in mysterious dungeons, the journey unfolds with breathtaking battles and high-stakes encounters...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isSynopsisExpanded) 8 else 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }

                    Text(
                        text = if (isSynopsisExpanded) "Show Less ^" else "Read More v",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Primary Continue Reading Button
                    Button(
                        onClick = {
                            if (nextChapter != null) {
                                viewModel.selectChapter(nextChapter)
                            }
                        },
                        enabled = nextChapter != null,
                        modifier = Modifier
                            .fillMaxWidth()
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
                            text = if (manga.lastReadChapterTitle != null) "Continue: ${manga.lastReadChapterTitle}" else "Start Reading",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Chapters Section Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.toggleSortOrder() }
                        ) {
                            Text(
                                text = if (sortAscending) "Ascending >" else "Descending >",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Chapters List
            items(chapters, key = { it.id }) { chapter ->
                ChapterItemModern(
                    chapter = chapter,
                    manga = manga,
                    onChapterClick = { viewModel.selectChapter(chapter) },
                    onBookmarkClick = { viewModel.toggleBookmark(chapter) },
                    onSetRead = { isRead -> viewModel.markChapterRead(chapter, isRead) }
                )
            }
        }
    }
}

@Composable
private fun TagChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MetaCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class) // combinedClickable, for the long-press menu
@Composable
private fun ChapterItemModern(
    chapter: Chapter,
    manga: Manga,
    onChapterClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onSetRead: (Boolean) -> Unit
) {
    // Long-press opens a menu to mark the chapter read/unread without opening it; only the read flag
    // changes, so the saved page position is kept
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
                    fontWeight = if (chapter.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (chapter.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (chapter.isRead) "Completed" else if (chapter.lastReadPage > 0) "Page ${chapter.lastReadPage + 1}" else "Unread",
                    fontSize = 11.sp,
                    color = if (chapter.lastReadPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
    }
    }
}

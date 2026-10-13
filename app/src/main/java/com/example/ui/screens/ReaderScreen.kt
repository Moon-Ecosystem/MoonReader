package com.example.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.data.Chapter
import com.example.data.Manga
import com.example.data.ReaderTheme
import com.example.data.ReadingDirection
import com.example.ui.MangaViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    manga: Manga,
    chapter: Chapter,
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.closeChapter()
    }

    val context = LocalContext.current
    val pages by viewModel.readerPages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingPages.collectAsStateWithLifecycle()
    val readingDirection by viewModel.preferencesManager.readingDirection.collectAsStateWithLifecycle()
    val readerTheme by viewModel.preferencesManager.readerTheme.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.preferencesManager.keepScreenOn.collectAsStateWithLifecycle()

    // Handle Keep Screen On
    DisposableEffect(keepScreenOn) {
        val activity = context as? Activity
        if (keepScreenOn && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            val act = context as? Activity
            act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hide mobile status bar in reading mode (Full-screen immersive reading)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // No legacy FLAG_FULLSCREEN: mixed with the insets controller it left stale zero
            // status-bar insets after exit, so screens drew under the clock
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            val act = context as? Activity
            val win = act?.window
            if (win != null) {
                WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.statusBars())
                ViewCompat.requestApplyInsets(win.decorView)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle-aware Reading Session Tracking
    DisposableEffect(lifecycleOwner, chapter.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    viewModel.pauseReadingSession()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.resumeReadingSession()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.endReadingSession()
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Tap-and-hold (Long-press) Page Options Dialog State
    var showPageOptionsSheet by remember { mutableStateOf(false) }
    var selectedPageFile by remember { mutableStateOf<File?>(null) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }

    val onCenterLongPress: (pageIndex: Int, file: File) -> Unit = { pageIndex, file ->
        selectedPageIndex = pageIndex
        selectedPageFile = file
        showPageOptionsSheet = true
    }

    // Background color based on reader theme
    val readerBgColor = when (readerTheme) {
        ReaderTheme.BLACK -> Color.Black
        ReaderTheme.DARK_GRAY -> Color(0xFF141418)
        ReaderTheme.LIGHT -> Color(0xFFF7F7F7)
    }

    val readerTextColor = if (readerTheme == ReaderTheme.LIGHT) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBgColor)
            .testTag("reader_screen_container")
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Extracting CBZ pages…",
                        color = readerTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to decode chapter pages",
                        color = readerTextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Check that the .cbz file is a valid archive containing JPG, PNG, or WebP images.",
                        color = readerTextColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.selectChapter(chapter) }
                        ) {
                            Text("Retry Extraction")
                        }
                        IconButton(
                            onClick = { viewModel.closeChapter() },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go Back",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        } else {
            // Reader Content View (Paging or Webtoon) with Pinch Zoom and Tap & Hold
            val initialPageIndex = (chapter.lastReadPage).coerceIn(0, (pages.size - 1).coerceAtLeast(0))

            if (readingDirection == ReadingDirection.WEBTOON) {
                WebtoonReader(
                    pages = pages,
                    initialIndex = initialPageIndex,
                    onPageChanged = { viewModel.onPageChanged(it) },
                    onCenterTap = {
                        viewModel.onReaderUserInteraction()
                        showControls = !showControls
                    },
                    onCenterLongPress = onCenterLongPress
                )
            } else {
                PagedReader(
                    pages = pages,
                    initialIndex = initialPageIndex,
                    isRtl = readingDirection == ReadingDirection.RTL,
                    onPageChanged = { viewModel.onPageChanged(it) },
                    onCenterTap = {
                        viewModel.onReaderUserInteraction()
                        showControls = !showControls
                    },
                    onCenterLongPress = onCenterLongPress
                )
            }

            // Sleek & Slim Top Bar Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(androidx.compose.animation.core.tween(200)) + slideInVertically { -it },
                exit = fadeOut(androidx.compose.animation.core.tween(180)) + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color(0xF2121217),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.closeChapter() },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Reader",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color.LightGray.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleBookmark(chapter) },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_bookmark_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Bookmark Chapter",
                                tint = if (chapter.isBookmarked) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Reader Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Sleek & Slim Bottom Bar Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(androidx.compose.animation.core.tween(200)) + slideInVertically { it },
                exit = fadeOut(androidx.compose.animation.core.tween(180)) + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color(0xF2121217),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentPage by viewModel.currentReaderPage.collectAsStateWithLifecycle()
                    val total = pages.size

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        // Slim Slider Row with inline badge counter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { viewModel.onPageChanged(it.toInt()) },
                                valueRange = 0f..(total - 1).toFloat().coerceAtLeast(0f),
                                steps = (total - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .testTag("reader_page_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.height(22.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${currentPage + 1}/$total",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Compact Navigation Row (Previous, Reading Mode Pill, Next)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            // Previous Chapter Button
                            Surface(
                                onClick = { viewModel.goToPreviousChapter() },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                        contentDescription = "Previous Chapter",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Prev",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Direction Mode Pill (RTL / LTR / Webtoon)
                            Surface(
                                onClick = {
                                    val nextDir = when (readingDirection) {
                                        ReadingDirection.RTL -> ReadingDirection.LTR
                                        ReadingDirection.LTR -> ReadingDirection.WEBTOON
                                        ReadingDirection.WEBTOON -> ReadingDirection.RTL
                                    }
                                    viewModel.setReadingDirection(nextDir)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(26.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (readingDirection) {
                                            ReadingDirection.RTL -> "Manga (RTL)"
                                            ReadingDirection.LTR -> "Paged (LTR)"
                                            ReadingDirection.WEBTOON -> "Webtoon"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Next Chapter Button
                            Surface(
                                onClick = { viewModel.goToNextChapter() },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = "Next",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                        contentDescription = "Next Chapter",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tap & Hold Center Options Bottom Sheet: 1. Set as cover 2. Save to gallery
            if (showPageOptionsSheet && selectedPageFile != null) {
                PageOptionsBottomSheet(
                    pageNumber = selectedPageIndex + 1,
                    onDismiss = { showPageOptionsSheet = false },
                    onSetAsCover = {
                        val file = selectedPageFile
                        if (file != null) {
                            viewModel.setCustomCoverFromFile(manga.id, file)
                            Toast.makeText(context, "Cover updated to page ${selectedPageIndex + 1}", Toast.LENGTH_SHORT).show()
                        }
                        showPageOptionsSheet = false
                    },
                    onSaveToGallery = {
                        val file = selectedPageFile
                        if (file != null) {
                            val success = saveImageToGallery(
                                context = context,
                                file = file,
                                title = "${manga.title}_ch${chapter.chapterNumber}_p${selectedPageIndex + 1}"
                            )
                            if (success) {
                                Toast.makeText(context, "Page ${selectedPageIndex + 1} saved to Gallery", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save page to Gallery", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showPageOptionsSheet = false
                    }
                )
            }

            // Quick Settings Bottom Sheet
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    val currentPage by viewModel.currentReaderPage.collectAsStateWithLifecycle()
                    val currentPageFile = pages.getOrNull(currentPage)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Reader Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Option to set current page as manga cover
                        if (currentPageFile != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setCustomCoverFromFile(manga.id, currentPageFile)
                                    Toast.makeText(context, "Cover updated to page ${currentPage + 1}", Toast.LENGTH_SHORT).show()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set Current Page as Manga Cover")
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Reading Direction
                        Text(
                            text = "Reading Mode",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        ReadingDirection.values().forEach { dir ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setReadingDirection(dir) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = readingDirection == dir,
                                    onClick = { viewModel.setReadingDirection(dir) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = dir.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Theme
                        Text(
                            text = "Background Theme",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        ReaderTheme.values().forEach { theme ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setReaderTheme(theme) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = readerTheme == theme,
                                    onClick = { viewModel.setReaderTheme(theme) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = theme.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Tap & Hold Modal Sheet presenting the 2 requested options:
 * a. Set the current page as the cover
 * b. Save the page to gallery
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageOptionsBottomSheet(
    pageNumber: Int,
    onDismiss: () -> Unit,
    onSetAsCover: () -> Unit,
    onSaveToGallery: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16161D),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "p.$pageNumber",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Page $pageNumber Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Quick actions for the active page",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Option 1: Set as cover
            Surface(
                onClick = onSetAsCover,
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("option_set_cover")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set Current Page as Cover",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Use this artwork as the manga thumbnail cover",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Save page to gallery
            Surface(
                onClick = onSaveToGallery,
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("option_save_gallery")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save Page to Gallery",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Export original full-resolution image to Photos / Gallery",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Paged horizontal reader with:
 * - Pinch-in and pinch-out zoom
 * - Double tap to toggle zoom
 * - Tap and hold in the center to show page options (set cover, save to gallery)
 * - Tap left / right to turn page
 * - Tap center to toggle controls
 */
@Composable
fun PagedReader(
    pages: List<File>,
    initialIndex: Int,
    isRtl: Boolean,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: (pageIndex: Int, file: File) -> Unit
) {
    val totalPages = pages.size
    val safeInitial = initialIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { totalPages }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = isRtl,
        beyondViewportPageCount = 1, // decode neighbouring pages ahead of the swipe
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        val file = pages.getOrNull(pageIndex)
        if (file != null) {
            ZoomableMangaPage(
                file = file,
                pageIndex = pageIndex,
                onLeftTap = {
                    coroutineScope.launch {
                        val target = if (isRtl) pagerState.currentPage + 1 else pagerState.currentPage - 1
                        if (target in 0 until totalPages) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
                onRightTap = {
                    coroutineScope.launch {
                        val target = if (isRtl) pagerState.currentPage - 1 else pagerState.currentPage + 1
                        if (target in 0 until totalPages) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
                onCenterTap = onCenterTap,
                onCenterLongPress = { onCenterLongPress(pageIndex, file) }
            )
        }
    }
}

/**
 * Webtoon continuous vertical scroll reader with:
 * - Pinch-in and pinch-out zoom on each page
 * - Tap center to toggle header/footer controls
 * - Tap and hold in center to show page options (set cover, save to gallery)
 */
@Composable
fun WebtoonReader(
    pages: List<File>,
    initialIndex: Int,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: (pageIndex: Int, file: File) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            onPageChanged(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(pages, key = { _, file -> file.absolutePath }) { index, file ->
            ZoomableWebtoonPage(
                file = file,
                pageIndex = index,
                onCenterTap = onCenterTap,
                onCenterLongPress = { onCenterLongPress(index, file) }
            )
        }
    }
}

/**
 * Zoomable Manga Page with:
 * - Multi-touch pinch-to-zoom (1f to 4.5f)
 * - Single-finger pan when zoomed in
 * - Single-finger swipe handed off to HorizontalPager when not zoomed
 * - Double tap to toggle zoom 1x <-> 2.5x
 * - Tap and hold in the center to trigger Page Options
 */
@Composable
fun ZoomableMangaPage(
    file: File,
    pageIndex: Int,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    val imageRequest = remember(file.absolutePath) {
        ImageRequest.Builder(context)
            .data(file)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 1. Pinch zoom & Pan gesture detector
            .pointerInput(file.absolutePath) {
                detectMangaZoomAndPan(
                    onTransform = { zoomChange, panChange ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 4.5f)
                        scale = newScale
                        if (newScale > 1f) {
                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                            offsetX = (offsetX + panChange.x * newScale).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + panChange.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    isZoomed = { scale > 1.05f }
                )
            }
            // 2. Tap, Double-Tap, and Tap & Hold (Long-press) detector
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val width = size.width
                        if (scale <= 1.05f) {
                            when {
                                tapOffset.x < width * 0.25f -> onLeftTap()
                                tapOffset.x > width * 0.75f -> onRightTap()
                                else -> onCenterTap()
                            }
                        } else {
                            onCenterTap()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                    onLongPress = { longPressOffset ->
                        val width = size.width
                        // Tap and hold in the center of the page screen (between 15% and 85% width)
                        if (longPressOffset.x in (width * 0.15f)..(width * 0.85f)) {
                            onCenterLongPress()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        var loadState by remember(file.absolutePath) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }
        // AsyncImage + state overlay instead of SubcomposeAsyncImage: subcomposition per page is costly in pagers/lists
        AsyncImage(
            model = imageRequest,
            contentDescription = "Manga Page ${pageIndex + 1}",
            contentScale = ContentScale.Fit,
            onState = { loadState = it },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
        when (loadState) {
            is AsyncImagePainter.State.Success -> Unit
            is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error decoding page",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Failed to decode page image",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = file.name,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

/**
 * Zoomable Webtoon Page for continuous vertical scrolling with pinch zoom and tap & hold
 */
@Composable
fun ZoomableWebtoonPage(
    file: File,
    pageIndex: Int,
    onCenterTap: () -> Unit,
    onCenterLongPress: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    val imageRequest = remember(file.absolutePath) {
        ImageRequest.Builder(context)
            .data(file)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(file.absolutePath) {
                detectMangaZoomAndPan(
                    onTransform = { zoomChange, panChange ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 4.5f)
                        scale = newScale
                        if (newScale > 1f) {
                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                            offsetX = (offsetX + panChange.x * newScale).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + panChange.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    isZoomed = { scale > 1.05f }
                )
            }
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { onCenterTap() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.2f
                        }
                    },
                    onLongPress = { longPressOffset ->
                        val width = size.width
                        if (longPressOffset.x in (width * 0.15f)..(width * 0.85f)) {
                            onCenterLongPress()
                        }
                    }
                )
            }
    ) {
        var loadState by remember(file.absolutePath) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }
        // AsyncImage + state overlay instead of SubcomposeAsyncImage: subcomposition per page is costly in pagers/lists
        AsyncImage(
            model = imageRequest,
            contentDescription = "Page ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            onState = { loadState = it },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
        when (loadState) {
            is AsyncImagePainter.State.Success -> Unit
            is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Unable to decode ${file.name}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom Transform gesture detector tailored for comic readers:
 * - Only consumes touch pointers when user pinches with >= 2 fingers OR when user is already zoomed in.
 * - Leaves 1-finger horizontal and vertical drags unconsumed when scale is 1f, allowing HorizontalPager and LazyColumn to scroll smoothly.
 */
private suspend fun PointerInputScope.detectMangaZoomAndPan(
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit,
    isZoomed: () -> Boolean
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.size
            val currentlyZoomed = isZoomed()

            if (pointerCount >= 2 || (currentlyZoomed && pointerCount == 1)) {
                val zoomChange = if (pointerCount >= 2) event.calculateZoom() else 1f
                val panChange = if (currentlyZoomed || pointerCount >= 2) event.calculatePan() else Offset.Zero

                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onTransform(zoomChange, panChange)
                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Saves a decoded manga page file to the device's Gallery / Photos app using MediaStore
 */
private fun saveImageToGallery(context: Context, file: File, title: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val cleanName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val filename = "${cleanName}_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MangaReader")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        resolver.openOutputStream(uri)?.use { outStream ->
            file.inputStream().use { inStream ->
                inStream.copyTo(outStream)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        true
    } catch (e: Exception) {
        android.util.Log.e("ReaderScreen", "Failed to save image to gallery", e)
        false
    }
}

package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.PageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val scope = rememberCoroutineScope()
    // Page the reader should show; the slider changes it and the pager/list follows
    val readerPage by viewModel.currentReaderPage.collectAsStateWithLifecycle()
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

    // Immersive reading: hide status and navigation bars; a swipe from the edge shows them transiently
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // No legacy FLAG_FULLSCREEN: mixed with the insets controller it left stale zero
            // status-bar insets after exit, so screens drew under the clock
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            val act = context as? Activity
            val win = act?.window
            if (win != null) {
                WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.systemBars())
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

    // Like Mihon, the system bars (clock, battery) appear together with the reader menu
    LaunchedEffect(showControls) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (showControls) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Tap-and-hold (Long-press) Page Options Dialog State
    var showPageOptionsSheet by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf<PageRef?>(null) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }

    val onCenterLongPress: (pageIndex: Int, page: PageRef) -> Unit = { pageIndex, page ->
        selectedPageIndex = pageIndex
        selectedPage = page
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
                ShowIfSlow {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Opening chapter…",
                            color = readerTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
                    targetPage = readerPage,
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
                    targetPage = readerPage,
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
                            // Bars are hidden here, so plain statusBars insets are 0: reserve their space
                            // and the camera cutout (top in portrait, side in landscape) explicitly
                            .windowInsetsPadding(
                                WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            )
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
                            .windowInsetsPadding(
                                WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        // While dragging, only the slider and counter move; the page jumps once on release.
                        // Moving the pager on every drag event made it report its page back mid-drag and
                        // fight the thumb (counter jumping around), and decoded a page per event.
                        var draggedPage by remember { mutableStateOf<Float?>(null) }
                        val shownPage = draggedPage?.roundToInt() ?: currentPage

                        // Slim Slider Row with inline badge counter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // RTL manga: page 1 starts at the right edge, matching the pager
                            val sliderDirection = if (readingDirection == ReadingDirection.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
                            CompositionLocalProvider(LocalLayoutDirection provides sliderDirection) {
                                Slider(
                                    value = draggedPage ?: currentPage.toFloat(),
                                    onValueChange = { draggedPage = it },
                                    onValueChangeFinished = {
                                        // roundToInt: toInt() truncated 29.999 to 29, so the last page was unreachable
                                        draggedPage?.let { viewModel.onPageChanged(it.roundToInt()) }
                                        draggedPage = null
                                    },
                                    valueRange = 0f..(total - 1).toFloat().coerceAtLeast(0f),
                                    // No steps: one tick per page drew hundreds of dots on long chapters
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
                            }

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
                                        text = "${shownPage + 1}/$total",
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
            if (showPageOptionsSheet && selectedPage != null) {
                PageOptionsBottomSheet(
                    pageNumber = selectedPageIndex + 1,
                    onDismiss = { showPageOptionsSheet = false },
                    onSetAsCover = {
                        val page = selectedPage
                        if (page != null) {
                            viewModel.setCustomCoverFromPage(manga.id, page)
                            Toast.makeText(context, "Cover updated to page ${selectedPageIndex + 1}", Toast.LENGTH_SHORT).show()
                        }
                        showPageOptionsSheet = false
                    },
                    onCopy = {
                        val page = selectedPage
                        if (page != null) {
                            scope.launch {
                                val bytes = viewModel.readPageBytes(page)
                                val copied = bytes != null && withContext(Dispatchers.IO) {
                                    copyImageToClipboard(context, bytes, page.displayName)
                                }
                                // Android 13+ shows its own "Copied" confirmation
                                if (!copied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, if (copied) "Page copied" else "Failed to copy page", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showPageOptionsSheet = false
                    },
                    onSaveToGallery = {
                        val page = selectedPage
                        val pageNumber = selectedPageIndex + 1
                        if (page != null) {
                            scope.launch {
                                val bytes = viewModel.readPageBytes(page)
                                val success = bytes != null && withContext(Dispatchers.IO) {
                                    saveImageToGallery(
                                        context = context,
                                        bytes = bytes,
                                        title = "${manga.title}_ch${chapter.chapterNumber}_p$pageNumber"
                                    )
                                }
                                if (success) {
                                    Toast.makeText(context, "Page $pageNumber saved to Gallery", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save page to Gallery", Toast.LENGTH_SHORT).show()
                                }
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
                    val currentPageRef = pages.getOrNull(currentPage)

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
                        if (currentPageRef != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setCustomCoverFromPage(manga.id, currentPageRef)
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

@Composable
private fun PageActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

/** Long-press sheet for the active page: set as cover, copy the image, save it to the gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageOptionsBottomSheet(
    pageNumber: Int,
    onDismiss: () -> Unit,
    onSetAsCover: () -> Unit,
    onCopy: () -> Unit,
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

            // Square action tiles side by side, as in Mihon
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PageActionTile(Icons.Default.Palette, "Set as cover", Modifier.weight(1f).testTag("option_set_cover"), onSetAsCover)
                PageActionTile(Icons.Default.ContentCopy, "Copy", Modifier.weight(1f).testTag("option_copy"), onCopy)
                PageActionTile(Icons.Default.Download, "Save", Modifier.weight(1f).testTag("option_save_gallery"), onSaveToGallery)
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
    pages: List<PageRef>,
    initialIndex: Int,
    targetPage: Int,
    isRtl: Boolean,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: (pageIndex: Int, page: PageRef) -> Unit
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

    // Jump when the page is changed from outside (the slider); swipes already match, so this is a no-op for them
    LaunchedEffect(targetPage) {
        if (targetPage in 0 until totalPages && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = isRtl,
        // Decode two pages each side ahead of the swipe (~5 screen-sized bitmaps held, ~50 MB)
        beyondViewportPageCount = 2,
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex)
        if (page != null) {
            ZoomableMangaPage(
                page = page,
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
                onCenterLongPress = { onCenterLongPress(pageIndex, page) }
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
    pages: List<PageRef>,
    initialIndex: Int,
    targetPage: Int,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: (pageIndex: Int, page: PageRef) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))

    // The last page is often shorter than the screen and can never become the first visible item,
    // so once the list can't scroll further, count the last page as reached (otherwise 30 of 31, never complete)
    // (before the first layout canScrollForward is also false, so require laid-out items)
    fun visiblePage() =
        if (listState.layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward) pages.lastIndex
        else listState.firstVisibleItemIndex

    LaunchedEffect(listState) {
        snapshotFlow { visiblePage() }.collect { index ->
            onPageChanged(index)
        }
    }

    // Jump when the page is changed from outside (the slider)
    LaunchedEffect(targetPage) {
        if (targetPage in pages.indices && targetPage != visiblePage()) {
            listState.scrollToItem(targetPage)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(pages, key = { index, _ -> index }) { index, page ->
            ZoomableWebtoonPage(
                page = page,
                onCenterTap = onCenterTap,
                onCenterLongPress = { onCenterLongPress(index, page) }
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
    page: PageRef,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val zoomScope = rememberCoroutineScope()
    val flingDecay = rememberSplineBasedDecay<Offset>()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current

    val imageRequest = remember(page) {
        ImageRequest.Builder(context)
            .data(page)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 1. Pinch zoom & Pan gesture detector
            .pointerInput(page) {
                detectMangaZoomAndPan(
                    onTransform = { zoomChange, panChange, centroid ->
                        val (s, o) = zoomedTransform(scale, Offset(offsetX, offsetY), zoomChange, panChange, centroid, size)
                        scale = s
                        offsetX = o.x
                        offsetY = o.y
                    },
                    onGestureStart = { flingJob?.cancel() },
                    onFling = { v ->
                        flingJob = zoomScope.flingPan(Offset(offsetX, offsetY), v, scale, size, flingDecay) {
                            offsetX = it.x
                            offsetY = it.y
                        }
                    },
                    isZoomed = { scale > 1.05f },
                    isAtHorizontalEdge = { dragX ->
                        val maxOffsetX = (scale - 1f) * size.width / 2f
                        // Finger moving right reveals the left side: hand over once that side is fully shown
                        if (dragX > 0f) offsetX >= maxOffsetX - 1f else offsetX <= -maxOffsetX + 1f
                    }
                )
            }
            // 2. Tap, Double-Tap, and Tap & Hold (Long-press) detector
            .pointerInput(page) {
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
                    onDoubleTap = { tap ->
                        zoomScope.animateDoubleTapZoom(tap, size, scale, Offset(offsetX, offsetY)) { s, o ->
                            scale = s
                            offsetX = o.x
                            offsetY = o.y
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
        var loadState by remember(page) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }
        // AsyncImage + state overlay instead of SubcomposeAsyncImage: subcomposition per page is costly in pagers/lists
        AsyncImage(
            model = imageRequest,
            contentDescription = "Manga Page ${page.index + 1}",
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
                            text = page.displayName,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ShowIfSlow {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
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
    page: PageRef,
    onCenterTap: () -> Unit,
    onCenterLongPress: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val zoomScope = rememberCoroutineScope()
    val flingDecay = rememberSplineBasedDecay<Offset>()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current

    val imageRequest = remember(page) {
        ImageRequest.Builder(context)
            .data(page)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(page) {
                detectMangaZoomAndPan(
                    onTransform = { zoomChange, panChange, centroid ->
                        val (s, o) = zoomedTransform(scale, Offset(offsetX, offsetY), zoomChange, panChange, centroid, size)
                        scale = s
                        offsetX = o.x
                        offsetY = o.y
                    },
                    onGestureStart = { flingJob?.cancel() },
                    onFling = { v ->
                        flingJob = zoomScope.flingPan(Offset(offsetX, offsetY), v, scale, size, flingDecay) {
                            offsetX = it.x
                            offsetY = it.y
                        }
                    },
                    isZoomed = { scale > 1.05f }
                )
            }
            .pointerInput(page) {
                detectTapGestures(
                    onTap = { onCenterTap() },
                    onDoubleTap = { tap ->
                        zoomScope.animateDoubleTapZoom(tap, size, scale, Offset(offsetX, offsetY)) { s, o ->
                            scale = s
                            offsetX = o.x
                            offsetY = o.y
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
        var loadState by remember(page) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }
        // AsyncImage + state overlay instead of SubcomposeAsyncImage: subcomposition per page is costly in pagers/lists
        AsyncImage(
            model = imageRequest,
            contentDescription = "Page ${page.index + 1}",
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
                            text = "Unable to decode ${page.displayName}",
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
                    ShowIfSlow {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
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
/**
 * Mihon-style double tap: animate (500 ms, ease-in-out) to 2x with the tapped point moved to the centre of
 * the view, or back to fit when already zoomed.
 */
private fun CoroutineScope.animateDoubleTapZoom(
    tap: Offset,
    size: IntSize,
    scale: Float,
    offset: Offset,
    apply: (scale: Float, offset: Offset) -> Unit
) {
    val zoomIn = scale <= 1.05f
    val toScale = if (zoomIn) 2f else 1f
    val maxX = (toScale - 1f) * size.width / 2f
    val maxY = (toScale - 1f) * size.height / 2f
    // graphicsLayer scales around the centre, so moving tap point p to the centre needs offset -s * (p - centre)
    val toOffset = if (zoomIn) {
        Offset(
            (-(tap.x - size.width / 2f) * toScale).coerceIn(-maxX, maxX),
            (-(tap.y - size.height / 2f) * toScale).coerceIn(-maxY, maxY)
        )
    } else {
        Offset.Zero
    }
    launch {
        animate(0f, 1f, animationSpec = tween(500, easing = EaseInOutQuad)) { f, _ ->
            apply(scale + (toScale - scale) * f, offset + (toOffset - offset) * f)
        }
    }
}

private suspend fun PointerInputScope.detectMangaZoomAndPan(
    onTransform: (zoomChange: Float, panChange: Offset, centroid: Offset) -> Unit,
    isZoomed: () -> Boolean,
    // Paged reader (Mihon-style): when a zoomed page is already at its left/right edge, a horizontal
    // one-finger drag toward that edge is left unconsumed so the pager turns the page
    isAtHorizontalEdge: ((dragX: Float) -> Boolean)? = null,
    // A new touch stops a running fling, like Mihon
    onGestureStart: () -> Unit = {},
    // Release velocity of a one-finger pan while zoomed, to keep the page gliding
    onFling: (Velocity) -> Unit = {}
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        var yieldedToPager = false
        var panned = false
        val velocity = VelocityTracker()
        do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.size
            val currentlyZoomed = isZoomed()

            // Only a one-finger pan's tail counts toward the fling; a pinch resets it
            if (pointerCount == 1) {
                event.changes.first().let { velocity.addPosition(it.uptimeMillis, it.position) }
            } else {
                velocity.resetTracking()
            }

            if (pointerCount >= 2 || (currentlyZoomed && pointerCount == 1 && !yieldedToPager)) {
                val zoomChange = if (pointerCount >= 2) event.calculateZoom() else 1f
                val panChange = if (currentlyZoomed || pointerCount >= 2) event.calculatePan() else Offset.Zero

                if (pointerCount == 1 && isAtHorizontalEdge != null &&
                    abs(panChange.x) > abs(panChange.y) && isAtHorizontalEdge(panChange.x)
                ) {
                    // Hand the rest of this gesture to the pager; consuming even once would cancel its drag
                    yieldedToPager = true
                    continue
                }

                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onTransform(zoomChange, panChange, event.calculateCentroid(useCurrent = false))
                    if (pointerCount == 1) panned = true
                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        if (panned && !yieldedToPager && isZoomed()) onFling(velocity.calculateVelocity())
    }
}

/**
 * New scale and offset after a pinch/pan, Mihon-style: zoom around the fingers' centroid, pan 1:1 with the
 * finger, and keep the page covering the view (max 5x like Mihon). The page is drawn with graphicsLayer,
 * which scales around the view centre, so a content point under screen point p stays put when
 * offset' = (p - centre) * (1 - k) + offset * k, with k = newScale / scale.
 */
private fun zoomedTransform(
    scale: Float,
    offset: Offset,
    zoomChange: Float,
    pan: Offset,
    centroid: Offset,
    size: IntSize
): Pair<Float, Offset> {
    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
    if (newScale <= 1f) return 1f to Offset.Zero
    val k = newScale / scale
    val center = Offset(size.width / 2f, size.height / 2f)
    val raw = (centroid - center) * (1f - k) + offset * k + pan
    return newScale to clampOffset(raw, newScale, size)
}

private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (scale - 1f) * size.width / 2f
    val maxY = (scale - 1f) * size.height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Lets a zoomed page keep gliding after a pan and slow down naturally, stopping at the page edges. */
private fun CoroutineScope.flingPan(
    start: Offset,
    velocity: Velocity,
    scale: Float,
    size: IntSize,
    decay: DecayAnimationSpec<Offset>,
    apply: (Offset) -> Unit
): Job = launch {
    // Clamped every frame, so it simply rests at an edge if it reaches one
    AnimationState(Offset.VectorConverter, start, Offset(velocity.x, velocity.y)).animateDecay(decay) {
        apply(clampOffset(value, scale, size))
    }
}

/**
 * Shows [content] only if it is still in composition after [delayMillis]. Opening a chapter and decoding
 * a page usually take well under that, so loading indicators no longer flash on every page.
 */
@Composable
private fun ShowIfSlow(delayMillis: Long = 400L, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    if (visible) content()
}

/** Puts a page image on the clipboard through the app's FileProvider; only the latest copy is kept. */
private fun copyImageToClipboard(context: Context, bytes: ByteArray, fileName: String): Boolean {
    return try {
        val dir = File(context.cacheDir, "clipboard").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, fileName.ifBlank { "page.jpg" })
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, fileName, uri))
        true
    } catch (e: Exception) {
        android.util.Log.e("ReaderScreen", "Failed to copy page to clipboard", e)
        false
    }
}

/**
 * Saves a manga page's image bytes to the device's Gallery / Photos app using MediaStore
 */
private fun saveImageToGallery(context: Context, bytes: ByteArray, title: String): Boolean {
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
            outStream.write(bytes)
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

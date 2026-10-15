package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.ui.components.stableStatusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.LibrarySort
import com.example.data.Manga
import com.example.ui.LibraryFilter
import com.example.ui.MangaViewModel
import com.example.ui.NavTab
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MangaViewModel,
    onSelectFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mangaList by viewModel.filteredManga.collectAsStateWithLifecycle()
    val totalLibraryManga by viewModel.libraryManga.collectAsStateWithLifecycle(emptyList())
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val currentFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val continueManga by viewModel.continueReadingManga.collectAsStateWithLifecycle()
    val folderUri by viewModel.preferencesManager.folderUri.collectAsStateWithLifecycle()
    val librarySort by viewModel.preferencesManager.librarySort.collectAsStateWithLifecycle()
    val librarySortAscending by viewModel.preferencesManager.librarySortAscending.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.stableStatusBars)
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Always on the icon's own near-black, since the white art washes out on the light theme
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_art),
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0A0B0C))
                                .padding(3.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Moon",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LibrarySortButton(
                            sort = librarySort,
                            ascending = librarySortAscending,
                            onSelect = viewModel::onLibrarySortSelected
                        )
                        IconButton(
                            onClick = { viewModel.selectTab(NavTab.SEARCH) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Manga",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if ((folderUri.isNullOrBlank() || totalLibraryManga.isEmpty()) && !isScanning) {
                    // Sleek Empty State with direct folder picker (no sample buttons)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = if (folderUri.isNullOrBlank()) "Select Manga Folder" else "No CBZ Manga Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (folderUri.isNullOrBlank())
                                        "Choose a folder containing your manga chapters (.cbz files) to start reading."
                                    else
                                        "No readable .cbz files were found in this directory. Choose a different folder with your manga files.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onSelectFolderClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("select_folder_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (folderUri.isNullOrBlank()) "Select Manga Folder" else "Change Folder",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Pull down from the top of the library to rescan the folder; the indicator also
                    // shows during the automatic launch scan
                    PullToRefreshBox(
                        isRefreshing = isScanning,
                        onRefresh = { viewModel.scanLibrary() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("manga_grid")
                    ) {
                        // 1. Library Section Header & Filter Pills (Shifted straight to top)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Library",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val countText = if (currentFilter == LibraryFilter.ALL) {
                                        "${totalLibraryManga.size} titles"
                                    } else {
                                        "${mangaList.size} of ${totalLibraryManga.size} titles"
                                    }
                                    Text(
                                        text = countText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Horizontal scrollable filter pills
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        FilterPill(
                                            selected = currentFilter == LibraryFilter.ALL,
                                            label = "All",
                                            onClick = { viewModel.setLibraryFilter(LibraryFilter.ALL) }
                                        )
                                    }
                                    item {
                                        FilterPill(
                                            selected = currentFilter == LibraryFilter.IN_PROGRESS,
                                            label = "Reading",
                                            onClick = { viewModel.setLibraryFilter(LibraryFilter.IN_PROGRESS) }
                                        )
                                    }
                                    item {
                                        FilterPill(
                                            selected = currentFilter == LibraryFilter.FAVORITES,
                                            label = "Favorites",
                                            onClick = { viewModel.setLibraryFilter(LibraryFilter.FAVORITES) }
                                        )
                                    }
                                    item {
                                        FilterPill(
                                            selected = currentFilter == LibraryFilter.UNREAD,
                                            label = "Unread",
                                            onClick = { viewModel.setLibraryFilter(LibraryFilter.UNREAD) }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Manga Grid Items or Filter Empty State
                        if (mangaList.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyFilterCard(
                                    filter = currentFilter,
                                    onResetFilter = { viewModel.setLibraryFilter(LibraryFilter.ALL) }
                                )
                            }
                        } else {
                            items(mangaList, key = { it.id }) { manga ->
                                ModernMangaCard(
                                    manga = manga,
                                    onClick = { viewModel.selectManga(manga) }
                                )
                            }
                        }
                    }
                    }
                }
            }

            // Circular Resume Reading Button in the bottom right corner above the bottom bar
            if (continueManga != null) {
                FloatingActionButton(
                    onClick = { viewModel.resumeReading(continueManga!!) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .size(56.dp)
                        .testTag("resume_reading_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume reading ${continueManga?.title}",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

/** Sort menu for the library; the active option shows its direction and tapping it again flips it. */
@Composable
private fun LibrarySortButton(
    sort: LibrarySort,
    ascending: Boolean,
    onSelect: (LibrarySort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(36.dp)
                .testTag("home_sort_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort library: ${sort.displayName}",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                val active = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.displayName,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingIcon = if (active) {
                        {
                            Icon(
                                imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (ascending) "Ascending" else "Descending",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        onSelect(option)
                        // Keep the menu open on the active option so its direction can be flipped and seen
                        if (!active) expanded = false
                    },
                    modifier = Modifier.testTag("home_sort_${option.name}")
                )
            }
        }
    }
}

@Composable
private fun FilterPill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(34.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyFilterCard(
    filter: LibraryFilter,
    onResetFilter: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            val (icon, title, desc) = when (filter) {
                LibraryFilter.FAVORITES -> Triple(
                    Icons.Default.FavoriteBorder,
                    "No Favorites Yet",
                    "Tap the heart icon on any manga details page to add it to your favorites."
                )
                LibraryFilter.UNREAD -> Triple(
                    Icons.Default.CheckCircleOutline,
                    "All Caught Up!",
                    "You have started reading all the manga in your library."
                )
                LibraryFilter.IN_PROGRESS -> Triple(
                    Icons.Default.AutoStories,
                    "No Manga In Progress",
                    "Open any title and start reading to track your progress here."
                )
                LibraryFilter.ALL -> Triple(
                    Icons.Default.SearchOff,
                    "No Manga Found",
                    "No manga matched your library filter criteria."
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (filter != LibraryFilter.ALL) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onResetFilter,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("View All Manga")
                }
            }
        }
    }
}

@Composable
private fun ModernMangaCard(
    manga: Manga,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth tactile spring scale when card is pressed
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_press_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("manga_card_${manga.id}")
    ) {
        // Cover Box with 3:4 Aspect Ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            if (!manga.coverPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(manga.coverPath),
                    contentDescription = "Cover for ${manga.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Top gradient overlay for contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )

            // Favorite indicator tag if bookmarked
            if (manga.isFavorite) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFDC2626).copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "♥",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Manga Title
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Chapter Count Badge
        Text(
            text = if (manga.chapterCount > 0) "Ch. ${manga.chapterCount}" else "Chapters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

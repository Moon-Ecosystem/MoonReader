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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.border
import androidx.compose.material3.Checkbox
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.data.LibraryStatus
import com.example.data.Manga
import com.example.data.db.CollectionEntity
import com.example.data.preferences.PreferencesManager
import com.example.ui.MangaViewModel
import com.example.ui.NavTab
import com.example.ui.components.CollectionNameDialog
import com.example.ui.components.CollectionPickerDialog
import com.example.ui.components.CollectionTabs
import com.example.ui.components.DeleteCollectionDialog
import androidx.compose.material.icons.filled.CollectionsBookmark
import java.io.File

/** newCollectionFor value when a collection is made from the "+" tab, with no series to add (ids are never empty). */
private const val NEW_COLLECTION_ONLY = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MangaViewModel,
    onSelectFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val homeLibrary by viewModel.homeLibrary.collectAsStateWithLifecycle()
    val totalLibraryManga by viewModel.libraryManga.collectAsStateWithLifecycle(emptyList())
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val libraryStatus by viewModel.preferencesManager.libraryStatus.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.preferencesManager.libraryFavoritesOnly.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val collectionMembers by viewModel.collectionMembers.collectAsStateWithLifecycle()
    val selectedCollection by viewModel.selectedCollection.collectAsStateWithLifecycle()

    // Collection dialogs: the series whose collections are being edited, the series a new collection is
    // created for (NEW_COLLECTION_ONLY from the "+" tab), and the collection being renamed or deleted
    var pickingCollectionsFor by remember { mutableStateOf<Manga?>(null) }
    var newCollectionFor by remember { mutableStateOf<String?>(null) }
    var renamingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var deletingCollection by remember { mutableStateOf<CollectionEntity?>(null) }

    pickingCollectionsFor?.let { manga ->
        CollectionPickerDialog(
            title = manga.title,
            collections = collections,
            memberOf = collectionMembers.filterValues { manga.id in it }.keys,
            onToggle = { id, member -> viewModel.setInCollection(id, manga.id, member) },
            onNewCollection = {
                pickingCollectionsFor = null
                newCollectionFor = manga.id
            },
            onDismiss = { pickingCollectionsFor = null }
        )
    }
    newCollectionFor?.let { mangaId ->
        CollectionNameDialog(
            title = "New collection",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createCollection(name, mangaId.takeIf { it != NEW_COLLECTION_ONLY })
                newCollectionFor = null
            },
            onDismiss = { newCollectionFor = null }
        )
    }
    renamingCollection?.let { collection ->
        CollectionNameDialog(
            title = "Rename collection",
            confirmLabel = "Rename",
            initialName = collection.name,
            onConfirm = { name ->
                viewModel.renameCollection(collection.id, name)
                renamingCollection = null
            },
            onDismiss = { renamingCollection = null }
        )
    }
    deletingCollection?.let { collection ->
        DeleteCollectionDialog(
            name = collection.name,
            onConfirm = {
                viewModel.deleteCollection(collection.id)
                deletingCollection = null
            },
            onDismiss = { deletingCollection = null }
        )
    }
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
                        LibraryViewButton(
                            status = libraryStatus,
                            favoritesOnly = favoritesOnly,
                            sort = librarySort,
                            ascending = librarySortAscending,
                            onStatus = viewModel::setLibraryStatus,
                            onFavoritesOnly = viewModel::setLibraryFavoritesOnly,
                            onSort = viewModel::onLibrarySortSelected
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
                    // One pager page per tab; swiping the grid sideways moves between collections
                    val tabIds = remember(collections) {
                        listOf(PreferencesManager.ALL_COLLECTIONS) + collections.map { it.id }
                    }
                    val pagerState = rememberPagerState(
                        initialPage = tabIds.indexOf(selectedCollection).coerceAtLeast(0)
                    ) { tabIds.size }
                    // Tab tapped (or the remembered tab loaded): slide to it
                    LaunchedEffect(selectedCollection, tabIds) {
                        val index = tabIds.indexOf(selectedCollection)
                        if (index >= 0 && index != pagerState.currentPage) pagerState.animateScrollToPage(index)
                    }
                    // Swiped: select the tab the pager settled on. Compared with the current selection, so the
                    // pager's first page (All, before collections load) never overwrites the remembered tab
                    LaunchedEffect(pagerState, tabIds) {
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            val id = tabIds.getOrNull(page) ?: return@collect
                            if (id != viewModel.selectedCollection.value) viewModel.selectCollection(id)
                        }
                    }

                    // Pinned above the grid so the tabs stay reachable while scrolling
                    CollectionTabs(
                        collections = collections,
                        // The page being scrolled towards, so the tab label highlights before the swipe
                        // settles; the settled page is saved above
                        selected = tabIds.getOrNull(pagerState.targetPage) ?: selectedCollection,
                        pagerPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                        counts = homeLibrary.mapValues { it.value.size },
                        onSelect = viewModel::selectCollection,
                        onCreate = { newCollectionFor = NEW_COLLECTION_ONLY },
                        onRename = { renamingCollection = it },
                        onDelete = { deletingCollection = it }
                    )
                    ActiveFilterChips(
                        status = libraryStatus,
                        favoritesOnly = favoritesOnly,
                        onClearStatus = { viewModel.setLibraryStatus(LibraryStatus.ALL) },
                        onClearFavorites = { viewModel.setLibraryFavoritesOnly(false) }
                    )
                    // Pull down from the top of the library to rescan the folder; the indicator also
                    // shows during the automatic launch scan
                    PullToRefreshBox(
                        isRefreshing = isScanning,
                        onRefresh = { viewModel.scanLibrary() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> tabIds.getOrElse(page) { page.toLong() } },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("collection_pager")
                    ) { page ->
                    val tabId = tabIds.getOrElse(page) { PreferencesManager.ALL_COLLECTIONS }
                    val mangaList = homeLibrary[tabId].orEmpty()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("manga_grid")
                    ) {
                        // Manga grid, or why it's empty
                        if (mangaList.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyFilterCard(
                                    status = libraryStatus,
                                    favoritesOnly = favoritesOnly,
                                    inCollection = tabId != PreferencesManager.ALL_COLLECTIONS,
                                    onResetFilter = viewModel::resetLibraryFilter
                                )
                            }
                        } else {
                            items(mangaList, key = { it.id }) { manga ->
                                ModernMangaCard(
                                    manga = manga,
                                    onClick = { viewModel.selectManga(manga) },
                                    onMarkRead = { viewModel.markMangaRead(manga, it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(manga) },
                                    onEditCollections = { pickingCollectionsFor = manga }
                                )
                            }
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

/**
 * One menu for what the library shows: a status (one of) plus favorites only, then the sort order.
 */
@Composable
private fun LibraryViewButton(
    status: LibraryStatus,
    favoritesOnly: Boolean,
    sort: LibrarySort,
    ascending: Boolean,
    onStatus: (LibraryStatus) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onSort: (LibrarySort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = status != LibraryStatus.ALL || favoritesOnly
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(36.dp)
                .testTag("home_filter_button")
        ) {
            // Tinted while filtering; the chips under the tabs say which filters are on
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = if (filtered) "Filter and sort library (filter on)" else "Filter and sort library",
                tint = if (filtered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        // Stays open so several options can be set in one go; tap outside to close.
        // Two columns side by side: what to show, and how to sort it
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    MenuSectionLabel("Show")
                    LibraryStatus.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            leadingIcon = { RadioButton(selected = option == status, onClick = null) },
                            onClick = { onStatus(option) },
                            modifier = Modifier.testTag("home_filter_${option.name}")
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Favorites only") },
                        leadingIcon = { Checkbox(checked = favoritesOnly, onCheckedChange = null) },
                        onClick = { onFavoritesOnly(!favoritesOnly) },
                        modifier = Modifier.testTag("home_filter_favorites")
                    )
                }
                VerticalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    MenuSectionLabel("Sort by")
                    LibrarySortOptions(sort, ascending, onSort)
                }
            }
        }
    }
}

@Composable
private fun LibrarySortOptions(sort: LibrarySort, ascending: Boolean, onSort: (LibrarySort) -> Unit) {
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
            // Tapping the active option again flips its direction
            trailingIcon = if (active) {
                {
                    Icon(
                        imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = if (ascending) "Ascending" else "Descending",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else null,
            onClick = { onSort(option) },
            modifier = Modifier.testTag("home_sort_${option.name}")
        )
    }
}

/** The filters that are on, each removable with its ✕; nothing at all while the whole library shows. */
@Composable
private fun ActiveFilterChips(
    status: LibraryStatus,
    favoritesOnly: Boolean,
    onClearStatus: () -> Unit,
    onClearFavorites: () -> Unit
) {
    if (status == LibraryStatus.ALL && !favoritesOnly) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("active_filters")
    ) {
        if (status != LibraryStatus.ALL) {
            FilterChipWithClear(status.displayName, onClearStatus, Modifier.testTag("active_filter_status"))
        }
        if (favoritesOnly) {
            FilterChipWithClear("Favorites", onClearFavorites, Modifier.testTag("active_filter_favorites"))
        }
    }
}

@Composable
internal fun FilterChipWithClear(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $label filter",
                modifier = Modifier.size(InputChipDefaults.IconSize)
            )
        },
        modifier = modifier
    )
}

@Composable
internal fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyFilterCard(
    status: LibraryStatus,
    favoritesOnly: Boolean,
    inCollection: Boolean,
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
            val (icon, title, desc) = when {
                inCollection && !favoritesOnly && status == LibraryStatus.ALL -> Triple(
                    Icons.Default.CollectionsBookmark,
                    "Empty Collection",
                    "Long-press a series in All and choose Collections… to add it here."
                )
                favoritesOnly && status == LibraryStatus.ALL -> Triple(
                    Icons.Default.FavoriteBorder,
                    "No Favorites Yet",
                    "Tap the heart icon on any manga details page to add it to your favorites."
                )
                favoritesOnly -> Triple(
                    Icons.Default.FavoriteBorder,
                    "No Matching Favorites",
                    "None of your favorites match the \"${status.displayName}\" filter."
                )
                status == LibraryStatus.NOT_STARTED -> Triple(
                    Icons.Default.CheckCircleOutline,
                    "All Caught Up!",
                    "You have started reading all the manga in your library."
                )
                status == LibraryStatus.READING -> Triple(
                    Icons.Default.AutoStories,
                    "No Manga In Progress",
                    "Open any title and start reading to track your progress here."
                )
                status == LibraryStatus.COMPLETED -> Triple(
                    Icons.Default.CheckCircleOutline,
                    "Nothing Finished Yet",
                    "Series you finish reading show up here."
                )
                else -> Triple(
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

            if (status != LibraryStatus.ALL || favoritesOnly) {
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ModernMangaCard(
    manga: Manga,
    onClick: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditCollections: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
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

    // The menu opens after 250 ms instead of the system's 400 ms; only the cards use the shorter delay
    val baseViewConfiguration = LocalViewConfiguration.current
    val fastLongPress = remember(baseViewConfiguration) {
        object : ViewConfiguration by baseViewConfiguration {
            override val longPressTimeoutMillis: Long = 250L
        }
    }

    // The menu anchors to this unscaled box: inside the scaled card it moved with the press animation,
    // and directly in the grid cell it had no anchor and was pinned to the grid's edge
    CompositionLocalProvider(LocalViewConfiguration provides fastLongPress) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .testTag("manga_card_${manga.id}")
        ) {
            // Cover Box with 3:4 Aspect Ratio; outlined in the accent color while its menu is open
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .then(
                        if (showMenu) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        else Modifier
                    )
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

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Mark as read") },
                onClick = { showMenu = false; onMarkRead(true) },
                modifier = Modifier.testTag("option_mark_read")
            )
            DropdownMenuItem(
                text = { Text("Mark as unread") },
                onClick = { showMenu = false; onMarkRead(false) },
                modifier = Modifier.testTag("option_mark_unread")
            )
            DropdownMenuItem(
                text = { Text(if (manga.isFavorite) "Remove from favorites" else "Add to favorites") },
                onClick = { showMenu = false; onToggleFavorite() },
                modifier = Modifier.testTag("option_toggle_favorite")
            )
            DropdownMenuItem(
                text = { Text("Collections…") },
                onClick = { showMenu = false; onEditCollections() },
                modifier = Modifier.testTag("option_collections")
            )
        }
    }
    }
}

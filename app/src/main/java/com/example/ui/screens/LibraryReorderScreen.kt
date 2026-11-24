package com.example.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LibraryGridLayout
import com.example.data.Manga
import com.example.ui.LibraryReorderDraft
import com.example.ui.MangaViewModel
import kotlin.math.roundToInt

private val handleSize = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryReorderScreen(draft: LibraryReorderDraft, viewModel: MangaViewModel) {
    val currentLibrary by viewModel.libraryManga.collectAsStateWithLifecycle()
    val folder by viewModel.preferencesManager.folderUri.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val gridLayout by viewModel.preferencesManager.libraryGridLayout.collectAsStateWithLifecycle()
    val stale = remember(currentLibrary, folder, draft.initialOrder) { !draft.matchesLibrary(currentLibrary, folder) }
    val canEdit = !stale && !scanning && !draft.saving
    var showReplaceDialog by rememberSaveable { mutableStateOf(false) }

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("Replace custom order?") },
            text = { Text("Saving this arrangement will replace the custom order you saved earlier.") },
            confirmButton = {
                TextButton(
                    enabled = canEdit,
                    onClick = {
                        showReplaceDialog = false
                        viewModel.saveLibraryReorder()
                    }
                ) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { showReplaceDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reorder library") },
                actions = {
                    TextButton(
                        onClick = viewModel::cancelLibraryReorder,
                        enabled = !draft.saving,
                        modifier = Modifier.testTag("library_reorder_cancel")
                    ) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            if (draft.confirmReplacement) showReplaceDialog = true
                            else viewModel.saveLibraryReorder()
                        },
                        enabled = canEdit && draft.hasChanges,
                        modifier = Modifier.testTag("library_reorder_save")
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (stale) {
                Text(
                    "The library changed. Cancel and reopen Reorder library to get the current series.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            ReorderGrid(draft.manga, gridLayout, canEdit, viewModel::moveLibraryManga)
        }
    }
}

private data class DraggedCard(
    val manga: Manga,
    val width: Int,
    val grabOffset: Offset,
    val pointer: Offset,
    val destinationIndex: Int
)

@Composable
private fun ReorderGrid(manga: List<Manga>, gridLayout: LibraryGridLayout, enabled: Boolean, move: (String, String) -> Boolean) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mangaById by rememberUpdatedState(remember(manga) { manga.associateBy { it.id } })
    val moveCard by rememberUpdatedState(move)
    val handlePixels = with(density) { handleSize.toPx() }
    val scrollEdge = with(density) { 56.dp.toPx() }
    val scrollSpeed = with(density) { 900.dp.toPx() }
    var drag by remember { mutableStateOf<DraggedCard?>(null) }

    fun moveUnderPointer() {
        val active = drag ?: return
        val layout = gridState.layoutInfo
        val point = Offset(active.pointer.x, active.pointer.y.coerceIn(0f, (layout.viewportSize.height - 1).coerceAtLeast(0).toFloat()))
        val target = layout.visibleItemsInfo.firstOrNull { point in it.bounds(layout.viewportSize.width, rtl) } ?: return
        val targetId = target.key as? String ?: return
        // The card now owns the destination slot. Wait until the finger enters a different slot before moving again.
        if (target.index != active.destinationIndex && moveCard(active.manga.id, targetId)) {
            // Keep the viewport on this grid slot even if its previous card was the one being dragged.
            gridState.requestScrollToItem(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            drag = active.copy(destinationIndex = target.index)
        }
    }

    LaunchedEffect(drag?.manga?.id) {
        if (drag == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val active = drag ?: break
            val viewportHeight = gridState.layoutInfo.viewportSize.height
            val edge = scrollEdge.coerceAtMost(viewportHeight / 2f)
            if (edge > 0) {
                val towardTop = ((edge - active.pointer.y) / edge).coerceIn(0f, 1f)
                val towardBottom = ((active.pointer.y - (viewportHeight - edge)) / edge).coerceIn(0f, 1f)
                val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                val delta = (towardBottom - towardTop) * scrollSpeed * seconds
                if (delta != 0f && gridState.scrollBy(delta) != 0f) moveUnderPointer()
            }
            previousFrame = frame
        }
    }

    Box(Modifier.fillMaxSize().padding(horizontal = gridLayout.horizontalPadding, vertical = 8.dp).clipToBounds()) {
        LazyVerticalGrid(
            columns = gridLayout.cells,
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(gridLayout.columnSpacing),
            verticalArrangement = Arrangement.spacedBy(gridLayout.rowSpacing),
            modifier = Modifier.fillMaxSize().testTag("library_reorder_grid")
                // Keep the gesture on the grid so the handle can scroll out of composition mid-drag.
                .pointerInput(gridState, enabled, rtl, handlePixels) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val layout = gridState.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { info ->
                            val bounds = info.bounds(layout.viewportSize.width, rtl)
                            val width = minOf(bounds.width, handlePixels)
                            val handle = if (rtl) bounds.left else bounds.right - width
                            down.position in Rect(handle, bounds.top, handle + width, bounds.top + handlePixels)
                        } ?: return@awaitEachGesture
                        val id = item.key as? String ?: return@awaitEachGesture
                        val card = mangaById[id] ?: return@awaitEachGesture
                        down.consume()
                        drag = DraggedCard(
                            card, item.size.width, down.position - item.bounds(layout.viewportSize.width, rtl).topLeft,
                            down.position, item.index
                        )
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                drag = drag?.copy(pointer = change.position)
                                moveUnderPointer()
                                if (!change.pressed) break
                            }
                        } finally {
                            drag = null
                        }
                    }
                }
        ) {
            itemsIndexed(manga, key = { _, card -> card.id }) { index, card ->
                val dragging = drag?.manga?.id == card.id
                Column(Modifier.animateItem().fillMaxWidth().alpha(if (dragging) 0.25f else 1f)) {
                    MangaCardContent(card, compact = gridLayout.columns != null) {
                        ReorderHandle(
                            Modifier.align(Alignment.TopEnd)
                                .testTag("library_reorder_handle_${card.id}")
                                .semantics {
                                    contentDescription = "Reorder ${card.title}"
                                    if (enabled) {
                                        customActions = buildList {
                                            manga.getOrNull(index - 1)?.let { previous ->
                                                add(CustomAccessibilityAction("Move earlier") { move(card.id, previous.id) })
                                            }
                                            manga.getOrNull(index + 1)?.let { next ->
                                                add(CustomAccessibilityAction("Move later") { move(card.id, next.id) })
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
        drag?.let { active ->
            Column(
                Modifier.absoluteOffset {
                    val position = active.pointer - active.grabOffset
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }.width(with(density) { active.width.toDp() }).clearAndSetSemantics { }
            ) {
                MangaCardContent(active.manga, outlined = true, compact = gridLayout.columns != null) {
                    ReorderHandle(Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

@Composable
private fun ReorderHandle(modifier: Modifier) {
    Surface(
        modifier = modifier.size(handleSize),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.93f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DragHandle, contentDescription = null)
        }
    }
}

private fun LazyGridItemInfo.bounds(viewportWidth: Int, rtl: Boolean): Rect {
    val left = if (rtl) viewportWidth - offset.x - size.width else offset.x
    return Rect(left.toFloat(), offset.y.toFloat(), (left + size.width).toFloat(), (offset.y + size.height).toFloat())
}

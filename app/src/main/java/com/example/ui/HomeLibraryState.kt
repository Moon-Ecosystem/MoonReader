package com.example.ui

import com.example.data.LibraryGridLayout
import com.example.data.LibrarySort
import com.example.data.LibraryStatus
import com.example.data.Manga
import kotlin.math.abs

internal data class HomeLibraryOptions(
    val status: LibraryStatus,
    val favoritesOnly: Boolean,
    val sort: LibrarySort,
    val ascending: Boolean,
    val scrollGeneration: Int = 0
)

internal data class HomeLibraryState(
    val options: HomeLibraryOptions,
    val tabs: Map<Long, List<Manga>> = emptyMap()
) {
    operator fun get(tab: Long): List<Manga>? = tabs[tab]
}

internal data class HomeGridAnchor(val id: String, val index: Int, val scrollOffset: Int)

internal data class HomeGridPosition(
    val layout: LibraryGridLayout,
    val firstIndex: Int,
    val anchors: List<HomeGridAnchor>
) {
    fun restore(manga: List<Manga>, layout: LibraryGridLayout): Pair<Int, Int> {
        if (manga.isEmpty()) return 0 to 0
        if (firstIndex == 0) {
            val offset = anchors.firstOrNull()?.scrollOffset?.takeIf { this.layout == layout } ?: 0
            return 0 to offset
        }
        val indices = manga.withIndex().associate { (index, book) -> book.id to index }
        // A series that was just read can move to the top. Prefer its neighbors in the old viewport.
        val candidate = anchors.mapNotNull { anchor -> indices[anchor.id]?.let { anchor to it } }
            .filter { (anchor, index) -> abs(index - anchor.index) <= (layout.columns ?: 3) }
            .minByOrNull { (anchor, index) -> abs(index - anchor.index) }
            ?: return firstIndex.coerceAtMost(manga.lastIndex) to 0
        val (anchor, index) = candidate
        return index to if (this.layout == layout) anchor.scrollOffset else 0
    }
}

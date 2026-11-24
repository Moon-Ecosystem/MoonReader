package com.example.ui.screens

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.dp
import com.example.data.LibraryGridLayout

internal val LibraryGridLayout.cells: GridCells
    get() = columns?.let { GridCells.Fixed(it) } ?: GridCells.Adaptive(minSize = 108.dp)

internal val LibraryGridLayout.horizontalPadding get() = if (columns == null) 16.dp else 8.dp
internal val LibraryGridLayout.columnSpacing get() = if (columns == null) 12.dp else 6.dp
internal val LibraryGridLayout.rowSpacing get() = if (columns == null) 14.dp else 8.dp

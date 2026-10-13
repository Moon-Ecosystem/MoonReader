package com.example.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable

/**
 * Status bar insets that don't depend on reported visibility. After the reader's immersive mode some
 * devices keep reporting the status bar as hidden (zero height) although it is visible again, so headers
 * padded with plain `statusBars` slid under the clock.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.stableStatusBars: WindowInsets
    @Composable get() = statusBarsIgnoringVisibility

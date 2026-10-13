package com.example.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PinLockScreen
import com.example.ui.screens.ChapterListScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MangaReaderTheme

private enum class AppScreen {
    LOCKED,
    MAIN_TABS,
    MANGA_DETAILS,
    READER
}

@Composable
fun MangaApp(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accentPreset by viewModel.preferencesManager.accentPreset.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedManga by viewModel.selectedManga.collectAsStateWithLifecycle()
    val selectedChapter by viewModel.selectedChapter.collectAsStateWithLifecycle()

    // Re-lock app when it goes to background
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.lockApp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Storage Access Framework Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Take persistable permission so folder access survives app restarts
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                Log.e("MangaApp", "Failed to take persistable URI permission", e)
            }
            viewModel.setFolderUri(uri.toString())
        }
    }

    // Intercept hardware/system back button
    BackHandler(enabled = isAppLocked || selectedChapter != null || selectedManga != null || currentTab != NavTab.HOME) {
        if (isAppLocked) {
            (context as? android.app.Activity)?.finish()
        } else {
            viewModel.handleBackPress()
        }
    }

    val currentScreen = when {
        isAppLocked -> AppScreen.LOCKED
        selectedChapter != null && selectedManga != null -> AppScreen.READER
        selectedManga != null -> AppScreen.MANGA_DETAILS
        else -> AppScreen.MAIN_TABS
    }

    MangaReaderTheme(accentPreset = accentPreset) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    when {
                        targetState == AppScreen.LOCKED || initialState == AppScreen.LOCKED -> {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                        }
                        targetState == AppScreen.READER -> {
                            (slideInVertically(
                                initialOffsetY = { it / 3 },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(300))) togetherWith fadeOut(tween(200))
                        }
                        initialState == AppScreen.READER -> {
                            fadeIn(tween(250)) togetherWith (slideOutVertically(
                                targetOffsetY = { it / 3 },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(250)))
                        }
                        targetState == AppScreen.MANGA_DETAILS && initialState == AppScreen.MAIN_TABS -> {
                            (slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(300))) togetherWith (slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(250)))
                        }
                        initialState == AppScreen.MANGA_DETAILS && targetState == AppScreen.MAIN_TABS -> {
                            (slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(300))) togetherWith (slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(250)))
                        }
                        else -> {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                        }
                    }
                },
                label = "screen_navigation_transition",
                modifier = Modifier.fillMaxSize()
            ) { screen ->
                when (screen) {
                    AppScreen.LOCKED -> {
                        PinLockScreen(
                            title = "MangaReader is Locked",
                            subtitle = "Enter your 4-digit passcode to continue",
                            onPinComplete = { pin -> viewModel.unlockApp(pin) }
                        )
                    }
                    AppScreen.READER -> {
                        val manga = selectedManga
                        val chapter = selectedChapter
                        if (manga != null && chapter != null) {
                            ReaderScreen(
                                manga = manga,
                                chapter = chapter,
                                viewModel = viewModel
                            )
                        }
                    }
                    AppScreen.MANGA_DETAILS -> {
                        val manga = selectedManga
                        if (manga != null) {
                            ChapterListScreen(
                                manga = manga,
                                viewModel = viewModel
                            )
                        }
                    }
                    AppScreen.MAIN_TABS -> {
                        Scaffold(
                            bottomBar = {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 6.dp,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bottom_navigation_bar")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CompactNavItem(
                                            selected = currentTab == NavTab.HOME,
                                            onClick = { viewModel.selectTab(NavTab.HOME) },
                                            icon = if (currentTab == NavTab.HOME) Icons.Filled.AutoStories else Icons.Outlined.AutoStories,
                                            label = "Library",
                                            testTag = "nav_tab_home"
                                        )

                                        CompactNavItem(
                                            selected = currentTab == NavTab.SEARCH,
                                            onClick = { viewModel.selectTab(NavTab.SEARCH) },
                                            icon = if (currentTab == NavTab.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                                            label = "Search",
                                            testTag = "nav_tab_search"
                                        )

                                        CompactNavItem(
                                            selected = currentTab == NavTab.SETTINGS,
                                            onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                                            icon = if (currentTab == NavTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            label = "Settings",
                                            testTag = "nav_tab_settings"
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    val forward = targetState.ordinal > initialState.ordinal
                                    val slideOffset = if (forward) 80 else -80
                                    (slideInHorizontally(
                                        initialOffsetX = { slideOffset },
                                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                                    ) + fadeIn(tween(250))) togetherWith (slideOutHorizontally(
                                        targetOffsetX = { -slideOffset },
                                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                                    ) + fadeOut(tween(200)))
                                },
                                label = "tab_transition",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) { tab ->
                                when (tab) {
                                    NavTab.HOME -> HomeScreen(
                                        viewModel = viewModel,
                                        onSelectFolderClick = { folderPickerLauncher.launch(null) }
                                    )
                                    NavTab.SEARCH -> SearchScreen(viewModel = viewModel)
                                    NavTab.SETTINGS -> SettingsScreen(
                                        viewModel = viewModel,
                                        onSelectFolderClick = { folderPickerLauncher.launch(null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val color = if (selected) activeColor else inactiveColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}


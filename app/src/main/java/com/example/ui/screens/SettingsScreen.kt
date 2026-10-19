package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.ui.components.stableStatusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AccentPreset
import com.example.data.ReadingDirection
import com.example.data.db.CollectionEntity
import com.example.data.tracking.AniListApi
import android.content.Intent
import android.net.Uri
import com.example.ui.MangaViewModel
import com.example.ui.components.CollectionNameDialog
import com.example.ui.components.DeleteCollectionDialog
import com.example.ui.components.PinDialogMode
import com.example.ui.components.PinSetupDialog
import kotlinx.coroutines.launch

/** The pages the settings list opens; null in [SettingsScreen] is the list itself. */
private enum class SettingsPage(val title: String) {
    LIBRARY("Library & collections"),
    BACKUP("Backup & restore"),
    APPEARANCE("Appearance"),
    READER("Reader"),
    ANILIST("AniList"),
    STATISTICS("Reading statistics"),
    SECURITY("Security")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MangaViewModel,
    onSelectFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderUri by viewModel.preferencesManager.folderUri.collectAsStateWithLifecycle()
    val accentPreset by viewModel.preferencesManager.accentPreset.collectAsStateWithLifecycle()
    val readingDirection by viewModel.preferencesManager.readingDirection.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.preferencesManager.keepScreenOn.collectAsStateWithLifecycle()
    val isAppLockEnabled by viewModel.preferencesManager.isAppLockEnabled.collectAsStateWithLifecycle()
    val trackStatistics by viewModel.preferencesManager.trackStatistics.collectAsStateWithLifecycle()
    val inactivitySeconds by viewModel.preferencesManager.inactivityThresholdSeconds.collectAsStateWithLifecycle()
    val aniListUser by viewModel.preferencesManager.aniListUserName.collectAsStateWithLifecycle()
    val trackAutoUpdate by viewModel.preferencesManager.trackAutoUpdate.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val collectionMembers by viewModel.collectionMembers.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var renamingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var deletingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var backupBusy by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) coroutineScope.launch {
            backupBusy = true
            val message = viewModel.exportBackup(uri)
            backupBusy = false
            snackbarHostState.showSnackbar(message)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutineScope.launch {
            backupBusy = true
            // Returns only on failure; on success the app restarts
            val error = viewModel.restoreBackup(uri)
            backupBusy = false
            snackbarHostState.showSnackbar(error)
        }
    }

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
                        .padding(start = if (page == null) 16.dp else 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page != null) {
                        IconButton(onClick = { page = null }, modifier = Modifier.testTag("settings_back")) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        text = page?.title ?: "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        // Each page starts scrolled to the top
        key(page) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (page) {
                    null -> {
                        SettingsHeader(title = "LIBRARY")
                        SettingsCard {
                            SettingsRow(
                                icon = Icons.Default.FolderOpen,
                                title = SettingsPage.LIBRARY.title,
                                summary = listOfNotNull(
                                    folderUri?.takeIf { it.isNotBlank() }?.let(::readableFolderPath) ?: "No folder selected yet",
                                    collections.size.takeIf { it > 0 }?.let { if (it == 1) "1 collection" else "$it collections" }
                                ).joinToString(" · "),
                                tag = "settings_library",
                                onClick = { page = SettingsPage.LIBRARY }
                            )
                            RowDivider()
                            SettingsRow(
                                icon = Icons.Default.Backup,
                                title = SettingsPage.BACKUP.title,
                                summary = "Progress, stats and settings in one file",
                                tag = "settings_backup",
                                onClick = { page = SettingsPage.BACKUP }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        SettingsHeader(title = "LOOK & READING")
                        SettingsCard {
                            SettingsRow(
                                icon = Icons.Default.ColorLens,
                                title = SettingsPage.APPEARANCE.title,
                                summary = "Accent: ${accentPreset.displayName}",
                                tag = "settings_appearance",
                                onClick = { page = SettingsPage.APPEARANCE }
                            )
                            RowDivider()
                            SettingsRow(
                                icon = Icons.Default.MenuBook,
                                title = SettingsPage.READER.title,
                                summary = readingDirection.displayName + if (keepScreenOn) " · screen stays on" else "",
                                tag = "settings_reader",
                                onClick = { page = SettingsPage.READER }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        SettingsHeader(title = "TRACKING")
                        SettingsCard {
                            SettingsRow(
                                icon = Icons.Default.Sync,
                                title = SettingsPage.ANILIST.title,
                                summary = aniListUser?.let { "Logged in as $it" } ?: "Not logged in",
                                tag = "settings_anilist",
                                onClick = { page = SettingsPage.ANILIST }
                            )
                            RowDivider()
                            SettingsRow(
                                icon = Icons.Default.BarChart,
                                title = SettingsPage.STATISTICS.title,
                                summary = if (trackStatistics) "On · pause after ${inactivitySeconds / 60} min" else "Off",
                                tag = "settings_statistics",
                                onClick = { page = SettingsPage.STATISTICS }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        SettingsHeader(title = "APP")
                        SettingsCard {
                            SettingsRow(
                                icon = Icons.Default.Lock,
                                title = SettingsPage.SECURITY.title,
                                summary = if (isAppLockEnabled) "PIN lock on" else "PIN lock off",
                                tag = "settings_security",
                                onClick = { page = SettingsPage.SECURITY }
                            )
                            RowDivider()
                            SettingsRow(
                                icon = Icons.Default.Info,
                                title = "Moon Reader v${com.example.BuildConfig.VERSION_NAME}",
                                summary = "Fast, offline CBZ manga reader for Android"
                            )
                        }
                    }

                    SettingsPage.LIBRARY -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Manga Root Folder",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = folderUri?.takeIf { it.isNotBlank() }?.let(::readableFolderPath) ?: "No folder selected yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onSelectFolderClick,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("change_folder_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Change Folder")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.scanLibrary()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Library scanning in progress")
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("rescan_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Re-scan")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Also reachable from the Library tabs; kept here so all collection actions are in one list
                        SettingsHeader(title = "COLLECTIONS")
                        SettingsCard {
                            SettingsRow(
                                icon = Icons.Default.Add,
                                title = "New collection",
                                tag = "settings_new_collection",
                                showChevron = false,
                                onClick = { showNewCollectionDialog = true }
                            )
                            if (collections.isEmpty()) {
                                RowDivider()
                                Text(
                                    text = "No collections yet. Long-press a series in the library to add it to one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            collections.forEach { collection ->
                                RowDivider()
                                val count = collectionMembers[collection.id]?.size ?: 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = collection.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (count == 1) "1 series" else "$count series",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { renamingCollection = collection },
                                        modifier = Modifier.testTag("settings_rename_collection")
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename ${collection.name}")
                                    }
                                    IconButton(
                                        onClick = { deletingCollection = collection },
                                        modifier = Modifier.testTag("settings_delete_collection")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete ${collection.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.BACKUP -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Backup,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (backupBusy) "Working…" else "Library Backup",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Progress, stats and settings in one file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { exportLauncher.launch("moonreader-backup-${java.time.LocalDate.now()}.zip") },
                                        enabled = !backupBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("backup_export_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Back Up")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            // Restored IDs are rewritten onto the selected folder, so it has to be chosen first
                                            if (folderUri?.startsWith("content://") == true) {
                                                showRestoreDialog = true
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Choose your library folder first, so restored progress matches your files")
                                                }
                                            }
                                        },
                                        enabled = !backupBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("backup_restore_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.APPEARANCE -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ColorLens,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Accent Color",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Current: ${accentPreset.displayName}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Color palette pills row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AccentPreset.values().forEach { preset ->
                                        val isSelected = preset == accentPreset
                                        val dotColor = if (preset.darkHex != 0L) Color(preset.darkHex) else MaterialTheme.colorScheme.primary

                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .clickable { viewModel.setAccentPreset(preset) }
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .testTag("accent_color_${preset.name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.READER -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Default Reading Direction",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

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
                                        Text(
                                            text = dir.displayName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Keep Screen On",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Prevents screen from turning off while reading",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = keepScreenOn,
                                        onCheckedChange = { viewModel.setKeepScreenOn(it) }
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.ANILIST -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "AniList",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = aniListUser?.let { "Logged in as $it" } ?: "Sync reading progress to your list",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (aniListUser == null) {
                                        Button(
                                            onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AniListApi.LOGIN_URL)))
                                            },
                                            modifier = Modifier.testTag("anilist_login")
                                        ) { Text("Log in") }
                                    } else {
                                        OutlinedButton(onClick = { viewModel.logoutAniList() }) { Text("Log out") }
                                    }
                                }

                                if (aniListUser != null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Update progress after reading",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Finishing a chapter or volume updates linked series",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = trackAutoUpdate,
                                            onCheckedChange = { viewModel.setTrackAutoUpdate(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.STATISTICS -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Track Reading Activity",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Log duration, pages, chapters read, and streaks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = trackStatistics,
                                        onCheckedChange = { viewModel.setTrackStatistics(it) }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                Text(
                                    text = "Inactivity Auto-Pause",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Pause timer when reader has no interaction",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(60 to "1 min", 120 to "2 min", 300 to "5 min").forEach { (secs, label) ->
                                        val isSelected = inactivitySeconds == secs
                                        OutlinedButton(
                                            onClick = { viewModel.setInactivityThresholdSeconds(secs) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                            )
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                OutlinedButton(
                                    onClick = { showClearStatsDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reset Reading Statistics",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.SECURITY -> {
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "PIN App Lock",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isAppLockEnabled) "App is protected with PIN" else "Disabled",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isAppLockEnabled,
                                        onCheckedChange = { enable ->
                                            if (enable) {
                                                pinDialogMode = PinDialogMode.SET_NEW
                                            } else {
                                                pinDialogMode = PinDialogMode.DISABLE_PIN
                                            }
                                        }
                                    )
                                }

                                if (isAppLockEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { pinDialogMode = PinDialogMode.CHANGE_PIN },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Change PIN")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // PIN Setup / Change Dialogs
    pinDialogMode?.let { mode ->
        PinSetupDialog(
            mode = mode,
            onSuccess = {
                pinDialogMode = null
                coroutineScope.launch { snackbarHostState.showSnackbar("PIN settings updated") }
            },
            onDismiss = { pinDialogMode = null },
            verifyCurrentPin = { pin -> viewModel.verifyPin(pin) },
            saveNewPin = { pin -> viewModel.setNewPin(pin) },
            disableLock = { viewModel.disableAppLockDirect() }
        )
    }

    if (showNewCollectionDialog) {
        CollectionNameDialog(
            title = "New collection",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createCollection(name)
                showNewCollectionDialog = false
            },
            onDismiss = { showNewCollectionDialog = false }
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

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(imageVector = Icons.Default.Restore, contentDescription = null) },
            title = { Text("Restore a Backup?") },
            text = {
                Text(
                    "Your current reading progress, statistics, favorites, collections, AniList links and settings " +
                        "will be replaced by the backup's. Progress is matched to the files in your selected library " +
                        "folder. The app restarts when it's done."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                    modifier = Modifier.testTag("backup_restore_confirm")
                ) {
                    Text("Choose Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Reset Reading Statistics?") },
            text = { Text("This will delete all recorded reading sessions and streaks. Your manga files, covers, and library will not be modified.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearStatistics()
                        showClearStatsDialog = false
                        coroutineScope.launch { snackbarHostState.showSnackbar("Reading statistics reset") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** A list row showing a setting's current value; tappable when [onClick] is given. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    tag: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onClick != null && showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Turns a SAF tree URI like `…/tree/primary%3AManga%2Flocal` into `Internal storage/Manga/local`. */
internal fun readableFolderPath(uriString: String): String {
    val uri = Uri.parse(uriString)
    val docId = uri.pathSegments.takeIf { uri.scheme == "content" && it.firstOrNull() == "tree" }
        ?.getOrNull(1) ?: return uriString
    if (uri.authority != "com.android.externalstorage.documents") return docId
    val volume = docId.substringBefore(':')
    val path = docId.substringAfter(':', "")
    val root = if (volume == "primary") "Internal storage" else volume
    return if (path.isEmpty()) root else "$root/$path"
}

package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.Manga
import com.example.data.db.MangaTrackEntity
import com.example.data.tracking.AniListMedia
import com.example.data.tracking.ScoreFormat
import com.example.data.tracking.TrackStatus
import com.example.ui.MangaViewModel
import com.example.ui.NavTab
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Mihon-style tracking sheet: search and link the series on AniList, then edit its list entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingSheet(manga: Manga, viewModel: MangaViewModel, onDismiss: () -> Unit) {
    val userName by viewModel.preferencesManager.aniListUserName.collectAsStateWithLifecycle()
    val track by viewModel.selectedTrack.collectAsStateWithLifecycle()
    val scoreFormat by viewModel.preferencesManager.aniListScoreFormat.collectAsStateWithLifecycle()
    var changingMatch by remember { mutableStateOf(false) }

    // Pick up edits made on the website since the last sync
    LaunchedEffect(manga.id) { viewModel.refreshTrack(manga.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val current = track
            when {
                userName == null -> {
                    Text(
                        "Log in to AniList to track this series.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            viewModel.selectTab(NavTab.SETTINGS)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("track_go_to_settings")
                    ) { Text("Go to Settings to log in") }
                }
                current == null || changingMatch -> TrackSearch(manga, viewModel) { media ->
                    viewModel.linkTrack(manga, media)
                    changingMatch = false
                }
                else -> TrackDetails(
                    track = current,
                    scoreFormat = ScoreFormat.from(scoreFormat),
                    onSave = viewModel::updateTrack,
                    onChangeMatch = { changingMatch = true },
                    onRemove = { viewModel.unlinkTrack(manga.id) }
                )
            }
        }
    }
}

@Composable
private fun TrackSearch(manga: Manga, viewModel: MangaViewModel, onPick: (AniListMedia) -> Unit) {
    var query by rememberSaveable(manga.id) { mutableStateOf(viewModel.trackSearchQuery(manga)) }
    var results by remember { mutableStateOf<List<AniListMedia>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            error = null
            runCatching { viewModel.searchAniList(query.trim(), manga) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Search failed" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { search() }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        label = { Text("Search AniList") },
        trailingIcon = {
            IconButton(onClick = { search() }) { Icon(Icons.Default.Search, contentDescription = "Search") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("track_search_field")
    )
    Spacer(Modifier.height(8.dp))
    when {
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        results?.isEmpty() == true -> Text("No results. Try a shorter title.")
        else -> LazyColumn(Modifier.heightIn(max = 440.dp)) {
            items(results.orEmpty(), key = { it.id }) { media ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(media) }
                        .padding(vertical = 6.dp)
                ) {
                    Cover(media.coverUrl)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(media.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                media.format?.replace('_', ' '),
                                media.year?.toString(),
                                media.chapters.takeIf { it > 0 }?.let { "$it ch" },
                                media.volumes.takeIf { it > 0 }?.let { "$it vol" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackDetails(
    track: MangaTrackEntity,
    scoreFormat: ScoreFormat,
    onSave: (MangaTrackEntity) -> Unit,
    onChangeMatch: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    // Edits collect in a draft and go out in one request, so quick taps on +/- aren't lost
    var draft by remember(track) { mutableStateOf(track) }
    var showMenu by remember { mutableStateOf(false) }

    // Header: cover, title, sync state, open on AniList and a menu for the rarer actions (like Mihon)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(track.coverUrl, width = 56.dp, height = 80.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (track.pendingSync) "Waiting to sync" else "Synced with AniList",
                style = MaterialTheme.typography.labelSmall,
                color = if (track.pendingSync) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://anilist.co/manga/${track.remoteId}")))
        }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on AniList")
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("track_menu")) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Change match") },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onClick = { showMenu = false; onChangeMatch() }
                )
                DropdownMenuItem(
                    text = { Text("Remove tracking", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onRemove() }
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusTile(draft.status, Modifier.weight(1f)) { draft = draft.copy(status = it) }
        ScoreTile(draft.score, scoreFormat, Modifier.weight(1f)) { draft = draft.copy(score = it) }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperTile("Chapters", draft.progress, draft.totalChapters, Modifier.weight(1f)) { draft = draft.copy(progress = it) }
        StepperTile("Volumes", draft.progressVolumes, draft.totalVolumes, Modifier.weight(1f)) { draft = draft.copy(progressVolumes = it) }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateTile("Started", draft.startedAt, Modifier.weight(1f)) { draft = draft.copy(startedAt = it) }
        DateTile("Finished", draft.completedAt, Modifier.weight(1f)) { draft = draft.copy(completedAt = it) }
    }

    if (draft != track) {
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { draft = track }, modifier = Modifier.weight(1f)) { Text("Discard") }
            Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f).testTag("track_save")) { Text("Save") }
        }
    }
}

/** A labelled tile; tapping it edits the value. */
@Composable
private fun Tile(label: String, modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun TileValue(text: String, muted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StatusTile(status: TrackStatus?, modifier: Modifier, onChange: (TrackStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Tile("Status", Modifier.fillMaxWidth(), onClick = { open = true }) {
            TileValue(status?.displayName ?: "Not set", muted = status == null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TrackStatus.entries.forEach {
                DropdownMenuItem(
                    text = { Text(it.displayName, fontWeight = if (it == status) FontWeight.Bold else null) },
                    onClick = { open = false; onChange(it) }
                )
            }
        }
    }
}

/** Score in the user's AniList format; [raw] is AniList's 0–100 value. */
@Composable
private fun ScoreTile(raw: Int, format: ScoreFormat, modifier: Modifier, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Tile("Score", modifier, onClick = { open = true }) {
        TileValue(format.label(raw), muted = raw == 0)
    }
    if (open) {
        var step by remember { mutableStateOf(format.toStep(raw).toFloat()) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Score") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        format.label(format.toRaw(step.roundToInt())),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = step,
                        onValueChange = { step = it },
                        valueRange = 0f..format.steps.toFloat(),
                        // Slider "steps" are the stops between the ends
                        steps = format.steps - 1,
                        modifier = Modifier.testTag("score_slider")
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onChange(format.toRaw(step.roundToInt())); open = false }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (raw > 0) TextButton(onClick = { onChange(0); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun StepperTile(label: String, value: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Tile(label, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString { append(value); if (max > 0) append(" / ").append(max) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SmallIconButton(Icons.Default.Remove, "Less $label") { onChange((value - 1).coerceAtLeast(0)) }
            Spacer(Modifier.width(6.dp))
            SmallIconButton(Icons.Default.Add, "More $label") {
                onChange(if (max > 0) (value + 1).coerceAtMost(max) else value + 1)
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(30.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp)) }
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTile(label: String, iso: String?, modifier: Modifier, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Tile(label, modifier, onClick = { picking = true }) {
        TileValue(iso?.let { LocalDate.parse(it).format(DATE_FORMAT) } ?: "Not set", muted = iso == null)
    }
    if (picking) {
        // The date picker works in UTC midnight millis
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (iso?.let { LocalDate.parse(it) } ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (iso != null) TextButton(onClick = { onChange(null); picking = false }) { Text("Clear") }
                    TextButton(onClick = { picking = false }) { Text("Cancel") }
                }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Cover(url: String?, width: Dp = 48.dp, height: Dp = 68.dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

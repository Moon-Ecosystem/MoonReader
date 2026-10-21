package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CollectionEntity
import com.example.data.preferences.PreferencesManager

/**
 * Home screen tabs: All, then each collection, then "+" to make one. Long-press a collection to rename or
 * delete it. [counts] holds the number shown on each tab (keyed like [PreferencesManager.ALL_COLLECTIONS]).
 * [pagerPosition] is the pager's fractional page (All is 0); the underline slides with it during a swipe.
 */
@Composable
fun CollectionTabs(
    collections: List<CollectionEntity>,
    selected: Long,
    pagerPosition: () -> Float,
    counts: Map<Long, Int>,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onRename: (CollectionEntity) -> Unit,
    onDelete: (CollectionEntity) -> Unit
) {
    // Where each tab sits in the row, keyed by page index, so the underline can move between two of them
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
    fun Modifier.trackBounds(index: Int) = onPlaced { tabBounds[index] = Rect(it.positionInParent(), it.size.toSize()) }
    val indicator = MaterialTheme.colorScheme.primary
    // A plain scrolling row: ScrollableTabRow also gives "+" a 90 dp slot, which left it far from the tabs
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            // Underline under the tab the pager is on, blended into the next one as the swipe progresses;
            // read while drawing, so a swipe redraws the row without recomposing it
            .drawBehind {
                val position = pagerPosition().coerceIn(0f, collections.size.toFloat())
                val from = tabBounds[floor(position).toInt()] ?: return@drawBehind
                val to = tabBounds[ceil(position).toInt()] ?: from
                val bounds = lerp(from, to, position - floor(position))
                val thickness = 3.dp.toPx()
                drawRoundRect(
                    color = indicator,
                    topLeft = Offset(bounds.left, bounds.bottom - thickness),
                    size = Size(bounds.width, thickness),
                    cornerRadius = CornerRadius(thickness / 2)
                )
            }
            .testTag("collection_tabs")
    ) {
        CollectionTab(
            label = "All",
            count = counts[PreferencesManager.ALL_COLLECTIONS],
            selected = selected == PreferencesManager.ALL_COLLECTIONS,
            onClick = { onSelect(PreferencesManager.ALL_COLLECTIONS) },
            modifier = Modifier.trackBounds(0).testTag("collection_tab_all")
        )
        collections.forEachIndexed { index, collection ->
            CollectionTab(
                label = collection.name,
                count = counts[collection.id],
                selected = collection.id == selected,
                onClick = { onSelect(collection.id) },
                onRename = { onRename(collection) },
                onDelete = { onDelete(collection) },
                modifier = Modifier.trackBounds(index + 1).testTag("collection_tab_${collection.id}")
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onCreate)
                .padding(horizontal = 10.dp)
                .testTag("collection_tab_new")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New collection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// A plain Box instead of Tab, which can't take a long press
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionTab(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 90.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onRename != null) ({ showMenu = true }) else null
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count != null) {
                Text(
                    text = " $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        if (onRename != null && onDelete != null) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { showMenu = false; onRename() },
                    modifier = Modifier.testTag("option_rename_collection")
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { showMenu = false; onDelete() },
                    modifier = Modifier.testTag("option_delete_collection")
                )
            }
        }
    }
}

/** Asks for a collection name; used both to create and to rename. */
@Composable
fun CollectionNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val submit = { if (name.isNotBlank()) onConfirm(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("collection_name_field")
            )
        },
        confirmButton = {
            Button(onClick = submit, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun DeleteCollectionDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = { Text("The series in it stay in your library with their progress; only the collection is removed.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Checklist of collections for one series; each tick applies right away. */
@Composable
fun CollectionPickerDialog(
    title: String,
    collections: List<CollectionEntity>,
    memberOf: Set<Long>,
    onToggle: (collectionId: Long, member: Boolean) -> Unit,
    onNewCollection: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (collections.isEmpty()) {
                Text("No collections yet. Make one to start grouping your series.")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    collections.forEach { collection ->
                        val checked = collection.id in memberOf
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(collection.id, !checked) }
                                .testTag("collection_option_${collection.id}")
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onNewCollection, modifier = Modifier.testTag("collection_new_button")) {
                Text("New collection")
            }
        }
    )
}

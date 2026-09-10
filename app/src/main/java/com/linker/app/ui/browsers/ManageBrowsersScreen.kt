package com.linker.app.ui.browsers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linker.app.R
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.ui.components.AppIcon
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.theme.nord13
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageBrowsersScreen() {
    val container = rememberAppContainer()
    val viewModel: ManageBrowsersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ManageBrowsersViewModel(container.browserPrefsRepository) }
        }
    )
    val browsers by viewModel.browsers.collectAsState()
    var renameTarget by remember { mutableStateOf<BrowserListItem?>(null) }

    val activeBrowsers = remember(browsers) { browsers.filterNot { it.hidden } }
    val hiddenBrowsers = remember(browsers) { browsers.filter { it.hidden } }

    // Local list keeps drag reordering smooth without fighting DB re-emits mid-drag.
    // Only the active section is draggable, so the drag source is just that subset.
    var localList by remember { mutableStateOf(activeBrowsers) }
    LaunchedEffect(activeBrowsers) { localList = activeBrowsers }

    Column(Modifier.fillMaxSize()) {
        // Forces a rescan, e.g. for a browser installed while this screen is open.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh browser list")
            }
        }

        if (localList.isEmpty() && hiddenBrowsers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No browsers found on this device.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = from.index.coerceIn(0, localList.size - 1)
            // Only the active section is in this list, but the LazyColumn also renders the Hidden
            // header/rows after it, so a drop down there lands at the end of the active list.
            val toIndex = to.index.coerceIn(0, localList.size - 1)
            localList = localList.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }

        // Render both sections through one LazyColumn with a stable key per browser, so toggling
        // a switch keeps the same item in the list and it *glides* from the active section to the
        // hidden one instead of teleporting. Active rows animate via the reorderable library's own
        // Modifier.animateItem() — stacking a second one on the same node makes drags stutter, so
        // the slower spring below is only for the non-draggable hidden rows and header. Hidden data
        // still comes from the live flow; the overlap filter is unchanged, so no duplicate keys.
        val rows = remember(localList, hiddenBrowsers) {
            buildList {
                addAll(localList.map { ManageRow.BrowserItem(it) })
                val hidden = hiddenBrowsers.filterNot { h -> localList.any { it.packageName == h.packageName } }
                if (hidden.isNotEmpty()) {
                    add(ManageRow.HiddenHeader)
                    addAll(hidden.map { ManageRow.BrowserItem(it) })
                }
            }
        }
        val activeKeys = remember(localList) { localList.mapTo(HashSet()) { it.packageName } }

        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ManageRow.BrowserItem -> {
                        if (row.browser.packageName in activeKeys) {
                            ReorderableItem(
                                reorderableState,
                                key = row.browser.packageName
                            ) { isDragging ->
                                BrowserRow(
                                    browser = row.browser,
                                    isDragging = isDragging,
                                    showDragHandle = true,
                                    onToggleHidden = { viewModel.toggleHidden(row.browser) },
                                    onRename = { renameTarget = row.browser },
                                    dragHandleModifier = Modifier.draggableHandle(
                                        onDragStopped = { viewModel.persistOrder(localList) }
                                    )
                                )
                            }
                        } else {
                            // The toggled browser mechanically lands in the hidden slot the moment
                            // the flow emits — a placement spring over a long jump still reads as a
                            // teleport. So entering the Hidden section gets its own visible motion:
                            // the row grows down from the header and fades in, so the move reads as
                            // a deliberate transition instead of a snap.
                            val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(
                                visibleState = enterState,
                                enter = expandVertically(tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                            ) {
                                BrowserRow(
                                    browser = row.browser,
                                    isDragging = false,
                                    showDragHandle = false,
                                    onToggleHidden = { viewModel.toggleHidden(row.browser) },
                                    onRename = { renameTarget = row.browser },
                                    dragHandleModifier = Modifier,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = SectionPlacementSpec,
                                        fadeOutSpec = null
                                    )
                                )
                            }
                        }
                    }
                    ManageRow.HiddenHeader -> {
                        val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = enterState,
                            enter = expandVertically(tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                        ) {
                            Text(
                                text = "Hidden",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        fadeInSpec = null,
                                        placementSpec = SectionPlacementSpec,
                                        fadeOutSpec = null
                                    )
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { browser ->
        RenameBrowserDialog(
            browser = browser,
            onDismiss = { renameTarget = null },
            onSave = { newLabel ->
                viewModel.rename(browser, newLabel)
                renameTarget = null
            },
            onReset = {
                viewModel.resetLabel(browser)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun BrowserRow(
    browser: BrowserListItem,
    isDragging: Boolean,
    showDragHandle: Boolean,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
    Surface(shadowElevation = elevation) {
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDragHandle) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier
                )
            } else {
                // No reordering in the hidden section, but keep the drag-handle slot so
                // icons and names stay aligned with the active section above.
                Spacer(Modifier.width(24.dp))
            }
            Spacer(Modifier.width(8.dp))
            AppIcon(browser.icon, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = browser.displayLabel,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (browser.displayLabel != browser.systemLabel) {
                    Text(
                        text = browser.systemLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            }
            Switch(checked = !browser.hidden, onCheckedChange = { onToggleHidden() })
        }
    }
}

@Composable
private fun RenameBrowserDialog(
    browser: BrowserListItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var text by remember(browser.packageName) { mutableStateOf(browser.displayLabel) }

    // Nord yellow pops on dark surfaces; light mode needs a darker amber to keep contrast.
    val resetColor = if (isSystemInDarkTheme()) nord13 else Color(0xFF9A7700)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Rename browser",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onReset) {
                        Text("Reset", color = resetColor)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onSave(text) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// Softer, slower spring than Modifier.animateItem()'s default (stiffness MediumLow), so the
// hidden section unfolds gently instead of snapping while the toggled row glides within it.
// Only used on the two non-reorderable items — the draggable active rows must not stack their
// own animateItem() on top of the reorderable library's, or drags start stuttering.
private val SectionPlacementSpec = spring<IntOffset>(
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = IntOffset.VisibilityThreshold
)

// One flat source for the LazyColumn so a browser's key stays stable while toggling moves it
// between the active and hidden sections: animateItem() can glide the move, and the item can't
// appear twice under two different keys right after a toggle.
private sealed interface ManageRow {
    val key: String

    data class BrowserItem(val browser: BrowserListItem) : ManageRow {
        override val key get() = browser.packageName
    }

    data object HiddenHeader : ManageRow {
        override val key get() = "hiddenHeader"
    }
}

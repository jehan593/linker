package com.linker.app.ui.browsers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.ui.components.AppIcon
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.theme.nord12
import com.linker.app.ui.theme.nord13
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    // Available browsers ordered the same way as `browsers`, oldest-hidden first — so the browser
    // you just disabled lands at the bottom of the Hidden section.
    val hiddenBrowsers = remember(browsers) {
        browsers.filter { it.hidden }.sortedBy { it.hiddenAtMillis ?: Long.MIN_VALUE }
    }

    // Local list keeps drag reordering smooth without fighting DB re-emits mid-drag.
    // Only the active section is draggable, so the drag source is just that subset. Mutated in
    // place (never re-assigned) during a drag and reconciled against `activeBrowsers` in
    // composition — the same snapshot-state pattern ownapps uses for its pinned rows, so the
    // library's per-move layout-info wait settles in one frame instead of stalling the gesture.
    val localList = remember { mutableStateListOf<BrowserListItem>() }
    val activePackageNames = remember(activeBrowsers) {
        activeBrowsers.mapTo(HashSet()) { it.packageName }
    }
    if (activePackageNames != localList.mapTo(HashSet()) { it.packageName }) {
        localList.clear()
        localList.addAll(activeBrowsers)
    }

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()

    // Fold-in animation for the hidden section is driven by the toggle event, never by a fresh
    // render (tab visit, cold start, load hand-off). recentlyHidden records which browsers were
    // switched live on this screen; only those fold in when the flow echoes them right back out.
    var recentlyHidden by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Softly reveal the list when the loading spinner hands off, instead of rows popping in.
    val listAlpha = remember { Animatable(if (isLoading) 0f else 1f) }
    LaunchedEffect(isLoading) {
        if (!isLoading) listAlpha.animateTo(1f, tween(300))
    }

    // Pull-to-refresh forces a rescan, e.g. for a browser installed while this screen is open.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                val start = System.currentTimeMillis()
                viewModel.refresh()
                // Keep the indicator up for a minimum so a fast refresh still reads as one.
                delay((MIN_PULL_REFRESH_MILLIS - (System.currentTimeMillis() - start)).coerceAtLeast(0))
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PullToRefreshBox
        }

        if (localList.isEmpty() && hiddenBrowsers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .graphicsLayer { alpha = listAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No browsers found on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@PullToRefreshBox
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            if (localList.size < 2) return@rememberReorderableLazyListState
            val fromIndex = from.index.coerceIn(0, localList.lastIndex)
            // Only the active section is in this list, but the LazyColumn also renders the Hidden
            // header/rows after it, so a drop down there lands at the end of the active list.
            val toIndex = to.index.coerceIn(0, localList.lastIndex)
            // Skip same-slot moves: a content-unchanged mutation still waits for a layout update
            // it will never get, stalling the move mutex and dropping every later drag frame.
            if (fromIndex != toIndex) {
                localList.add(toIndex, localList.removeAt(fromIndex))
            }
        }

        // Render both sections through one LazyColumn with a stable key per browser, so toggling
        // a switch keeps the same item in the list. Active rows are the only ReorderableItems (the
        // drag source stays limited to that subset, exactly like the reference behavior); hidden
        // rows are plain items that get their own visible motion instead. Hidden data still comes
        // from the live flow; the overlap filter is unchanged, so no duplicate keys.
        // Derived (ownapps' `displayPinned` pattern): re-evaluated whenever the in-place list
        // mutation is read, regardless of the remember keys staying stable.
        val rows by remember(localList, hiddenBrowsers) {
            derivedStateOf {
                buildList {
                    addAll(localList.map { ManageRow.BrowserItem(it) })
                    val hidden = hiddenBrowsers.filterNot { h -> localList.any { it.packageName == h.packageName } }
                    if (hidden.isNotEmpty()) {
                        add(ManageRow.HiddenHeader)
                        addAll(hidden.map { ManageRow.BrowserItem(it) })
                    }
                }
            }
        }
        val activeKeys by remember(localList) {
            derivedStateOf { localList.mapTo(HashSet()) { it.packageName } }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = listAlpha.value },
            contentPadding = PaddingValues(top = 8.dp)
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ManageRow.BrowserItem -> {
                        if (row.browser.packageName in activeKeys) {
                            ReorderableItem(
                                reorderableState,
                                key = row.browser.packageName,
                                // No fade on composition (tab visit / fresh render) — placement-only
                                // movement on actual drags and toggles, same as ownscreen/ownapps.
                                animateItemModifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null
                                )
                            ) { isDragging ->
                                BrowserRow(
                                    browser = row.browser,
                                    isDragging = isDragging,
                                    showReorderGrip = true,
                                    onToggleHidden = {
                                        recentlyHidden = recentlyHidden + row.browser.packageName
                                        viewModel.toggleHidden(row.browser)
                                    },
                                    onRename = { renameTarget = row.browser },
                                    reorderGripModifier = Modifier
                                        .draggableHandle(
                                            onDragStopped = { viewModel.persistOrder(localList) }
                                        )
                                        // Padding grows the touch target from the bare 9dp dot grid
                                        // to a 25dp-wide handle — ownapps' exact modifier chain.
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            // The toggled browser mechanically lands in the hidden slot the moment
                            // the flow emits — a placement spring over a long jump still reads as a
                            // teleport. So entering the Hidden section gets its own visible motion:
                            // the row grows down from the header and fades in, so the move reads as
                            // a deliberate transition instead of a snap.
                            val enterState = remember {
                                MutableTransitionState(row.browser.packageName !in recentlyHidden).apply { targetState = true }
                            }
                            AnimatedVisibility(
                                visibleState = enterState,
                                enter = expandVertically(tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                            ) {
                                BrowserRow(
                                    browser = row.browser,
                                    isDragging = false,
                                    showReorderGrip = false,
                                    onToggleHidden = { viewModel.toggleHidden(row.browser) },
                                    onRename = { renameTarget = row.browser },
                                    reorderGripModifier = Modifier,
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
                        val enterState = remember {
                            MutableTransitionState(recentlyHidden.isEmpty()).apply { targetState = true }
                        }
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
    showReorderGrip: Boolean,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    reorderGripModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(browser.icon, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = browser.displayLabel,
                    // Long names step down a size (and clamp to one line) so the row stays
                    // compact instead of squeezing the edit icon and switch.
                    style = if (browser.displayLabel.length > 20) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (browser.displayLabel != browser.systemLabel) {
                    Text(
                        text = browser.systemLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showReorderGrip) {
                // Faint 3x2 grip-dot grid on the right, like ownapps' pinned rows — the drag handle
                // sits right before the secondary (edit) action, mirroring ownapps' pin placement.
                ReorderGrip(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = reorderGripModifier
                )
            } else {
                // No grip in the hidden section, but keep its slot so the edit icon and switch stay
                // aligned with the active rows above (dot grid 9dp + 8dp side padding each side).
                Spacer(Modifier.width(25.dp))
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            }
            Spacer(Modifier.width(8.dp))
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

    // Nord yellow pops on dark surfaces; amber keeps contrast in light mode.
    val resetColor = if (isSystemInDarkTheme()) nord13 else nord12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename browser") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReset) {
                    Text("Reset", color = resetColor)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

// Softer, slower spring than Modifier.animateItem()'s default (stiffness MediumLow), so the
// hidden section unfolds gently instead of snapping while the toggled row glides within it.
// Only used on the non-reorderable hidden rows and header — the draggable active rows must not
// stack their own animateItem() on top of the reorderable library's, or drags start stuttering.
private val SectionPlacementSpec = spring<IntOffset>(
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = IntOffset.VisibilityThreshold
)

// Ownapps-style grip: a faint 3x2 grid of dots marking a row as reorderable. Left bare of padding —
// the caller's draggableHandle modifier carries the 8dp side padding and the touch target.
@Composable
private fun ReorderGrip(color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                GripDot(color)
                GripDot(color)
            }
        }
    }
}

@Composable
private fun GripDot(color: Color) {
    Box(
        Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// Long enough for a fast rescan to still read as a deliberate refresh.
private const val MIN_PULL_REFRESH_MILLIS = 800L

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
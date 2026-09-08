package com.linker.app.ui.browsers

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextDecoration
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

    // Local list keeps drag reordering smooth without fighting DB re-emits mid-drag.
    var localList by remember { mutableStateOf(browsers) }
    LaunchedEffect(browsers) { localList = browsers }

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

        if (localList.isEmpty()) {
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
            localList = localList.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }

        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            items(localList, key = { it.packageName }) { browser ->
                ReorderableItem(reorderableState, key = browser.packageName) { isDragging ->
                    BrowserRow(
                        browser = browser,
                        isDragging = isDragging,
                        onToggleHidden = { viewModel.toggleHidden(browser) },
                        onRename = { renameTarget = browser },
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = { viewModel.persistOrder(localList) }
                        )
                    )
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
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    dragHandleModifier: Modifier
) {
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
    Surface(shadowElevation = elevation) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier
            )
            Spacer(Modifier.width(8.dp))
            AppIcon(browser.icon, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = browser.displayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (browser.hidden) TextDecoration.LineThrough else TextDecoration.None
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
                    modifier = Modifier.fillMaxWidth()
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
                    FilledTonalButton(onClick = { onSave(text) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

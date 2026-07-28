package com.linker.app.ui.savedlinks

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linker.app.R
import com.linker.app.data.db.entity.SavedLinkEntity
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.theme.nord0
import com.linker.app.ui.theme.nord13
import com.linker.app.util.dayKey
import com.linker.app.util.dayLabel
import com.linker.app.util.formatSavedTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedLinksScreen() {
    val container = rememberAppContainer()
    val viewModel: SavedLinksViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SavedLinksViewModel(container.savedLinksRepository, container.notesnookRepository) }
        }
    )
    val links by viewModel.links.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var editTarget by remember { mutableStateOf<SavedLinkEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Grouped after filtering, so a search still reads as day-organized rather than a flat list.
    // groupBy preserves first-seen key order, and links is already sorted newest-first, so the
    // resulting day groups come out newest-first too without any extra sorting here.
    val groupedByDay = remember(links) { links.groupBy { dayKey(it.savedAtMillis) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.searchText,
            onValueChange = viewModel::onSearchChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search saved links") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (viewModel.searchText.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchChanged("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true
        )

        if (links.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (viewModel.searchText.isEmpty())
                        "No saved links yet — use the bookmark icon in the link chooser to save one."
                    else
                        "No saved links match \"${viewModel.searchText}\".",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groupedByDay.forEach { (day, linksForDay) ->
                    stickyHeader(key = day.toString()) {
                        Text(
                            text = dayLabel(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(linksForDay, key = { it.id }) { link ->
                        SavedLinkRow(
                            link = link,
                            searchQuery = viewModel.searchText,
                            onOpen = {
                                // Goes through whatever is currently the default browser handler —
                                // if that's still Linker, this deliberately re-opens the chooser
                                // rather than a fixed browser, consistent with tapping any link.
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            onEdit = { editTarget = link },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(link.url))
                                // Android 13+ (API 33) already shows its own system "Copied"
                                // confirmation for clipboard writes — an app-level toast on top of
                                // that would just be a second, redundant confirmation.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSend = { viewModel.sendToNotesnook(link) },
                            onDelete = { viewModel.delete(link) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    editTarget?.let { link ->
        EditSavedLinkDialog(
            link = link,
            onDismiss = { editTarget = null },
            onSave = { newUrl ->
                viewModel.edit(link, newUrl)
                editTarget = null
            }
        )
    }
}

@Composable
private fun SavedLinkRow(
    link: SavedLinkEntity,
    searchQuery: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // No line cap — a long URL wraps in full rather than being truncated, at a smaller
        // style than the app's usual body text so it stays reasonably compact while wrapping.
        // Colored as `primary` (the app's link-accent color) so it reads as a link rather than
        // plain body text, distinct from the muted timestamp below and the action icons.
        Text(
            text = highlightedUrlText(link.url, searchQuery),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = formatSavedTime(link.savedAtMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Actions on their own row below the URL rather than squeezed alongside it — the wide
        // URL column was crowding five icons into a thin trailing strip. Kept right-aligned so
        // this row still visually reads as "belonging to" the link text above it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Each action tinted by role rather than all defaulting to the same content color: edit,
            // copy, and send are neutral, open matches the link's own accent color (it's what acts on
            // that link), delete uses the error tone as the one destructive action here — same
            // convention as the rename dialog's Save/Cancel/Reset buttons in ManageBrowsersScreen.
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy link", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to Notesnook", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpen) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "Open", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Highlights every case-insensitive occurrence of [query] in [url] with a fixed highlighter-yellow
 * background (Nord's nord13) and dark (nord0) text — deliberately not theme-relative colors, since
 * a search highlight is meant to pop the same way regardless of dark/light mode or the link text's
 * own (primary-tinted) color.
 */
private fun highlightedUrlText(url: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(url)
    return buildAnnotatedString {
        var index = 0
        while (index < url.length) {
            val matchIndex = url.indexOf(query, index, ignoreCase = true)
            if (matchIndex < 0) {
                append(url.substring(index))
                break
            }
            append(url.substring(index, matchIndex))
            withStyle(SpanStyle(background = nord13, color = nord0, fontWeight = FontWeight.Bold)) {
                append(url.substring(matchIndex, matchIndex + query.length))
            }
            index = matchIndex + query.length
        }
    }
}

@Composable
private fun EditSavedLinkDialog(
    link: SavedLinkEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(link.id) { mutableStateOf(link.url) }

    fun submitEdit() {
        if (text.isNotBlank()) onSave(text)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit link") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text("Link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitEdit() })
            )
        },
        confirmButton = {
            TextButton(onClick = { submitEdit() }) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

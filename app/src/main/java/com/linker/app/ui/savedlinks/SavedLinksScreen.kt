package com.linker.app.ui.savedlinks

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            initializer { SavedLinksViewModel(container.savedLinksRepository) }
        }
    )
    val links by viewModel.links.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var editTarget by remember { mutableStateOf<SavedLinkEntity?>(null) }

    // Grouping happens after filtering so search keeps day headers. Links arrive newest-first,
    // so the groups come out newest-first too without extra sorting.
    val groupedByDay = remember(links) { links.groupBy { dayKey(it.savedAtMillis) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.searchText,
            onValueChange = viewModel::onSearchChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search saved links") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (viewModel.searchText.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchChanged("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (links.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (viewModel.searchText.isEmpty())
                        "No saved links yet. Use the bookmark icon in the chooser to save one."
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
                                // Opens with whatever is currently the default handler — if that's still Linker,
                                // this re-opens the chooser, consistent with tapping any link.
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            onEdit = { editTarget = link },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(link.url))
                                // Android 13+ shows its own "Copied" confirmation for clipboard writes.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, link.url)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, null))
                            },
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
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onOpen() }
    ) {
        // Long URLs wrap in full (no truncation), tinted as a link so they read as clickable,
        // distinct from the muted timestamp and action icons.
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
        // Actions on their own row below the URL, right-aligned so they read as belonging to it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tinted by role: edit/copy/share are neutral, delete uses the error color
            // as the one destructive action.
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy link", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share link", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Highlights every case-insensitive match of [query] in [url] with a fixed highlighter
 * style (Nord nord13 background, nord0 text) so it pops the same in dark or light mode.
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
                        text = "Edit link",
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
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = { Text("Link") },
                    modifier = Modifier.fillMaxWidth().imePadding(),
                    minLines = 5,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { onSave(text) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

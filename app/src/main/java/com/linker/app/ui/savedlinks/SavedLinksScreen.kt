package com.linker.app.ui.savedlinks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linker.app.data.db.entity.SavedLinkEntity
import com.linker.app.ui.rememberAppContainer
import com.linker.app.util.formatSavedAt

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

    if (links.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No saved links yet — use the bookmark icon in the link chooser to save one.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(links, key = { it.id }) { link ->
            SavedLinkRow(
                link = link,
                onOpen = {
                    // Goes through whatever is currently the default browser handler — if that's
                    // still Linker, this deliberately re-opens the chooser rather than a fixed
                    // browser, consistent with what tapping any other link does.
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onDelete = { viewModel.delete(link) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SavedLinkRow(link: SavedLinkEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = link.url,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSavedAt(link.savedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

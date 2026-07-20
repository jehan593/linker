package com.linker.app.interceptor

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linker.app.R
import com.linker.app.ui.components.AppIcon

@Composable
fun LinkChooserScreen(
    viewModel: LinkChooserViewModel,
    onOpenInBrowser: (packageName: String, url: String) -> Unit,
    onManageBrowsers: () -> Unit,
    onDismiss: () -> Unit
) {
    val host = remember(viewModel.editableUrl) {
        runCatching { Uri.parse(viewModel.editableUrl).host }.getOrNull()
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* absorb clicks so the scrim below doesn't dismiss */ },
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_public), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = host ?: "Open link",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.saveLink() }) {
                        Icon(
                            painter = painterResource(if (viewModel.isAlreadySaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                            contentDescription = if (viewModel.isAlreadySaved) "Already saved — tap to refresh timestamp" else "Save link",
                            // Tinted like the link accent (same primary used for the URL text and
                            // the Open action elsewhere) only once it's actually saved, so the
                            // filled/outline shape isn't the only cue that a tap now just bumps the
                            // timestamp instead of creating a new saved entry.
                            tint = if (viewModel.isAlreadySaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(viewModel.editableUrl))
                        // Android 13+ (API 33) already shows its own system "Copied" confirmation
                        // for clipboard writes — an app-level toast on top of that would just be a
                        // second, redundant confirmation for the same action.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy link")
                    }
                    IconButton(onClick = { viewModel.sendToNotesnook() }, enabled = !viewModel.isSending) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to Notesnook")
                    }
                }

                // Multi-line, no maxLines cap, and a smaller text style than the rest of the
                // card's content — long URLs need to be fully readable rather than truncated or
                // scrolled horizontally.
                OutlinedTextField(
                    value = viewModel.editableUrl,
                    onValueChange = viewModel::onUrlEdited,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    label = { Text("Link") },
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Open with",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    viewModel.loadingBrowsers -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    viewModel.browsers.isEmpty() -> {
                        Text(
                            text = "No browsers to show — everything might be hidden. Check Manage Browsers.",
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(viewModel.browsers, key = { it.packageName }) { browser ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenInBrowser(browser.packageName, viewModel.editableUrl) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(browser.icon, size = 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(browser.displayLabel, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onManageBrowsers) { Text("Manage browsers") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

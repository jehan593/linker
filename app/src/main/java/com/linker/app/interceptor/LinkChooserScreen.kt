package com.linker.app.interceptor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linker.app.R
import com.linker.app.ui.components.AppIcon
import kotlin.math.roundToInt

@Composable
fun LinkChooserScreen(
    viewModel: LinkChooserViewModel,
    onOpenInBrowser: (packageName: String, url: String) -> Unit,
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

// Always 4 lines tall; longer URLs scroll inside the field so a monster link
                // can't push the browser list and footer buttons off-screen.
                OutlinedTextField(
                    value = viewModel.editableUrl,
                    onValueChange = viewModel::onUrlEdited,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    label = { Text("Link") },
                    minLines = 4,
                    maxLines = 4,
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
                            text = "No browsers to show. They might all be hidden in Manage Browsers.",
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        Box(Modifier.weight(1f, fill = false)) {
                            LazyColumn(
                                state = listState,
                                // Shows up to 5 rows; more than that scrolls, with a thin scrollbar on the right edge.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                            ) {
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
                            BrowserListScrollbar(listState)
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.saveLink() }) {
                        Icon(
                            painter = painterResource(if (viewModel.isAlreadySaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                            contentDescription = if (viewModel.isAlreadySaved) "Already saved — tap to re-save" else "Save link",
                            // Filled and tinted primary once saved, so the icon alone reads "already saved — tap to re-save".
                            tint = if (viewModel.isAlreadySaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(viewModel.editableUrl))
                        // Android 13+ shows its own "Copied" confirmation for clipboard writes.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy link")
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, viewModel.editableUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share link")
                    }
                    IconButton(onClick = { viewModel.sendToNotesnook() }, enabled = !viewModel.isSending) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to Notesnook")
                    }
                }
            }
        }
    }
}

// Small custom scrollbar: Jetpack Compose on Android has no built-in scrollbar, and the rows
// are uniform in height, so the thumb is sized from the first visible row. Hidden until the
// list overflows (more than ~5 browsers), so the right edge stays clean on small lists.
@Composable
private fun BoxScope.BrowserListScrollbar(state: LazyListState) {
    val layoutInfo = state.layoutInfo
    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return
    if (layoutInfo.totalItemsCount < 2) return

    val viewportPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val itemHeightPx = firstVisible.size.coerceAtLeast(1)
    val totalContentPx = itemHeightPx * layoutInfo.totalItemsCount
    val maxScrollPx = totalContentPx - viewportPx
    if (maxScrollPx <= 0) return

    val scrollPx = (firstVisible.index * itemHeightPx) - firstVisible.offset
    val fraction = (scrollPx.toFloat() / maxScrollPx).coerceIn(0f, 1f)
    val minThumbPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val thumbPx = (viewportPx.toFloat() * viewportPx / totalContentPx).roundToInt().coerceAtLeast(minThumbPx)
    val thumbOffsetY = with(LocalDensity.current) {
        (fraction * (viewportPx - thumbPx)).roundToInt().toDp()
    }

    // matchParentSize fits the track to the list area without adding any height, so the
    // scrollbar never stretches the popup or leaves a gap below the list.
    Box(
        modifier = Modifier.matchParentSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { thumbPx.toDp() })
                    .offset(y = thumbOffsetY)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            )
        }
    }
}

package com.linker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Shared across the app (opened from MainActivity's top bar) since both the link chooser and the
 * saved links list send through the same account — there's only ever one API key/tag pair to
 * configure, not one per screen.
 */
@Composable
fun NotesnookSettingsDialog(
    initialApiKey: String,
    initialTagId: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, tagId: String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var tagId by remember { mutableStateOf(initialTagId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notesnook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "From Notesnook: Settings > Inbox > Create Key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Inbox API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagId,
                    onValueChange = { tagId = it },
                    label = { Text("Tag ID (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Right-click a tag in Notesnook and choose Copy ID. Sent links are titled " +
                        "\"Link: <url>\", with the send time and link in the note body.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey, tagId) }) {
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

package com.linker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Full-page Notesnook settings, opened from MainActivity's top bar gear icon. One shared API
 * key/tag pair is used by both the link chooser and the saved links list, so it's stored here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesnookSettingsScreen(
    initialApiKey: String,
    initialTagId: String,
    onBack: () -> Unit,
    onSave: (apiKey: String, tagId: String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var tagId by remember { mutableStateOf(initialTagId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notesnook") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                "Optional. Right-click a tag in Notesnook and choose Copy ID, then paste it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onSave(apiKey.trim(), tagId.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

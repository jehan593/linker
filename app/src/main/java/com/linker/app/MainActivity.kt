package com.linker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.linker.app.data.repository.NotesnookSettings
import com.linker.app.ui.browsers.ManageBrowsersScreen
import com.linker.app.ui.home.DefaultBrowserBanner
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.savedlinks.SavedLinksScreen
import com.linker.app.ui.settings.NotesnookSettingsDialog
import com.linker.app.ui.theme.LinkerTheme
import com.linker.app.util.DefaultBrowserRole
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Result is ignored directly — the ON_RESUME check below re-reads the role state either way,
    // which also correctly covers the user backing out without choosing anything.
    private val requestDefaultBrowser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LinkerTheme {
                var isDefaultBrowser by remember { mutableStateOf(DefaultBrowserRole.isHeld(this)) }
                var selectedTab by remember { mutableIntStateOf(0) }
                var isNotesnookSettingsOpen by remember { mutableStateOf(false) }
                val container = rememberAppContainer()
                val notesnookSettings by container.notesnookRepository.settingsFlow
                    .collectAsState(initial = NotesnookSettings(apiKey = null, tagId = null))
                val coroutineScope = rememberCoroutineScope()

                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isDefaultBrowser = DefaultBrowserRole.isHeld(this@MainActivity)
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Linker") },
                                actions = {
                                    IconButton(onClick = { isNotesnookSettingsOpen = true }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Notesnook settings")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                        ) {
                            if (!isDefaultBrowser) {
                                DefaultBrowserBanner(
                                    onRequestDefault = {
                                        requestDefaultBrowser.launch(DefaultBrowserRole.requestIntent(this@MainActivity))
                                    }
                                )
                            }
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Browsers") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("Saved Links") }
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                when (selectedTab) {
                                    0 -> ManageBrowsersScreen()
                                    else -> SavedLinksScreen()
                                }
                            }
                        }
                    }
                }

                if (isNotesnookSettingsOpen) {
                    NotesnookSettingsDialog(
                        initialApiKey = notesnookSettings.apiKey.orEmpty(),
                        initialTagId = notesnookSettings.tagId.orEmpty(),
                        onDismiss = { isNotesnookSettingsOpen = false },
                        onSave = { apiKey, tagId ->
                            coroutineScope.launch {
                                container.notesnookRepository.saveSettings(apiKey.trim(), tagId.trim())
                                isNotesnookSettingsOpen = false
                            }
                        }
                    )
                }
            }
        }
    }
}

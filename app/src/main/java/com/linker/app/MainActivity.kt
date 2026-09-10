package com.linker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.linker.app.ui.browsers.ManageBrowsersScreen
import com.linker.app.ui.home.DefaultBrowserBanner
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.savedlinks.SavedLinksScreen
import com.linker.app.ui.theme.LinkerTheme
import com.linker.app.util.DefaultBrowserRole

class MainActivity : ComponentActivity() {

    // Result is ignored; on resume the role state is re-read below.
    private val requestDefaultBrowser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LinkerTheme {
                var isDefaultBrowser by remember { mutableStateOf(DefaultBrowserRole.isHeld(this)) }
                var selectedTab by remember { mutableIntStateOf(0) }
                val container = rememberAppContainer()

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
                                title = { Text("Linker") }
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
            }
        }
    }
}

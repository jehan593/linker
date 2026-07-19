package com.linker.app.interceptor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linker.app.MainActivity
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.theme.LinkerTheme

/**
 * This is what actually runs when Linker is the default browser and a link is tapped anywhere
 * on the device: it shows our own chooser instead of opening a real browser directly, so hide /
 * reorder / rename / edit / save can all happen in the moment before a browser is picked. Every
 * exit path (pick a browser, cancel, jump to Manage Browsers) finishes this activity — it's a
 * transient overlay, not a screen the user navigates back to.
 */
class LinkInterceptorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.dataString
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            LinkerTheme {
                val container = rememberAppContainer()
                val viewModel: LinkChooserViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            LinkChooserViewModel(url, container.browserPrefsRepository, container.savedLinksRepository)
                        }
                    }
                )

                LinkChooserScreen(
                    viewModel = viewModel,
                    onOpenInBrowser = { packageName, finalUrl -> openInBrowser(packageName, finalUrl) },
                    onManageBrowsers = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun openInBrowser(packageName: String, url: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage(packageName) }
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Couldn't open that link", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

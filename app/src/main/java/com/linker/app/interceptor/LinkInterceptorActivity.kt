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
import com.linker.app.ui.rememberAppContainer
import com.linker.app.ui.theme.LinkerTheme

/**
 * Shows the chooser when a link is tapped and Linker is the default browser.
 * Every exit (pick a browser, close) finishes this activity.
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
                            LinkChooserViewModel(
                                url,
                                container.browserPrefsRepository,
                                container.savedLinksRepository
                            )
                        }
                    }
                )

                LinkChooserScreen(
                    viewModel = viewModel,
                    onOpenInBrowser = { packageName, finalUrl -> openInBrowser(packageName, finalUrl) },
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

package com.linker.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sends a saved link to a Notesnook account via its Inbox API
 * (https://help.notesnook.com/inbox-api/getting-started) — same fixed endpoint and request shape
 * as the noter sibling app's NotesnookApi, adapted to POST a link instead of free-text note body.
 * The inbox key is per-account, created from Notesnook's own Settings > Inbox screen; this app
 * only ever POSTs to it.
 */
object NotesnookApi {
    private const val INBOX_URL = "https://inbox.notesnook.com/"

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun sentAt(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    suspend fun sendLink(apiKey: String, url: String, tagId: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            val escapedUrl = escapeHtml(url)
            val body = JSONObject().apply {
                put("title", "Link: $url")
                put("type", "note")
                put("source", "linker-android")
                put("version", 1)
                put("content", JSONObject().apply {
                    put("type", "html")
                    put("data", "<p>${sentAt()} - <a href=\"$escapedUrl\">$escapedUrl</a></p>")
                })
                if (!tagId.isNullOrBlank()) put("tagIds", JSONArray().put(tagId))
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(INBOX_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", apiKey)
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                when (val status = connection.responseCode) {
                    in 200..299 -> Result.success(Unit)
                    401, 403 -> Result.failure(IOException("Notesnook rejected the API key. Check it in settings."))
                    else -> Result.failure(IOException("Notesnook returned $status."))
                }
            } catch (e: IOException) {
                Result.failure(IOException("Could not reach Notesnook's inbox service.", e))
            } finally {
                connection?.disconnect()
            }
        }
}

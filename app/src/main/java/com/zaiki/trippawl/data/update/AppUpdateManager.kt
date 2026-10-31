package com.zaiki.trippawl.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.zaiki.trippawl.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metadata representing a GitHub release update check result.
 */
data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val releasePageUrl: String
)

/**
 * Checks GitHub Releases (`https://github.com/zaiki505/TripPawl`) for newer app versions,
 * parses changelogs and APK assets, and manages direct download/install actions.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val GITHUB_OWNER = "zaiki505"
        const val GITHUB_REPO = "TripPawl"
        const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val REPO_HTML_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    }

    /**
     * Checks the GitHub API for the latest release tag and compares it to [BuildConfig.VERSION_NAME].
     */
    suspend fun checkForUpdates(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(LATEST_RELEASE_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "TripPawl-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Repository might not have a public release yet
                return@runCatching AppUpdateInfo(
                    isUpdateAvailable = false,
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = BuildConfig.VERSION_NAME,
                    releaseTitle = "No releases found",
                    releaseNotes = "No published releases on GitHub repository yet.",
                    apkDownloadUrl = null,
                    releasePageUrl = REPO_HTML_URL
                )
            } else if (responseCode !in 200..299) {
                val errorStream = connection.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP $responseCode"
                throw IllegalStateException("GitHub API error ($responseCode): $errorMsg")
            }

            val jsonString = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(jsonString)

            val rawTag = json.optString("tag_name", "").trim()
            val cleanLatestVersion = rawTag.removePrefix("v").removePrefix("V")
            val releaseTitle = json.optString("name", "v$cleanLatestVersion").ifBlank { "v$cleanLatestVersion" }
            val releaseNotes = json.optString("body", "").trim()
            val releaseHtmlUrl = json.optString("html_url", REPO_HTML_URL)

            var apkUrl: String? = null
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = isVersionNewer(cleanLatestVersion, currentVersion)

            AppUpdateInfo(
                isUpdateAvailable = isNewer,
                currentVersion = currentVersion,
                latestVersion = cleanLatestVersion.ifBlank { currentVersion },
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkUrl,
                releasePageUrl = releaseHtmlUrl
            )
        }
    }

    /**
     * Opens the direct APK download link or GitHub release page in the system browser.
     */
    fun openReleaseOrDownloadInBrowser(targetUrl: String) {
        val uri = Uri.parse(targetUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Enqueues an APK download via Android's [DownloadManager] with a visible system notification.
     */
    fun startDownloadViaManager(apkUrl: String, versionTag: String): Long {
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("TripPawl v$versionTag")
            setDescription("Downloading latest TripPawl update APK from GitHub...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "TripPawl-v$versionTag.apk"
            )
            setMimeType("application/vnd.android.package-archive")
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    /**
     * Compares semantic versions like "1.1.0" vs "1.0.0".
     */
    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val latestParts = latest.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

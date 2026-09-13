package io.music_assistant.client.updater

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.music_assistant.client.utils.createPlatformHttpClient
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long? = null,
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: String,
        val releaseName: String?,
        val changelog: String?,
        val downloadUrl: String,
        val htmlUrl: String,
    ) : UpdateCheckResult()

    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
    data object Idle : UpdateCheckResult()
    data object Checking : UpdateCheckResult()
}

class GitHubUpdateChecker(
    private val client: HttpClient = createPlatformHttpClient(),
) {
    suspend fun checkForUpdates(
        repo: String,
        currentVersion: String = CURRENT_APP_VERSION,
    ): UpdateCheckResult {
        val trimmed = repo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')

        if (!trimmed.contains('/') || trimmed.split('/').size != 2) {
            return UpdateCheckResult.Error("Enter a valid GitHub repo in owner/repo format (e.g. username/mobile-app)")
        }

        return try {
            val response = client.get("https://api.github.com/repos/$trimmed/releases/latest") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "MusicAssistantOwnedApp")
            }

            if (response.status.value == 404) {
                return UpdateCheckResult.Error("No releases found on GitHub repo $trimmed yet.")
            }
            if (response.status.value !in 200..299) {
                return UpdateCheckResult.Error("GitHub returned status ${response.status.value}")
            }

            val text = response.bodyAsText()
            val release = myJson.decodeFromString<GitHubRelease>(text)
            val latestTag = release.tagName.removePrefix("v").removePrefix("android-v").trim()
            val currentClean = currentVersion.removePrefix("v").removePrefix("android-v").trim()

            if (isNewerVersion(latestTag, currentClean)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVersion,
                    newVersion = release.tagName,
                    releaseName = release.name,
                    changelog = release.body,
                    downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
                    htmlUrl = release.htmlUrl,
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to connect to GitHub")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val lParts = latest.split('.', '-').mapNotNull { it.toIntOrNull() }
        val cParts = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(lParts.size, cParts.size)) {
            if (lParts[i] > cParts[i]) return true
            if (lParts[i] < cParts[i]) return false
        }
        return latest != current && !current.contains(latest)
    }

    companion object {
        const val CURRENT_APP_VERSION = "0.14.0-owned"
    }
}

package com.ivor.ivormusic.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Repository for checking app updates from GitHub Releases.
 * Uses GitHub API to fetch the latest release and compare version tags.
 */
class UpdateRepository {
    
    companion object {
        private const val TAG = "UpdateRepository"
        private const val GITHUB_API_BASE = "https://api.github.com/repos"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Check if an update is available.
     * @param repoPath The GitHub repo path (e.g., "ivorisnoob/TheMusicApp")
     * @param currentVersion The current app version (e.g., "1.4")
     * @return UpdateResult with update info or current status
     */
    suspend fun checkForUpdate(
        repoPath: String,
        currentVersion: String
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = "$GITHUB_API_BASE/$repoPath/releases/latest"
            
            Log.d(TAG, "Checking for updates at: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "IvorMusic")
                .build()
            
            val response = client.newCall(request).execute()
            
            when (response.code) {
                200 -> {
                    val json = response.body?.string() ?: return@withContext UpdateResult.Error("Empty response")
                    val jsonObject = JSONObject(json)
                    
                    val tagName = jsonObject.optString("tag_name", "")
                    val releaseName = jsonObject.optString("name", tagName)
                    val releaseNotes = jsonObject.optString("body", "")
                    val htmlUrl = jsonObject.optString("html_url", "")
                    val publishedAt = jsonObject.optString("published_at", "")
                    
                    // Extract download URL for APK if available
                    var apkDownloadUrl: String? = null
                    val assets = jsonObject.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                apkDownloadUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    
                    // Clean version strings for comparison (remove 'v' prefix if present)
                    val latestVersion = tagName.removePrefix("v").removePrefix("V")
                    val cleanCurrentVersion = currentVersion.removePrefix("v").removePrefix("V")
                    
                    Log.d(TAG, "Latest version: $latestVersion, Current: $cleanCurrentVersion")
                    
                    val isUpdateAvailable = isNewerVersion(latestVersion, cleanCurrentVersion)
                    
                    if (isUpdateAvailable) {
                        UpdateResult.UpdateAvailable(
                            latestVersion = latestVersion,
                            releaseName = releaseName,
                            releaseNotes = releaseNotes,
                            htmlUrl = htmlUrl,
                            apkDownloadUrl = apkDownloadUrl,
                            publishedAt = publishedAt
                        )
                    } else {
                        UpdateResult.UpToDate(currentVersion = cleanCurrentVersion)
                    }
                }
                404 -> {
                    Log.w(TAG, "No releases found for $repoPath")
                    UpdateResult.NoReleases
                }
                else -> {
                    Log.e(TAG, "API error ${response.code}: ${response.message}")
                    UpdateResult.Error("GitHub API error (${response.code})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Compare version strings to determine if latest is newer than current.
     * Handles formats like "1.4", "1.4.1", "2.0"
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            // Pad with zeros to same length
            val maxLength = maxOf(latestParts.size, currentParts.size)
            val paddedLatest = latestParts + List(maxLength - latestParts.size) { 0 }
            val paddedCurrent = currentParts + List(maxLength - currentParts.size) { 0 }
            
            // Compare each part
            for (i in 0 until maxLength) {
                if (paddedLatest[i] > paddedCurrent[i]) return true
                if (paddedLatest[i] < paddedCurrent[i]) return false
            }
            
            return false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison failed", e)
            return false
        }
    }
}

/**
 * Result of an update check.
 */
sealed class UpdateResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val apkDownloadUrl: String?,
        val publishedAt: String
    ) : UpdateResult()
    
    data class UpToDate(val currentVersion: String) : UpdateResult()
    
    object NoReleases : UpdateResult()
    
    data class Error(val message: String) : UpdateResult()
    
    object Checking : UpdateResult()
}

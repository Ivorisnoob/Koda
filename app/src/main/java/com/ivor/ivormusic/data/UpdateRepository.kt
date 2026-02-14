package com.ivor.ivormusic.data

import android.os.Build
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
 * Supports ABI-aware APK matching for split builds.
 */
class UpdateRepository {
    
    private val TAG = "UpdateRepository"
    private val GITHUB_API_BASE = "https://api.github.com/repos"
    
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
                    
                    // Parse ALL APK assets
                    val apkAssets = mutableListOf<ApkAsset>()
                    val assets = jsonObject.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                apkAssets.add(
                                    ApkAsset(
                                        name = name,
                                        downloadUrl = asset.optString("browser_download_url"),
                                        size = asset.optLong("size", 0L)
                                    )
                                )
                            }
                        }
                    }
                    
                    // Parse images from release body (markdown image syntax)
                    val releaseImages = parseImagesFromMarkdown(releaseNotes)
                    
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
                            apkAssets = apkAssets,
                            apkDownloadUrl = findBestApk(apkAssets)?.downloadUrl,
                            publishedAt = publishedAt,
                            releaseImages = releaseImages
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
    
    /**
     * Extract image URLs from markdown-formatted release notes
     */
    private fun parseImagesFromMarkdown(markdown: String): List<String> {
        val images = mutableListOf<String>()
        
        // 1. Match markdown image syntax: ![alt](url)
        val mdRegex = Regex("""!\[.*?]\((.*?)\)""")
        mdRegex.findAll(markdown).forEach { match ->
            match.groupValues.getOrNull(1)?.let { url ->
                if (url.startsWith("http") && url !in images) images.add(url)
            }
        }
        
        // 2. Match HTML image syntax: <img ... src="url" ... />
        val htmlRegex = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""")
        htmlRegex.findAll(markdown).forEach { match ->
            match.groupValues.getOrNull(1)?.let { url ->
                if (url.startsWith("http") && url !in images) images.add(url)
            }
        }
        
        // 3. Match raw image URLs (common extensions)
        val rawUrlRegex = Regex("""(https://(?:user-images\.githubusercontent\.com|github\.com)[^\s)\"]+\.(?:png|jpg|jpeg|gif|webp))""", RegexOption.IGNORE_CASE)
        rawUrlRegex.findAll(markdown).forEach { match ->
            val url = match.groupValues[0]
            if (url !in images) images.add(url)
        }
        
        // 4. Match GitHub user-attachments (no extension)
        val attachmentRegex = Regex("""https://github\.com/user-attachments/assets/[a-f0-9\-]+""")
        attachmentRegex.findAll(markdown).forEach { match ->
            val url = match.groupValues[0]
            if (url !in images) images.add(url)
        }
        
        return images
    }
    
    companion object DeviceInfo {
        /**
         * Get the device's primary ABI
         */
        fun getDeviceAbi(): String {
            return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        }
        
        /**
         * Find the best matching APK for this device
         */
        fun findBestApk(assets: List<ApkAsset>): ApkAsset? {
            val abi = getDeviceAbi()
            // First try exact ABI match
            val abiMatch = assets.find { asset ->
                asset.name.contains(abi, ignoreCase = true)
            }
            if (abiMatch != null) return abiMatch
            
            // Try simplified match (arm64 -> v8a, armeabi -> v7a)
            val simplified = when {
                abi.contains("arm64") || abi.contains("v8a") -> assets.find { 
                    it.name.contains("v8a", ignoreCase = true) || it.name.contains("arm64", ignoreCase = true)
                }
                abi.contains("armeabi") || abi.contains("v7a") -> assets.find {
                    it.name.contains("v7a", ignoreCase = true) || it.name.contains("armeabi", ignoreCase = true)
                }
                else -> null
            }
            if (simplified != null) return simplified
            
            // Fallback: universal APK or first available
            return assets.find { it.name.contains("universal", ignoreCase = true) }
                ?: assets.firstOrNull()
        }
    }
}

/**
 * Represents a downloadable APK asset from a GitHub release.
 */
data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

/**
 * Result of an update check.
 */
sealed class UpdateResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val apkAssets: List<ApkAsset> = emptyList(),
        val apkDownloadUrl: String?,
        val publishedAt: String,
        val releaseImages: List<String> = emptyList()
    ) : UpdateResult()
    
    data class UpToDate(val currentVersion: String) : UpdateResult()
    
    object NoReleases : UpdateResult()
    
    data class Error(val message: String) : UpdateResult()
    
    object Checking : UpdateResult()
}

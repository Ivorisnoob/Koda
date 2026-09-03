package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val tag = "UpdateRepository"
    
    /**
     * Check if an update is available.
     * @param repoPath The GitHub repo path (e.g., "ivorisnoob/TheMusicApp")
     * @param currentVersion The current app version (e.g., "1.4")
     * @return UpdateResult with update info or current status
     */
    suspend fun checkForUpdate(
        repoPath: String,
        currentVersion: String,
        forceRefresh: Boolean = false,
    ): UpdateResult = withContext(Dispatchers.IO) {
        val cacheKey = "$repoPath|$currentVersion"
        if (!forceRefresh) {
            cachedCheck?.takeIf {
                it.key == cacheKey && System.currentTimeMillis() - it.checkedAtMs < CHECK_TTL_MS
            }?.let { return@withContext it.result }
        }

        checkMutex.withLock {
            if (!forceRefresh) {
                cachedCheck?.takeIf {
                    it.key == cacheKey && System.currentTimeMillis() - it.checkedAtMs < CHECK_TTL_MS
                }?.let { return@withLock it.result }
            }

            val result = fetchLatestRelease(repoPath, currentVersion)
            if (result !is UpdateResult.Error) {
                cachedCheck = CachedCheck(cacheKey, result, System.currentTimeMillis())
            }
            result
        }
    }

    private fun fetchLatestRelease(repoPath: String, currentVersion: String): UpdateResult {
        try {
            val url = "$GITHUB_API_BASE/$repoPath/releases/latest"

            KLog.d(tag, "Checking for updates at: $url")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Koda-Android")
                .build()

            return client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val json = response.body?.string()
                            ?: return@use UpdateResult.Error("GitHub returned an empty response")
                        val jsonObject = JSONObject(json)

                        val tagName = jsonObject.optString("tag_name", "")
                        val releaseName = jsonObject.optString("name", tagName)
                        val releaseNotes = jsonObject.optString("body", "")
                        val htmlUrl = jsonObject.optString("html_url", "")
                        val publishedAt = jsonObject.optString("published_at", "")

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
                        val latestVersion = tagName.removePrefix("v").removePrefix("V")
                        val cleanCurrentVersion = currentVersion.removePrefix("v").removePrefix("V")

                        KLog.d(tag, "Latest version: $latestVersion, Current: $cleanCurrentVersion")

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
                            )
                        } else {
                            UpdateResult.UpToDate(
                                currentVersion = cleanCurrentVersion,
                                latestVersion = latestVersion,
                                releaseName = releaseName,
                                releaseNotes = releaseNotes,
                                htmlUrl = htmlUrl,
                                publishedAt = publishedAt,
                            )
                        }
                    }
                    404 -> {
                        KLog.w(tag, "No releases found for $repoPath")
                        UpdateResult.NoReleases
                    }
                    else -> {
                        KLog.e(tag, "API error ${response.code}: ${response.message}")
                        UpdateResult.Error("GitHub API error (${response.code})")
                    }
                }
            }
        } catch (e: Exception) {
            KLog.e(tag, "Error checking for updates", e)
            return UpdateResult.Error(e.message ?: "Could not reach GitHub")
        }
    }
    
    /**
     * Compare version strings to determine if latest is newer than current.
     * Handles formats like "1.4", "1.4.1", "2.0"
     */
    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = Regex("\\d+").findAll(latest).map { it.value.toInt() }.toList()
        val currentParts = Regex("\\d+").findAll(current).map { it.value.toInt() }.toList()
        if (latestParts.isEmpty() || currentParts.isEmpty()) return false
        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxLength) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    companion object DeviceInfo {
        private const val GITHUB_API_BASE = "https://api.github.com/repos"
        private const val CHECK_TTL_MS = 10 * 60 * 1000L
        private val checkMutex = Mutex()
        private data class CachedCheck(
            val key: String,
            val result: UpdateResult,
            val checkedAtMs: Long,
        )
        @Volatile private var cachedCheck: CachedCheck? = null
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

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
            
            // Never return an APK for a different architecture. A universal
            // build is safe; an arbitrary first asset may simply not install.
            return assets.find { it.name.contains("universal", ignoreCase = true) }
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
    ) : UpdateResult()
    
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val releaseName: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val publishedAt: String,
    ) : UpdateResult()
    
    object NoReleases : UpdateResult()

    data class Error(val message: String) : UpdateResult()

    object Checking : UpdateResult()

    /**
     * Local Only mode is on, so no release check was made.
     *
     * The repository never returns this - it has no Context to read the
     * preference from. The update screen decides it before calling, which is
     * what keeps the check itself honest: Local Only means the app does not
     * reach the network, and a "small" metadata request is still a request.
     */
    object LocalOnly : UpdateResult()
}

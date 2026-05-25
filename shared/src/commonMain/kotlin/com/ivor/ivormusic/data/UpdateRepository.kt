package com.ivor.ivormusic.data

data class ApkAsset(val name: String, val downloadUrl: String, val size: Long)

sealed class UpdateResult {
    object Checking : UpdateResult()
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String = "",
        val releaseNotes: String = "",
        val htmlUrl: String = "",
        val apkAssets: List<ApkAsset> = emptyList()
    ) : UpdateResult()
    data class UpToDate(val currentVersion: String = "3.0.0") : UpdateResult()
    data class Error(val message: String) : UpdateResult()
    object NoReleases : UpdateResult()
}

class UpdateRepository {
    suspend fun checkForUpdate(repoPath: String = "ivorisnoob/koda", currentVersion: String = "3.0.0"): UpdateResult =
        UpdateResult.UpToDate(currentVersion)

    companion object {
        fun getDeviceAbi(): String = "universal"
        fun findBestApk(apkAssets: List<ApkAsset>): ApkAsset? = apkAssets.firstOrNull()
    }
}

package com.be.music.update

import com.be.music.platform.Platform
import com.be.music.platform.SimpleHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class UpdateResult {
    data object UpToDate : UpdateResult()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult()
    data class MandatoryUpdate(val info: UpdateInfo) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

@Serializable
data class UpdateInfo(
    val app_name: String,
    val package_name: String,
    val latest_version_name: String,
    val latest_version_code: Int,
    val min_supported_version_code: Int,
    val download_url: String,
    val changelog: String,
    val file_size: String,
    val release_date: String,
    val mandatory: Boolean,
    val sha256: String
)

class UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val VERSION_URL = "NULL"
    }

    suspend fun checkForUpdate(): UpdateResult {
        return try {
            val body = SimpleHttp.get(VERSION_URL, 10000)
                ?: return UpdateResult.Error("Empty response body")

            val info = json.decodeFromString<UpdateInfo>(body)

            if (info.latest_version_code > Platform.appVersionCode) {
                if (info.mandatory) {
                    return UpdateResult.MandatoryUpdate(info)
                }
                return UpdateResult.UpdateAvailable(info)
            }
            UpdateResult.UpToDate
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }
}

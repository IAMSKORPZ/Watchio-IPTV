package com.iamskorpz.watchioiptv.data.updates

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

class UpdateRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val manifestUrl: String,
    private val expectedChannel: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val UITEST_MANIFEST_URL = "watchio://uitest/update.json"
    }

    fun installedVersion(): InstalledVersion {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        return InstalledVersion(code, packageInfo.versionName ?: "unknown")
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installed = installedVersion()
        if (manifestUrl.isBlank()) throw UpdateException("Update checking is disabled for this build.")
        val body = httpGet(manifestUrl)
        val manifest = parseManifest(body)
        UpdatePolicy.validateManifest(manifest, expectedChannel)
        val status = UpdatePolicy.compare(manifest.versionCode, installed.versionCode)
        UpdateCheckResult(installed, manifest, status)
    }

    suspend fun downloadAndVerify(
        manifest: UpdateManifest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): VerifiedUpdateFile = withContext(Dispatchers.IO) {
        UpdatePolicy.validateManifest(manifest, expectedChannel)
        val updatesDir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val target = File(updatesDir, UpdatePolicy.sanitizeFileName(manifest.apk.fileName))
        if (UpdateArtifactValidator.validateCached(
                file = target,
                expectedSha256 = manifest.apk.sha256,
                expectedPackageName = context.packageName,
                packageNameReader = ::archivePackageName,
            )
        ) {
            return@withContext VerifiedUpdateFile(manifest, target.absolutePath)
        }
        val partial = File(updatesDir, "${target.name}.part")
        if (partial.exists()) partial.delete()
        if (target.exists()) target.delete()

        val request = Request.Builder().url(manifest.apk.downloadUrl).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("Unable to download update.")
            val body = response.body ?: throw UpdateException("Update download was empty.")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }

        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        UpdateArtifactValidator.validateDownloaded(
            file = target,
            expectedSha256 = manifest.apk.sha256,
            expectedPackageName = context.packageName,
            packageNameReader = ::archivePackageName,
        )
        VerifiedUpdateFile(manifest, target.absolutePath)
    }

    private fun parseManifest(body: String): UpdateManifest {
        return try {
            json.decodeFromString(UpdateManifest.serializer(), body)
        } catch (_: SerializationException) {
            throw UpdateException("Provider returned invalid update information.")
        } catch (_: IllegalArgumentException) {
            throw UpdateException("Provider returned invalid update information.")
        }
    }

    private fun httpGet(url: String): String {
        if (url == UITEST_MANIFEST_URL) return uitestManifest()
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("Unable to check for updates.")
            return response.body?.string()?.takeIf { it.isNotBlank() } ?: throw UpdateException("Update information was empty.")
        }
    }

    private fun uitestManifest(): String = """
        {
          "schemaVersion": 1,
          "channel": "uitest",
          "versionCode": ${installedVersion().versionCode},
          "versionName": "${installedVersion().versionName}",
          "minimumSupportedVersionCode": 1,
          "mandatory": false,
          "publishedAt": "2026-08-28T16:36:29+01:00",
          "releaseNotes": ["UITEST deterministic update manifest."],
          "githubRelease": "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.0-dev.1",
          "apk": {
            "fileName": "watchio-dev-0.1.0-dev.1-debug.apk",
            "downloadUrl": "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/download/v0.1.0-dev.1/watchio-dev-0.1.0-dev.1-debug.apk",
            "sha256": "7453742ce6d876bdf153bfc699938ffa9aef6ba0efa3ab96acfc9bbe46d33b6a"
          }
        }
    """.trimIndent()

    private fun archivePackageName(file: File): String? =
        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
}

class UpdateException(message: String) : Exception(message)

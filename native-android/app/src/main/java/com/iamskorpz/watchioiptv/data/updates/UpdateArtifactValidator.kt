package com.iamskorpz.watchioiptv.data.updates

import java.io.File
import java.security.MessageDigest

internal object UpdateArtifactValidator {
    fun validateCached(
        file: File,
        expectedSha256: String,
        expectedPackageName: String,
        packageNameReader: (File) -> String?,
    ): Boolean {
        if (!file.exists() || !sha256(file).equals(expectedSha256, ignoreCase = true)) return false
        validatePackage(file, expectedPackageName, packageNameReader)
        return true
    }

    fun validateDownloaded(
        file: File,
        expectedSha256: String,
        expectedPackageName: String,
        packageNameReader: (File) -> String?,
    ) {
        if (!sha256(file).equals(expectedSha256, ignoreCase = true)) {
            file.delete()
            throw UpdateException("Update verification failed.")
        }
        validatePackage(file, expectedPackageName, packageNameReader)
    }

    private fun validatePackage(
        file: File,
        expectedPackageName: String,
        packageNameReader: (File) -> String?,
    ) {
        if (packageNameReader(file) != expectedPackageName) {
            file.delete()
            throw UpdateException("Update package does not match Watchio.")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

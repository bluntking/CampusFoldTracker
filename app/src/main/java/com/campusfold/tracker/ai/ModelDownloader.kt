package com.campusfold.tracker.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ModelDownloader {

    private const val TAG = "ModelDownloader"

    /**
     * Downloads a model file from Hugging Face with resumable HTTP Range requests,
     * size verification, and pinned SHA-256 verification.
     * On failure, keeps the .tmp file intact so partial downloads are not lost
     * and shows real error messages.
     */
    suspend fun downloadModel(
        context: Context,
        model: AiModelInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val modelsDir = File(appContext.filesDir, "llm_models").apply { mkdirs() }
        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        // 1. Check if model already downloaded and valid
        if (targetFile.exists() && targetFile.length() > 0) {
            if (model.expectedSizeBytes <= 0 || targetFile.length() == model.expectedSizeBytes) {
                if (model.pinnedSha256.isBlank() || verifyFileSha256(targetFile, model.pinnedSha256)) {
                    Log.i(TAG, "Model already downloaded and verified: ${targetFile.absolutePath}")
                    AiDiagnostics.recordEvent(
                        type = "DOWNLOAD_OK",
                        message = "Model already present and verified: ${model.displayName}",
                        details = "Path: ${targetFile.absolutePath}, Size: ${targetFile.length()}"
                    )
                    return@withContext Result.success(targetFile)
                }
            }
        }

        // 2. Check available storage space
        val freeBytes = appContext.filesDir.usableSpace
        val requiredBytes = if (model.expectedSizeBytes > 0) model.expectedSizeBytes else 1_600_000_000L
        if (freeBytes < requiredBytes) {
            val freeMb = freeBytes / (1024 * 1024)
            val reqMb = requiredBytes / (1024 * 1024)
            val err = "Insufficient storage space: $freeMb MB free, but $reqMb MB is required."
            AiDiagnostics.recordEvent("DOWNLOAD_FAIL", "Disk space error for ${model.displayName}", err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        AiDiagnostics.recordEvent(
            type = "DOWNLOAD_START",
            message = "Starting download for ${model.displayName}",
            details = "URL: ${model.downloadUrl}"
        )

        try {
            var currentUrl = model.downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0

            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            while (true) {
                val urlObj = URL(currentUrl)
                connection = urlObj.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.setRequestProperty("User-Agent", "CampusFoldTracker-Android/1.0")

                // Resumable HTTP Range header if existing partial bytes present
                if (existingBytes > 0 && (model.expectedSizeBytes <= 0 || existingBytes < model.expectedSizeBytes)) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                    Log.i(TAG, "Requesting HTTP Range: bytes=$existingBytes- for ${model.displayName}")
                }

                connection.connect()

                val code = connection.responseCode
                if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLocation.isNullOrBlank() || redirectCount++ > 6) {
                        throw IllegalStateException("Too many redirects or missing redirect Location header")
                    }
                    currentUrl = newLocation
                } else if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                    break
                } else if (code == 401 || code == 403) {
                    connection.disconnect()
                    val err = "Access denied (HTTP $code). ${model.displayName} is gated on Hugging Face. Please use 'Import model file' to load weights manually."
                    throw IllegalStateException(err)
                } else {
                    val err = "Server returned HTTP $code: ${connection.responseMessage}"
                    connection.disconnect()
                    throw IllegalStateException(err)
                }
            }

            val responseCode = connection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val contentLength = connection.contentLengthLong

            val totalBytes = when {
                model.expectedSizeBytes > 0 -> model.expectedSizeBytes
                isPartial && contentLength > 0 -> existingBytes + contentLength
                contentLength > 0 -> contentLength
                else -> -1L
            }

            var downloadedBytes = if (isPartial) existingBytes else 0L
            val appendMode = isPartial && existingBytes > 0

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, appendMode).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 200) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 99)
                            } else 0
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            // Verification Stage: Keep .tmp file intact on any failure!
            val downloadedSize = tempFile.length()
            if (model.expectedSizeBytes > 0 && downloadedSize != model.expectedSizeBytes) {
                val err = "Download size mismatch for ${model.displayName}: expected ${model.expectedSizeBytes} bytes, but got $downloadedSize bytes. (Retaining .tmp file for resumption)"
                Log.e(TAG, err)
                AiDiagnostics.recordEvent("DOWNLOAD_FAIL", "Size mismatch for ${model.displayName}", err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            // SHA-256 verification
            if (model.pinnedSha256.isNotBlank()) {
                onProgress(99, downloadedBytes, totalBytes)
                Log.i(TAG, "Computing SHA-256 hash for downloaded ${model.displayName}...")
                val computedHash = computeSha256Hex(tempFile)
                if (!computedHash.equals(model.pinnedSha256, ignoreCase = true)) {
                    val err = "SHA-256 verification failed for ${model.displayName}!\nExpected: ${model.pinnedSha256}\nComputed: $computedHash\n(Retaining .tmp file for debugging)"
                    Log.e(TAG, err)
                    AiDiagnostics.recordEvent("DOWNLOAD_FAIL", "SHA-256 mismatch for ${model.displayName}", err)
                    return@withContext Result.failure(IllegalStateException(err))
                }
                Log.i(TAG, "SHA-256 verified successfully: $computedHash")
            }

            // Move tempFile to targetFile upon full verification
            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                val err = "Failed to finalize downloaded model file from .tmp to target destination."
                AiDiagnostics.recordEvent("DOWNLOAD_FAIL", "File rename error", err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            AiDiagnostics.recordEvent(
                type = "DOWNLOAD_OK",
                message = "Model downloaded and verified successfully: ${model.displayName}",
                details = "Path: ${targetFile.absolutePath}, Size: ${targetFile.length()} bytes"
            )

            onProgress(100, targetFile.length(), targetFile.length())
            Result.success(targetFile)

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown download error"
            Log.e(TAG, "Download failed for ${model.displayName}", e)
            AiDiagnostics.recordEvent(
                type = "DOWNLOAD_FAIL",
                message = "Download error for ${model.displayName}: $errorMsg",
                details = "Temp file kept at ${tempFile.absolutePath} (${if (tempFile.exists()) tempFile.length() else 0} bytes)"
            )
            Result.failure(e)
        }
    }

    fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyFileSha256(file: File, expectedSha256: String): Boolean {
        return try {
            computeSha256Hex(file).equals(expectedSha256.trim(), ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing file", e)
            false
        }
    }
}

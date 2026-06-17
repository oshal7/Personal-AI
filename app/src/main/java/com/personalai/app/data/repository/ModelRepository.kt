package com.personalai.app.data.repository

import android.content.Context
import com.personalai.app.domain.model.ModelInfo
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class DownloadProgress {
    data class InProgress(val bytesRead: Long, val totalBytes: Long) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
    data object Complete : DownloadProgress()
}

/**
 * Downloads and caches GGUF model files in app-private storage; never re-downloads a file
 * that's already present and valid. Mobile connections regularly drop multi-GB transfers
 * mid-stream, so a failed download keeps its `.part` file and resumes via an HTTP Range
 * request on the next attempt instead of restarting from zero.
 */
class ModelRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun localFile(model: ModelInfo): File = File(modelsDir, model.fileName)

    fun isDownloaded(model: ModelInfo): Boolean = localFile(model).let { it.exists() && it.length() > 0 }

    fun download(model: ModelInfo): Flow<DownloadProgress> = flow {
        val destination = localFile(model)
        val partial = File(modelsDir, "${model.fileName}.part")
        val alreadyOnDisk = if (partial.exists()) partial.length() else 0L

        try {
            val requestBuilder = Request.Builder().url(model.downloadUrl)
            if (alreadyOnDisk > 0) {
                requestBuilder.header("Range", "bytes=$alreadyOnDisk-")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 416) {
                    // Our partial file doesn't match what the server has anymore; clear it
                    // so the next retry starts a clean download instead of looping forever.
                    partial.delete()
                    emit(DownloadProgress.Failed("Resume point no longer valid — tap retry to restart the download"))
                    return@flow
                }

                val resuming = alreadyOnDisk > 0 && response.code == 206
                if (alreadyOnDisk > 0 && !resuming) {
                    // Server ignored our Range request and is sending the full file again.
                    partial.delete()
                }

                if (!response.isSuccessful) {
                    emit(DownloadProgress.Failed("HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(DownloadProgress.Failed("Empty response body"))
                    return@flow
                }

                val baseBytes = if (resuming) alreadyOnDisk else 0L
                val remainingBytes = body.contentLength().takeIf { it > 0 }
                    ?: (model.approxSizeBytes - baseBytes).coerceAtLeast(0)
                val totalBytes = baseBytes + remainingBytes

                body.byteStream().use { input ->
                    FileOutputStream(partial, resuming).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead = baseBytes
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            emit(DownloadProgress.InProgress(bytesRead, totalBytes))
                        }
                    }
                }
            }

            if (model.sha256.isNotBlank() && !verifyChecksum(partial, model.sha256)) {
                partial.delete()
                emit(DownloadProgress.Failed("Checksum mismatch"))
                return@flow
            }

            partial.renameTo(destination)
            emit(DownloadProgress.Complete)
        } catch (e: Exception) {
            // Keep the partial file on disk: a retry resumes from here instead of
            // re-downloading the whole model after a dropped mobile connection.
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    fun delete(model: ModelInfo) {
        localFile(model).delete()
        File(modelsDir, "${model.fileName}.part").delete()
    }

    private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }
}

package com.personalai.app.data.repository

import android.content.Context
import com.personalai.app.domain.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class DownloadProgress {
    data class InProgress(val bytesRead: Long, val totalBytes: Long) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
    data object Complete : DownloadProgress()
}

/** Downloads and caches GGUF model files in app-private storage; never re-downloads a file that's already present and valid. */
class ModelRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun localFile(model: ModelInfo): File = File(modelsDir, model.fileName)

    fun isDownloaded(model: ModelInfo): Boolean = localFile(model).let { it.exists() && it.length() > 0 }

    fun download(model: ModelInfo): Flow<DownloadProgress> = flow {
        val destination = localFile(model)
        val partial = File(modelsDir, "${model.fileName}.part")

        try {
            val request = Request.Builder().url(model.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadProgress.Failed("HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(DownloadProgress.Failed("Empty response body"))
                    return@flow
                }
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.approxSizeBytes

                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead = 0L
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
            partial.delete()
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    fun delete(model: ModelInfo) {
        localFile(model).delete()
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

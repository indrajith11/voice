package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader {
    companion object {
        private const val TAG = "ModelDownloader"
    }

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress

    suspend fun download(
        modelId: String,
        downloadUrl: String,
        targetFile: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var randomAccessFile: RandomAccessFile? = null
        var inputStream: java.io.InputStream? = null
        
        try {
            Log.d(TAG, "Starting download/resume of $modelId from $downloadUrl")
            val url = URL(downloadUrl)
            
            // Resume support check
            var existingLength = 0L
            if (targetFile.exists()) {
                existingLength = targetFile.length()
                if (existingLength >= expectedSize) {
                    Log.d(TAG, "File already matches or exceeds expected size. No download needed.")
                    onProgress(1.0f)
                    return@withContext true
                }
                Log.d(TAG, "Resuming download from byte offset: $existingLength")
            } else {
                targetFile.parentFile?.mkdirs()
                targetFile.createNewFile()
            }

            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            
            if (existingLength > 0) {
                connection.setRequestProperty("Range", "bytes=$existingLength-")
            }

            connection.connect()

            val responseCode = connection.responseCode
            // 206 Partial Content is returned when resuming, otherwise 200 OK
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                Log.e(TAG, "Failed to connect: HTTP $responseCode")
                return@withContext false
            }

            randomAccessFile = RandomAccessFile(targetFile, "rw")
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                randomAccessFile.seek(existingLength)
            } else {
                randomAccessFile.setLength(0) // Restart
                existingLength = 0L
            }

            inputStream = connection.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalDownloaded = existingLength

            while (true) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                
                randomAccessFile.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead
                
                val progressValue = if (expectedSize > 0) totalDownloaded.toFloat() / expectedSize.toFloat() else 0.5f
                onProgress(progressValue)
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    put(modelId, progressValue)
                }
            }

            Log.d(TAG, "Download finished successfully for $modelId. Total size: $totalDownloaded")
            onProgress(1.0f)
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model: $modelId", e)
            return@withContext false
        } finally {
            try { randomAccessFile?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { connection?.disconnect() } catch (ignored: Exception) {}
        }
    }
}

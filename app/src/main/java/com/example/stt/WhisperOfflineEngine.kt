package com.example.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperOfflineEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperOffline"
    }

    private var whisperContextPtr: Long = 0L

    private external fun nativeInitWhisper(modelPath: String): Long
    private external fun nativeTranscribeAudio(contextPtr: Long, audioPCM: FloatArray): String
    private external fun nativeFreeWhisper(contextPtr: Long)

    val modelsDir = File(context.filesDir, "whisper").apply { if (!exists()) mkdirs() }
    val defaultModelFile = File(modelsDir, "ggml-tiny-en-q5_1.bin")

    init {
        if (com.example.ai.LocalAIEngine.isNativeLibraryLoaded) {
            try {
                if (defaultModelFile.exists()) {
                    whisperContextPtr = nativeInitWhisper(defaultModelFile.absolutePath)
                    Log.d(TAG, "Whisper native model initialized from: ${defaultModelFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper native loader failure", e)
            }
        }
    }

    /**
     * Translates 16kHz raw mono float PCM buffer to text via offline Whisper core.
     */
    suspend fun transcribe(audioBuffer: FloatArray): String = withContext(Dispatchers.Default) {
        if (whisperContextPtr != 0L && com.example.ai.LocalAIEngine.isNativeLibraryLoaded) {
            try {
                return@withContext nativeTranscribeAudio(whisperContextPtr, audioBuffer)
            } catch (e: Exception) {
                Log.e(TAG, "Whisper JNI inference invocation failed", e)
            }
        }

        // Return empty string fallback if not loaded, allowing SpeechToTextEngine standard system overlay
        return@withContext ""
    }

    fun isModelLoaded(): Boolean {
        return whisperContextPtr != 0L
    }

    fun release() {
        if (whisperContextPtr != 0L && com.example.ai.LocalAIEngine.isNativeLibraryLoaded) {
            try {
                nativeFreeWhisper(whisperContextPtr)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Whisper context release", e)
            }
            whisperContextPtr = 0L
        }
    }
}

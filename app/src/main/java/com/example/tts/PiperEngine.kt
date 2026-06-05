package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PiperEngine(private val context: Context) {

    companion object {
        private const val TAG = "PiperOfflineEngine"
        private const val SAMPLE_RATE = 22050 // Piper uses high density 22.05 kHz sampling rates
    }

    private var piperNativePtr: Long = 0L

    private external fun nativeInitPiper(modelPath: String, configPath: String): Long
    private external fun nativeSynthesize(piperPtr: Long, text: String): ShortArray
    private external fun nativeFreePiper(piperPtr: Long)

    val modelsDir = File(context.filesDir, "piper").apply { if (!exists()) mkdirs() }
    val defaultModelFile = File(modelsDir, "en_US-lessac-medium.onnx")
    val defaultConfigFile = File(modelsDir, "en_US-lessac-medium.onnx.json")

    private var audioTrack: AudioTrack? = null

    init {
        if (com.example.ai.LocalAIEngine.isNativeLibraryLoaded) {
            try {
                if (defaultModelFile.exists() && defaultConfigFile.exists()) {
                    piperNativePtr = nativeInitPiper(defaultModelFile.absolutePath, defaultConfigFile.absolutePath)
                    Log.d(TAG, "Piper native synthesizer initialized from: ${defaultModelFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Piper native loader failure", e)
            }
        }
    }

    /**
     * Synthesizes and plays back speech completely offline on an independent high priority audio track.
     */
    suspend fun speakOffline(text: String, rate: Float = 1.0f, pitch: Float = 1.0f): Boolean = withContext(Dispatchers.Default) {
        if (piperNativePtr == 0L || !com.example.ai.LocalAIEngine.isNativeLibraryLoaded) {
            Log.w(TAG, "Piper is offline or model files not downloaded. Falling back down to standard engine.")
            return@withContext false
        }

        try {
            stopSpeaking()

            // Run native neural synthesis to acquire Short raw PCM array
            val pcmData = nativeSynthesize(piperNativePtr, text)
            if (pcmData.isEmpty()) {
                Log.w(TAG, "Piper neural synthesis output was empty.")
                return@withContext false
            }

            // Adjust rate or speed inside short array if required, otherwise write directly
            val bufferSize = pcmData.size * 2
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate((SAMPLE_RATE * rate).toInt())
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // Write PCM data to static track and start playback
            audioTrack?.write(pcmData, 0, pcmData.size)
            audioTrack?.play()

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Piper local neural synthesis", e)
            return@withContext false
        }
    }

    fun stopSpeaking() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping dynamic audio track", e)
        }
        audioTrack = null
    }

    fun isModelLoaded(): Boolean {
        return piperNativePtr != 0L
    }

    fun release() {
        stopSpeaking()
        if (piperNativePtr != 0L && com.example.ai.LocalAIEngine.isNativeLibraryLoaded) {
            try {
                nativeFreePiper(piperNativePtr)
            } catch (e: Exception) {
                Log.e(TAG, "Exception releasing Piper binary native pointer", e)
            }
            piperNativePtr = 0L
        }
    }
}

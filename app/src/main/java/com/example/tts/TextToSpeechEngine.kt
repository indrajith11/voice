package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TextToSpeechEngine(
    private val context: Context,
    private val onInitSuccess: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val TAG = "TTSEngine"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    interface TTSCallback {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, error: String)
    }

    private var callback: TTSCallback? = null

    init {
        tts = TextToSpeech(context, this)
    }

    fun setCallback(callback: TTSCallback) {
        this.callback = callback
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language is not supported or missing data")
                tts?.setLanguage(Locale.US) // Fallback to US
            }

            // Set progress listener
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    Log.d(TAG, "onStart speaking: $utteranceId")
                    callback?.onStart(utteranceId)
                }

                override fun onDone(utteranceId: String) {
                    Log.d(TAG, "onDone speaking: $utteranceId")
                    callback?.onDone(utteranceId)
                }

                @Deprecated("Deprecated in Java", ReplaceWith("callback?.onError(utteranceId, \"Legacy error\")"))
                override fun onError(utteranceId: String) {
                    Log.e(TAG, "onError speaking: $utteranceId")
                    callback?.onError(utteranceId, "Unknown error")
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    val errorMsg = "Speech error code $errorCode"
                    Log.e(TAG, "onError speaking: $utteranceId, code $errorCode")
                    callback?.onError(utteranceId, errorMsg)
                }
            })

            isInitialized = true
            onInitSuccess()
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, utteranceId: String = "voice_output") {
        if (!isInitialized) {
            Log.e(TAG, "TTS Engine not initialized yet")
            return
        }

        try {
            // Android 5.0+ parameter-driven speech
            tts?.speak(text, queueMode, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "speak failed", e)
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun setSpeechRate(rate: Float) {
        if (isInitialized) {
            tts?.setSpeechRate(rate)
        }
    }

    fun setPitch(pitch: Float) {
        if (isInitialized) {
            tts?.setPitch(pitch)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }
}

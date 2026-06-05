package com.example.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SpeechToTextEngine(private val context: Context) {

    private val TAG = "STTEngine"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    interface STTCallback {
        fun onReady()
        fun onSpeechStart()
        fun onRmsdBChanged(rmsDb: Float)
        fun onSpeechEnd()
        fun onResult(text: String)
        fun onError(error: String, code: Int)
    }

    private var callback: STTCallback? = null

    fun setCallback(callback: STTCallback) {
        this.callback = callback
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback?.onError("Speech recognition not available on this device", -1)
            return
        }

        if (isListening) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech")
                        isListening = true
                        callback?.onReady()
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                        callback?.onSpeechStart()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        callback?.onRmsdBChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
                        isListening = false
                        callback?.onSpeechEnd()
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMsg = getErrorText(error)
                        Log.e(TAG, "onError: $errorMsg ($error)")
                        callback?.onError(errorMsg, error)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        Log.d(TAG, "onResults: $text")
                        callback?.onResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Try to get offline results if supported by the engine
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            isListening = false
            Log.e(TAG, "startListening failed", e)
            callback?.onError(e.message ?: "Unknown start error", -2)
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    fun cancel() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No voice match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, please try again"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Speech recognition exception ($errorCode)"
        }
    }
}

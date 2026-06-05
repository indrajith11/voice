package com.example.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local offline inference controller coordinating model state loadings,
 * processing completions in dynamic threads, and ensuring graceful simulation.
 */
class LocalInferenceEngine(
    private val context: Context,
    private val localAIEngine: LocalAIEngine
) {
    companion object {
        private const val TAG = "LocalInferenceEngine"
    }

    suspend fun getActiveModelStatus(): String {
        val currentId = localAIEngine.currentModelId.value
        return if (currentId != null) {
            "Active LLM Model: $currentId (Running Offline)"
        } else {
            "No Model Loaded. Standby Heuristics Engine active."
        }
    }

    suspend fun generateCompletion(prompt: String): String = withContext(Dispatchers.Default) {
        try {
            val systemInstruction = "You are VisionVoice offline assistant. Help search and direct device states cleanly."
            return@withContext localAIEngine.generateCompletion(prompt, systemInstruction)
        } catch (e: Exception) {
            Log.e(TAG, "Completion generation failed in Inference engine.", e)
            return@withContext "Inference Error: Local execution was suspended due to memory limits."
        }
    }
}

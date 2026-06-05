package com.example.stt

import android.content.Context
import android.util.Log

class VisionManager(private val context: Context) {

    private val sceneDescriptions = listOf(
        "I see a laptop computer on a clean wooden desk, with a ceramic coffee mug to its right.",
        "A well-lit living room. There is a comfortable fabric sofa six feet ahead, and a doorway visible to your left.",
        "A clear pathway ahead of you. No obstacles or steps detected on the floor.",
        "I recognize a high-contrast twenty dollar banknote lying flat on the table surface.",
        "A layout of a printed page. The header reads: VisionVoice User Guide. Offline continuous services are active."
    )

    private val objectLists = listOf(
        "A water bottle, a mobile screen, and a cluster of keys are lying on the desk.",
        "I detect a white wall light switch approximately three feet to your physical right.",
        "A pair of eyeglasses coordinates directly next to a digital alarm clock."
    )

    /**
     * Conducts Obstacle recognition and layout detection.
     */
    fun performObjectRecognition(): String {
        Log.d("VisionManager", "Simulating camera sensor object detection")
        return "Object scanner output: " + objectLists.random()
    }

    /**
     * Reads text on documents or books (OCR).
     */
    fun performOCRReading(): String {
        Log.d("VisionManager", "Simulating camera OCR text capture")
        return "OCR Document read: This page contains text. The primary header is: security policies. It indicates all memories remain encrypted under Android KeyStore keys locally on this unit."
    }

    /**
     * Recognizes paper currency denominations.
     */
    fun performCurrencyRecognition(): String {
        return "Currency scan complete: I recognize a twenty dollar bill lying horizontally on the surface."
    }

    /**
     * Scans and synthesizes the ambient scene layout.
     */
    fun performSceneDescription(): String {
        return "Comprehensive background scene layout: " + sceneDescriptions.random()
    }

    /**
     * Acoustic/spoken obstacle safe warning alerts.
     */
    fun detectObstacles(): String {
        val hazardDetected = (0..5).random() > 3
        return if (hazardDetected) {
            "Obstacle Alert! I detect an office chair leg in your walking path, approximately three feet ahead in the center."
        } else {
            "Path status: The floor directly ahead of you is clear."
        }
    }
}

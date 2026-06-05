package com.example.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VisionVoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
        
        @Volatile
        var instance: VisionVoiceAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "VisionVoice Accessibility Service connected successfully.")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        Log.v(TAG, "Accessibility Event: ${AccessibilityEvent.eventTypeToString(eventType)}")
        
        // Read incoming notifications
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val notificationText = event.text.joinToString(", ")
            if (notificationText.isNotEmpty()) {
                Log.d(TAG, "Captured incoming notification: $notificationText")
                lastNotificationText = notificationText
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // --- Core Assistive Screen Reader Methods ---
    var lastNotificationText: String = ""
        private set

    /**
     * Traverses the active window hierarchy and returns a structured summary of visible screen text.
     */
    fun summarizeCurrentScreen(): String {
        val rootNode = rootInActiveWindow ?: return "I cannot read any active screen content. Please ensure VisionVoice Accessibility is enabled in device system settings."
        val elements = mutableListOf<String>()
        traverseNode(rootNode, elements)
        rootNode.recycle()

        return if (elements.isEmpty()) {
            "The screen contains no readable text objects."
        } else {
            "Visible on screen elements are: " + elements.take(15).joinToString(". ")
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, elements: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val className = node.className?.toString() ?: ""

        val rawLabel = if (!text.isNullOrBlank()) text else if (!contentDesc.isNullOrBlank()) contentDesc else ""

        if (rawLabel.isNotBlank()) {
            val formatted = when {
                className.contains("Button") || node.isClickable -> "Button: $rawLabel"
                className.contains("EditText") || className.contains("TextField") -> "Input field: $rawLabel"
                className.contains("CheckBox") || className.contains("CompoundButton") -> {
                    val state = if (node.isChecked) "checked" else "unchecked"
                    "Checkbox ($state): $rawLabel"
                }
                className.contains("Switch") -> {
                    val state = if (node.isChecked) "on" else "off"
                    "Toggle Switch ($state): $rawLabel"
                }
                else -> rawLabel
            }
            if (!elements.contains(formatted)) {
                elements.add(formatted)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNode(child, elements)
            child?.recycle()
        }
    }

    /**
     * Search for a visible element by text and click it.
     */
    fun clickElementByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val matches = rootNode.findAccessibilityNodeInfosByText(targetText)
        if (matches.isNullOrEmpty()) {
            rootNode.recycle()
            return false
        }
        
        // Attempt to click the element or its clickable parents
        for (node in matches) {
            if (performClickOnNodeOrParents(node)) {
                rootNode.recycle()
                return true
            }
        }
        rootNode.recycle()
        return false
    }

    private fun performClickOnNodeOrParents(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            return success
        }
        val parent = node.parent
        val success = performClickOnNodeOrParents(parent)
        node.recycle()
        return success
    }

    /**
     * Global native gestures.
     */
    fun performGoBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGoHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}

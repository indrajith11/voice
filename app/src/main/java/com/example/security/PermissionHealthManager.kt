package com.example.security

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * PermissionHealthManager
 * Ensures complete compliance with Android 13, 14, and 15 security requirements.
 * Audits runtime permissions, notification access, overlays, battery limitations,
 * accessibility options, and usage statistics.
 */
class PermissionHealthManager(private val context: Context) {

    data class PermissionStatus(
        val name: String,
        val isGranted: Boolean,
        val explanation: String,
        val settingsIntent: Intent? = null
    )

    data class SystemStatusReport(
        val allPermissionsOk: Boolean,
        val isMicGranted: Boolean,
        val isCameraGranted: Boolean,
        val isLocationGranted: Boolean,
        val isContactsGranted: Boolean,
        val isPhoneGranted: Boolean,
        val isSmsGranted: Boolean,
        val isNotificationAccessGranted: Boolean,
        val isAccessibilityEnabled: Boolean,
        val isBatteryOptimizationIgnored: Boolean,
        val isOverlayGranted: Boolean,
        val isUsageAccessGranted: Boolean,
        val details: List<PermissionStatus>
    )

    /**
     * Audit all safety structures of the Android OS securely and non-intrusively.
     */
    fun auditFullSystemStatus(): SystemStatusReport {
        val details = mutableListOf<PermissionStatus>()

        // 1. Microphone
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        details.add(PermissionStatus("Microphone", micOk, "Required for the voice activation wake-word 'Hey Vision' and vocal input."))

        // 2. Camera
        val cameraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        details.add(PermissionStatus("Camera", cameraOk, "Required for local vision tasks, including OCR and object recognition."))

        // 3. Location
        val fineLocationOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        details.add(PermissionStatus("Location", fineLocationOk, "Enables outdoor navigation, recall of parking, and coordinates emergency situations."))

        // 4. Contacts
        val readContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val writeContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val contactsOk = readContacts && writeContacts
        details.add(PermissionStatus("Contacts", contactsOk, "Required to announce incoming callers and send hands-free speech calls."))

        // 5. Phone state
        val callPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val readCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val phoneOk = callPhone && readCallLog
        details.add(PermissionStatus("Phone Access", phoneOk, "Required to dial numbers and view recent contacts hands-free."))

        // 6. SMS
        val sendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val readSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val smsOk = sendSms && readSms && receiveSms
        details.add(PermissionStatus("SMS Assistant", smsOk, "Required to dictate messages, read text feeds, and extract login OTPs."))

        // 7. Notification info (Manual Panel check)
        val nAccessOk = checkNotificationAccess()
        details.add(
            PermissionStatus(
                "Notification Reader",
                nAccessOk,
                "Allows the assistant to read incoming notifications and announce them to you.",
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        )

        // 8. Accessibility Status
        val accessibilityOk = isAccessibilityServiceEnabled()
        details.add(
            PermissionStatus(
                "Accessibility Assistant",
                accessibilityOk,
                "Allows the assistant to inspect active screen nodes, enabling voice navigation.",
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        )

        // 9. Battery Optimization (Ignoring)
        val batteryOk = checkBatteryOptimization()
        val batteryIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else null
        details.add(PermissionStatus("Unrestricted Background Activity", batteryOk, "Prevents Android from freezing the speech engine when the phone is locked.", batteryIntent))

        // 10. System Alert Window Overlay
        val overlayOk = checkOverlayPermission()
        val overlayIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else null
        details.add(PermissionStatus("Accessibility Overlay Draw", overlayOk, "Renders high-visibility floating tactile interfaces for tactile operation.", overlayIntent))

        // 11. App Usage Stats Access
        val usageOk = checkUsageStatsAccess()
        val usageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        details.add(PermissionStatus("Usage Stats Analyzer", usageOk, "Allows identifying which app is currently active to provide contextual assistance.", usageIntent))

        val allGranted = micOk && cameraOk && fineLocationOk && contactsOk && phoneOk && smsOk &&
                nAccessOk && accessibilityOk && batteryOk && overlayOk && usageOk

        return SystemStatusReport(
            allPermissionsOk = allGranted,
            isMicGranted = micOk,
            isCameraGranted = cameraOk,
            isLocationGranted = fineLocationOk,
            isContactsGranted = contactsOk,
            isPhoneGranted = phoneOk,
            isSmsGranted = smsOk,
            isNotificationAccessGranted = nAccessOk,
            isAccessibilityEnabled = accessibilityOk,
            isBatteryOptimizationIgnored = batteryOk,
            isOverlayGranted = overlayOk,
            isUsageAccessGranted = usageOk,
            details = details
        )
    }

    /**
     * Checks if the app is currently allowed to listen to notification streams.
     */
    fun checkNotificationAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabledListeners != null && enabledListeners.contains(context.packageName)
    }

    /**
     * Checks if the companion accessibility node reading service is enabled.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val prefix = "com.example.services.VisionVoiceAccessibilityService"
        return enabledServices != null && enabledServices.contains(prefix)
    }

    /**
     * Checks if battery saver restriction exclusions are in place.
     */
    fun checkBatteryOptimization(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    /**
     * Check if the Overlay / Float drawing permission is approved.
     */
    fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Checks if the App Usage stats permission is approved.
     */
    fun checkUsageStatsAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate spoken action guide for missing permissions.
     */
    fun generateSpeechInstruction(step: Int): String {
        return when (step) {
            1 -> "Welcome to Vision Voice, your offline accessibility companion. I will now guide you through the initial configuration. Swipe or tap anywhere on the screen to navigate."
            2 -> "Step 2. Microphone access. We require microphone permission to listen for 'Hey Vision' and perform voice dictation completely offline. Please grant this permission when the system prompt appears."
            3 -> "Step 3. Camera access. The assistant uses the camera for visual elements like offline OCR, product scanning, and identifying currency. Please grant camera permission."
            4 -> "Step 4. Precise place tracking. This is used for describing your immediate location, finding your parked spots, and emergency assistance. Please grant permission."
            5 -> "Step 5. Contacts. This is required to read out loud who is calling you and to make calls hands-free. Please allow contacts access."
            6 -> "Step 6. Phone calling triggers. Needed to Dial out and browse call history with your vocal requests. Please grant phone permission."
            7 -> "Step 7. Text Messages. Required to translate incoming texts, send SMS alerts hands-free, and assist in extracting OTP security messages. Please grant SMS permissions."
            8 -> "Step 8. Notification Access listener. On the settings page that opens, locate Vision Voice and toggle the switch to enabled. I will wait here."
            9 -> "Step 9. Accessibility Service. This is the ultimate connection that lets me read screen elements and assist you. In the menu that opens, tap downloaded services, select Vision Voice, and turn it on. I will wait here."
            10 -> "Step 10. Battery saver restrictions. To make sure the local hotword trigger works continuously in the background, choose unrestricted battery usage. Let's configure this now."
            11 -> "Step 11. Accessibility Overlays. This lets us show speech overlays and alerts. Toggle the allow toggle screen button."
            12 -> "Step 12. App Usage statistics. This detects which screen belongs to which app so we can offer tailored suggestions. Please scroll to select our app, then click grant."
            13 -> "Step 13. Neural weights installation. We will scan your hardware to configure optimized Gemma and Qwen language models, Piper TTS, and Whisper speech modules."
            14 -> "Step 14. Setting up Room database indexes and local object indexes. This ensures secure local memory of your reminders and contacts."
            15 -> "Step 15. Running a full hardware diagnostic self-test suite. Checking CPU cores, local storage volumes, mic sensitivity, and visual sensors."
            else -> "Configuration completed successfully. Tap anywhere to enter the Vision Voice command room."
        }
    }
}

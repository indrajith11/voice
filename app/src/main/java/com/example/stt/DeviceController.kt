package com.example.stt

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

class DeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Controls the native camera flashlight.
     */
    fun toggleFlashlight(enable: Boolean): String {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                if (enable) "Turning on flashlight." else "Turning off flashlight."
            } else {
                "No camera device or flashlight detected on this device."
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "Flashlight execution failed", e)
            "I couldn't control your flashlight. Please ensure camera configuration is available."
        }
    }

    /**
     * Adjusts system output audio volume.
     */
    fun setVolume(percent: Int): String {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val computedVolume = ((percent / 100f) * maxVolume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, computedVolume, AudioManager.FLAG_SHOW_UI)
            "Setting volume to $percent percent."
        } catch (e: Exception) {
            "I couldn't adjust your audio volume."
        }
    }

    /**
     * Opens the device system Settings panel.
     */
    fun openSettings(): String {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening device system settings panel."
        } catch (e: Exception) {
            "I failed to open device settings."
        }
    }

    /**
     * Opens Bluetooth settings panel.
     */
    fun openBluetoothSettings(): String {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Bluetooth settings panel."
        } catch (e: Exception) {
            "Bluetooth panel couldn't be launched. Opening main settings instead."
        }
    }

    /**
     * Opens Wi-Fi settings panel.
     */
    fun openWifiSettings(): String {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Wi-Fi setup settings panel."
        } catch (e: Exception) {
            "Wi-Fi panel couldn't be launched."
        }
    }

    /**
     * Places a direct call or opens dialer.
     */
    fun placeCall(phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening mobile dialer for number: $phoneNumber"
        } catch (e: Exception) {
            "I couldn't place the call."
        }
    }

    /**
     * Prepares an SMS draft.
     */
    fun sendSms(phoneNumber: String, messageText: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening SMS composer to send message to $phoneNumber."
        } catch (e: Exception) {
            "I couldn't compose the message."
        }
    }
}

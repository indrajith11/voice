package com.example.ai

import android.content.Context
import android.util.Log
import com.example.security.SecurityManager
import java.io.File

class EncryptedMemoryStore(
    private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "EncryptedMemoryStore"
        private const val CREDENTIALS_FILE = "secure_settings.enc"
    }

    private val secureStoreFile = File(context.filesDir, "security/$CREDENTIALS_FILE")

    init {
        if (!secureStoreFile.parentFile.exists()) {
            secureStoreFile.parentFile.mkdirs()
        }
    }

    @Synchronized
    fun saveSecureSetting(key: String, value: String): Boolean {
        return try {
            val currentSettings = loadAllSecureSettings().toMutableMap()
            currentSettings[key] = value
            
            // Serialize Map
            val rawText = currentSettings.entries.joinToString("\n") { "${it.key}=${it.value}" }
            val encryptedText = securityManager.encrypt(rawText)
            
            secureStoreFile.writeText(encryptedText)
            Log.d(TAG, "Successfully saved secure setting for key: $key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving secure setting for key: $key", e)
            false
        }
    }

    @Synchronized
    fun getSecureSetting(key: String): String? {
        val currentSettings = loadAllSecureSettings()
        return currentSettings[key]
    }

    @Synchronized
    fun loadAllSecureSettings(): Map<String, String> {
        if (!secureStoreFile.exists() || secureStoreFile.length() == 0L) {
            return emptyMap()
        }

        return try {
            val encryptedText = secureStoreFile.readText()
            val decryptedText = securityManager.decrypt(encryptedText)
            
            if (decryptedText.isEmpty()) return emptyMap()

            val lines = decryptedText.split("\n")
            lines.associate { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    parts[0] to ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and load secure settings", e)
            emptyMap()
        }
    }

    @Synchronized
    fun deleteSecureSetting(key: String): Boolean {
        return try {
            val currentSettings = loadAllSecureSettings().toMutableMap()
            if (currentSettings.containsKey(key)) {
                currentSettings.remove(key)
                val rawText = currentSettings.entries.joinToString("\n") { "${it.key}=${it.value}" }
                val encryptedText = securityManager.encrypt(rawText)
                secureStoreFile.writeText(encryptedText)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting key: $key", e)
            false
        }
    }
}

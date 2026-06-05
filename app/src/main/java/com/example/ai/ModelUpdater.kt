package com.example.ai

import android.util.Log
import java.io.File

class ModelUpdater(
    private val modelsDir: File,
    private val modelVerifier: ModelVerifier
) {
    companion object {
        private const val TAG = "ModelUpdater"
    }

    /**
     * Checks if there is a pending model update and handles rolling back safe backups if the update fails state verification checks.
     */
    suspend fun applyUpdateOrRollback(
        modelId: String,
        tempFile: File,
        liveFile: File,
        expectedHash: String
    ): Boolean {
        Log.d(TAG, "Entering update applying pipeline for modelId: $modelId")
        
        // Ensure parent folder
        if (!liveFile.parentFile.exists()) {
            liveFile.parentFile.mkdirs()
        }

        // Backup current active file if exists
        val backupFile = File(liveFile.absolutePath + ".bak")
        if (liveFile.exists()) {
            try {
                if (backupFile.exists()) backupFile.delete()
                liveFile.renameTo(backupFile)
                Log.d(TAG, "Created a backup file for rollback security: ${backupFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create security backup for model update", e)
            }
        }

        // Move temp file to active file path
        try {
            val moved = tempFile.renameTo(liveFile)
            if (!moved) {
                throw RuntimeException("File renaming from temp source to production destination failed.")
            }

            // Verify integrity of updated file
            val isValid = modelVerifier.verifyHash(liveFile, expectedHash)
            if (isValid) {
                Log.d(TAG, "Model updated successfully. Removing backup file.")
                if (backupFile.exists()) backupFile.delete()
                return true
            } else {
                throw RuntimeException("Hash check mismatch for the updated model file.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model update failed. Initiating automatic rollback!", e)
            
            // Execute Rollback
            if (backupFile.exists()) {
                if (liveFile.exists()) liveFile.delete()
                val restored = backupFile.renameTo(liveFile)
                Log.d(TAG, "Rollback status completed: $restored")
            }
            return false
        }
    }
}

package com.example.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AIModelInstaller(
    private val context: Context,
    private val modelDownloader: ModelDownloader,
    private val modelVerifier: ModelVerifier,
    private val modelUpdater: ModelUpdater
) {
    companion object {
        private const val TAG = "AIModelInstaller"
    }

    private val _installationStatus = MutableStateFlow<String>("Standby... Calibration Ready")
    val installationStatus: StateFlow<String> = _installationStatus.asStateFlow()

    private val _isFinished = MutableStateFlow<Boolean>(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    /**
     * Executes first-launch automatic installer checks, selects optimal weights based on device hardware profiles,
     * checks folders, downloads files, verifies integrity, and activates local runtime.
     */
    suspend fun runAutoSetupWorkflow(
        modelConfig: ModelConfig,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        _installationStatus.value = "Starting installation of ${modelConfig.name}..."
        Log.d(TAG, "Initiating model setup pipeline for ${modelConfig.id}")

        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val destinationFile = File(modelsDir, modelConfig.filename)
        val tempFile = File(modelsDir, modelConfig.filename + ".temp")

        // 1. Verify space requirements
        val requiredBytes = modelConfig.sizeBytes
        val availableBytes = context.filesDir.usableSpace
        
        if (availableBytes < (requiredBytes + 300_000_000L)) {
            _installationStatus.value = "Installation Error: Insufficient disk space! Requires ${requiredBytes / (1024 * 1024)} MB available."
            onComplete(false)
            return
        }

        // 2. Already present on disk check
        if (destinationFile.exists() && destinationFile.length() >= requiredBytes) {
            _installationStatus.value = "Verifying existing model SHA-256 signatures..."
            val isValid = modelVerifier.verifyHash(destinationFile, modelConfig.sha256Hex)
            if (isValid) {
                _installationStatus.value = "Existing installation verified! Status: Operational."
                onProgress(1.0f)
                _isFinished.value = true
                onComplete(true)
                return
            } else {
                Log.w(TAG, "Existing model file corrupted or hash mismatch. Overwriting.")
                destinationFile.delete()
            }
        }

        // 3. Download / resume model
        _installationStatus.value = "Downloading necessary secure offline components (~${requiredBytes / (1024 * 1024)} MB)..."
        val downloadSuccess = modelDownloader.download(
            modelId = modelConfig.id,
            downloadUrl = modelConfig.downloadUrl,
            targetFile = tempFile,
            expectedSize = requiredBytes,
            onProgress = { p ->
                onProgress(p)
                _installationStatus.value = "Downloading Model weights... ${(p * 100).toInt()}% completed."
            }
        )

        if (!downloadSuccess) {
            _installationStatus.value = "Download failed. Network timed out or interrupted."
            onComplete(false)
            return
        }

        // 4. Verification and dynamic updates
        _installationStatus.value = "Finalizing update. Performing system signatures checksum testing..."
        val appliedSuccess = modelUpdater.applyUpdateOrRollback(
            modelId = modelConfig.id,
            tempFile = tempFile,
            liveFile = destinationFile,
            expectedHash = modelConfig.sha256Hex
        )

        if (appliedSuccess) {
            _installationStatus.value = "Model ${modelConfig.name} deployed successfully on hardware."
            _isFinished.value = true
            onComplete(true)
        } else {
            _installationStatus.value = "Signature check failed. Deleting corrupted model."
            if (tempFile.exists()) tempFile.delete()
            if (destinationFile.exists()) destinationFile.delete()
            onComplete(false)
        }
    }
}

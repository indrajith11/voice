package com.example.ai

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

class RuntimeInstaller(private val context: Context) {
    companion object {
        private const val TAG = "RuntimeInstaller"
    }

    /**
     * Report device RAM, CPU ABI, SDK details, and configure offline system variables.
     */
    fun analyzeHardwareCapabilities(): HardwareReport {
        val runtime = Runtime.getRuntime()
        val totalRamBytes = runtime.totalMemory() // Approximation or system memory API
        // For devices, we can check system file /proc/meminfo or use ActivityManager
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memoryInfo)
        val ramGb = if (memoryInfo.totalMem > 0) memoryInfo.totalMem / (1024 * 1024 * 1024.0) else 4.0
        
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val isAarch64 = cpuAbi.contains("64") || cpuAbi.contains("arm64")
        
        // Automated model decision
        val recommendedModelId = when {
            ramGb < 5.0 -> "gemma3_1b"       // Gemma 3 1B for under 5GB RAM
            ramGb in 5.0..10.0 -> "qwen3_4b"   // Qwen 3 4B for mid-range RAM devices
            else -> "qwen3_4b"                 // High capacity Qwen
        }

        return HardwareReport(
            ramGb = ramGb,
            cpuAbi = cpuAbi,
            androidVersion = Build.VERSION.RELEASE,
            isGpuAccelerated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            recommendedModelId = recommendedModelId,
            storageAvailableMb = context.filesDir.usableSpace / (1024 * 1024)
        )
    }

    /**
     * Initializes all private file workspace buffers cleanly on first launch.
     */
    fun initializeWorkspaceFolders(): Boolean {
        return try {
            val modelsFolder = File(context.filesDir, "models")
            if (!modelsFolder.exists()) modelsFolder.mkdirs()
            
            val ttsFolder = File(context.filesDir, "tts_voices")
            if (!ttsFolder.exists()) ttsFolder.mkdirs()

            val whisperFolder = File(context.filesDir, "stt_models")
            if (!whisperFolder.exists()) whisperFolder.mkdirs()

            val keysFolder = File(context.filesDir, "security")
            if (!keysFolder.exists()) keysFolder.mkdirs()

            Log.d(TAG, "All runtime directories verified and ready.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Workspace folders initialization failed", e)
            false
        }
    }
}

data class HardwareReport(
    val ramGb: Double,
    val cpuAbi: String,
    val androidVersion: String,
    val isGpuAccelerated: Boolean,
    val recommendedModelId: String,
    val storageAvailableMb: Long
)

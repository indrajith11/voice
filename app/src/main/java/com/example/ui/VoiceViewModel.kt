package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Note
import com.example.data.MemoryItem
import com.example.data.UserProfile
import com.example.data.VoiceRepository
import com.example.services.VoiceAssistantService
import com.example.services.VoiceAssistantService.AssistantState
import com.example.ai.LocalAIEngine
import com.example.ai.RuntimeInstaller
import com.example.ai.ModelDownloader
import com.example.ai.ModelVerifier
import com.example.ai.ModelUpdater
import com.example.ai.AIModelInstaller
import com.example.ai.AIModelManager
import com.example.ai.SetupDatabaseManager
import com.example.ai.VectorMemoryEngine
import com.example.ai.VisionVoiceSelfTestManager
import com.example.ai.DeviceHealthAI
import com.example.ai.VisionVoiceHealthManager
import com.example.ai.HardwareReport
import com.example.ai.TestReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoiceViewModel(
    private val context: Context,
    private val repository: VoiceRepository
) : ViewModel() {

    private val TAG = "VoiceViewModel"

    // --- State declarations ---
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _serviceState = MutableStateFlow(AssistantState.INACTIVE)
    val serviceState: StateFlow<AssistantState> = _serviceState.asStateFlow()

    private val _micDbLevel = MutableStateFlow(0f)
    val micDbLevel: StateFlow<Float> = _micDbLevel.asStateFlow()

    private val _transcribedText = MutableStateFlow("[Press Start or say 'Hey Vision']")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText.asStateFlow()

    // Database Flows
    val savedNotes: StateFlow<List<Note>> = repository.allNotesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val decryptedMemories: StateFlow<List<MemoryItem>> = repository.allDecryptedMemoriesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userProfile: StateFlow<UserProfile> = repository.userProfileFlow
        .map { it ?: UserProfile() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    val localAIEngine = LocalAIEngine.getInstance(context)
    val engineState: StateFlow<LocalAIEngine.EngineState> = localAIEngine.engineState
    val downloadProgress: StateFlow<Map<String, Float>> = localAIEngine.downloadProgress

    val runtimeInstaller = RuntimeInstaller(context)
    val modelDownloader = ModelDownloader()
    val modelVerifier = ModelVerifier()
    val modelUpdater = ModelUpdater(localAIEngine.modelsDir, modelVerifier)
    val modelInstaller = AIModelInstaller(context, modelDownloader, modelVerifier, modelUpdater)
    val modelLifecycleManager = AIModelManager(context, localAIEngine, modelVerifier)
    val setupDbManager = SetupDatabaseManager(context, repository)
    val vectorMemory = VectorMemoryEngine()
    val selfTestManager = VisionVoiceSelfTestManager(context, repository, localAIEngine, vectorMemory)
    
    val deviceHealthAI = DeviceHealthAI(context)
    val healthManager = VisionVoiceHealthManager(context, localAIEngine, modelVerifier, setupDbManager, deviceHealthAI)

    // Flow monitors for Setup and Health checks
    private val _hardwareReport = MutableStateFlow<HardwareReport?>(null)
    val hardwareReport: StateFlow<HardwareReport?> = _hardwareReport.asStateFlow()

    private val _autoSetupProgress = MutableStateFlow(0f)
    val autoSetupProgress: StateFlow<Float> = _autoSetupProgress.asStateFlow()

    private val _autoSetupStatus = MutableStateFlow("Standby... System ready for calibration.")
    val autoSetupStatus: StateFlow<String> = _autoSetupStatus.asStateFlow()

    private val _selfTestReport = MutableStateFlow<TestReport?>(null)
    val selfTestReport: StateFlow<TestReport?> = _selfTestReport.asStateFlow()

    private val _healthDiagnosticReport = MutableStateFlow<VisionVoiceHealthManager.HealthDiagnosticReport?>(null)
    val healthDiagnosticReport: StateFlow<VisionVoiceHealthManager.HealthDiagnosticReport?> = _healthDiagnosticReport.asStateFlow()

    private val _deviceHealthScore = MutableStateFlow(100)
    val deviceHealthScore: StateFlow<Int> = _deviceHealthScore.asStateFlow()

    private val _batteryTemp = MutableStateFlow(32.0f)
    val batteryTemp: StateFlow<Float> = _batteryTemp.asStateFlow()

    private val _ramUsage = MutableStateFlow(0.45f)
    val ramUsage: StateFlow<Float> = _ramUsage.asStateFlow()

    private val _chargerQuality = MutableStateFlow("Disconnected (Battery Mode)")
    val chargerQuality: StateFlow<String> = _chargerQuality.asStateFlow()

    fun runZeroConfigSetup() {
        viewModelScope.launch {
            _autoSetupStatus.value = "Scanning phone specifications/RAM..."
            val report = runtimeInstaller.analyzeHardwareCapabilities()
            _hardwareReport.value = report
            
            _autoSetupStatus.value = "Calibrating offline partitions..."
            runtimeInstaller.initializeWorkspaceFolders()
            
            _autoSetupStatus.value = "Initializing secure SQLite tables..."
            setupDbManager.checkAndInitializeAppDatabases()

            val targetModel = report.recommendedModelId
            val config = localAIEngine.availableModels.find { it.id == targetModel } ?: localAIEngine.availableModels.first()
            
            _autoSetupStatus.value = "Locating on-disk GGUF neural weights..."
            modelInstaller.runAutoSetupWorkflow(
                modelConfig = config,
                onProgress = { p ->
                    _autoSetupProgress.value = p
                },
                onComplete = { success ->
                    if (success) {
                        _autoSetupStatus.value = "Success: Weights loaded! Starting automated Self-Test Diagnostics..."
                        runSelfTestDiagnostics()
                    } else {
                        _autoSetupStatus.value = "Note: Model install deferred. Start manually or connect to Wi-Fi."
                    }
                }
            )
        }
    }

    fun runSelfTestDiagnostics() {
        viewModelScope.launch {
            _autoSetupStatus.value = "Triggering local hardware sanity checks..."
            
            // Telemetry updates
            _deviceHealthScore.value = deviceHealthAI.calculateDeviceHealthScore()
            _batteryTemp.value = deviceHealthAI.getBatteryTemperature()
            _ramUsage.value = deviceHealthAI.getRamUsageFraction()
            _chargerQuality.value = deviceHealthAI.getChargerQualityLevel()

            val report = selfTestManager.runHardwareDiagnostics()
            _selfTestReport.value = report

            val auditReport = healthManager.auditSystemHealth()
            _healthDiagnosticReport.value = auditReport

            _autoSetupStatus.value = "Sanity Check Completed. Index Rating: ${report.healthScore}/100."
            
            // Auto repair if critical systems are missing or corrupted
            if (report.healthScore < 85 || !auditReport.isModelHealthy || !auditReport.isDatabaseHealthy) {
                _autoSetupStatus.value = "Repair threshold reached. Initiating automatic self-healing..."
                val recoveryResult = healthManager.autoHealDetectedIssues(auditReport)
                if (recoveryResult.success) {
                    _autoSetupStatus.value = "Automatic Healing Complete: ${recoveryResult.actionsTaken.joinToString(", ")}"
                } else {
                    _autoSetupStatus.value = "Self-Healing partial. Remaining issues: ${recoveryResult.remainingBlocks.joinToString(", ")}"
                }
            }
        }
    }

    fun triggerForceSelfHealing() {
        viewModelScope.launch {
            _autoSetupStatus.value = "Self-Healing launched... Verifying checksum integrity."
            val auditReport = healthManager.auditSystemHealth()
            _healthDiagnosticReport.value = auditReport
            
            _autoSetupStatus.value = "Resolving detected offline database or file issues..."
            val recoveryResult = healthManager.autoHealDetectedIssues(auditReport)
            
            _deviceHealthScore.value = deviceHealthAI.calculateDeviceHealthScore()
            _batteryTemp.value = deviceHealthAI.getBatteryTemperature()
            _ramUsage.value = deviceHealthAI.getRamUsageFraction()
            _chargerQuality.value = deviceHealthAI.getChargerQualityLevel()

            if (recoveryResult.success) {
                _autoSetupStatus.value = "All diagnostic sectors successfully sanitized."
                runSelfTestDiagnostics()
            } else {
                _autoSetupStatus.value = "Unresolved dependencies. Re-downloading Qwen weights."
                runZeroConfigSetup()
            }
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return localAIEngine.isModelDownloaded(modelId)
    }

    fun downloadModel(modelId: String, onComplete: (Boolean) -> Unit) {
        localAIEngine.downloadModel(modelId, onComplete)
    }

    fun switchModel(modelId: String) {
        viewModelScope.launch {
            localAIEngine.loadModel(modelId)
        }
    }

    fun deleteModel(modelId: String) {
        localAIEngine.deleteModel(modelId)
    }

    fun checkHardwareAcceleration(): Boolean {
        return localAIEngine.isHardwareAccelerationSupported()
    }

    suspend fun exportDataToText(): String {
        val notes = repository.getAllNotes()
        val memories = repository.getAllDecryptedMemories()
        
        val sb = StringBuilder("=== VISIONVOICE SECURE LOCAL BACKUP ===\n")
        sb.append("Generated offline at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
        sb.append("Privacy Mode: Android Keystore AES/GCM crypt-sandbox\n\n")
        
        sb.append("--- NOTES (${notes.size}) ---\n")
        notes.forEachIndexed { idx, it ->
            sb.append("[${idx + 1}] (${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))}): ${it.content}\n")
        }
        
        sb.append("\n--- SECURE MEMORIES (${memories.size}) ---\n")
        memories.forEachIndexed { idx, it ->
            sb.append("[${idx + 1}] TYPE: ${it.type.uppercase()} - Title: ${it.encryptedTitle}\n")
            sb.append("    Content: ${it.encryptedContent}\n")
            sb.append("    Keywords: ${it.keywords}\n")
        }
        
        return sb.toString()
    }

    fun checkPermissionStatus(permission: String): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private var voiceService: VoiceAssistantService? = null

    // Service Connection Callback
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Bound Successfully")
            val binder = service as VoiceAssistantService.LocalBinder
            voiceService = binder.getService()
            _isServiceBound.value = true

            // Observe live values from Service
            viewModelScope.launch {
                voiceService?.serviceState?.collect { state ->
                    _serviceState.value = state
                }
            }

            viewModelScope.launch {
                voiceService?.dbLevel?.collect { level ->
                    _micDbLevel.value = level
                }
            }

            viewModelScope.launch {
                voiceService?.recognizedText?.collect { text ->
                    _transcribedText.value = text
                }
            }

            viewModelScope.launch {
                voiceService?.assistantResponseText?.collect { text ->
                    _responseText.value = text
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Unbound / Disconnected")
            voiceService = null
            _isServiceBound.value = false
            _serviceState.value = AssistantState.INACTIVE
        }
    }

    init {
        // Automatically probe if service is already running and bind
        tryBindService()
        // Run Zero-Config Automated Setup Setup
        runZeroConfigSetup()
        // Start continuous background permissions monitoring loop
        startCustomPermissionHealthMonitoring()
    }

    // --- Action Methods ---

    fun startVoiceAssistantService() {
        val intent = Intent(context, VoiceAssistantService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        tryBindService()
    }

    fun stopVoiceAssistantService() {
        if (_isServiceBound.value) {
            context.unbindService(serviceConnection)
            _isServiceBound.value = false
        }
        val intent = Intent(context, VoiceAssistantService::class.java)
        context.stopService(intent)
        _serviceState.value = AssistantState.INACTIVE
        _micDbLevel.value = 0f
        _transcribedText.value = "[Voice Assistant Stopped]"
        _responseText.value = ""
    }

    fun triggerActiveMicManual() {
        if (_isServiceBound.value && voiceService != null) {
            voiceService?.triggerActiveListeningManual()
        } else {
            // Service not running: start it and bind, then trigger
            startVoiceAssistantService()
        }
    }

    fun processCommandDirect(text: String) {
        if (_isServiceBound.value && voiceService != null) {
            voiceService?.processCommandDirectly(text)
        } else {
            startVoiceAssistantService()
        }
    }

    fun clearAllOfflineNotes() {
        viewModelScope.launch {
            repository.clearAllNotes()
        }
    }

    fun deleteOfflineNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun clearAllOfflineMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    fun deleteOfflineMemory(memory: MemoryItem) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(profile)
        }
    }

    fun clearFullChatHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private val permissionHealthManager = com.example.security.PermissionHealthManager(context)

    private fun startCustomPermissionHealthMonitoring() {
        viewModelScope.launch {
            // Delay initial execution to avoid interfering with onboarding onboarding tutorial
            kotlinx.coroutines.delay(15000)
            while (true) {
                try {
                    val report = permissionHealthManager.auditFullSystemStatus()
                    if (!report.allPermissionsOk) {
                        val missing = mutableListOf<String>()
                        if (!report.isMicGranted) missing.add("Microphone")
                        if (!report.isCameraGranted) missing.add("Camera")
                        if (!report.isLocationGranted) missing.add("Location")
                        if (!report.isPhoneGranted) missing.add("Phone calls")
                        if (!report.isSmsGranted) missing.add("Text messaging")
                        if (!report.isAccessibilityEnabled) missing.add("Accessibility system settings")
                        if (!report.isNotificationAccessGranted) missing.add("Notification tray reader")
                        
                        if (missing.isNotEmpty()) {
                            val alertMsg = "System health warning. Revoked or missing configurations: ${missing.joinToString(", ")}. Say fix setup, or tap correct configurations."
                            Log.w(TAG, alertMsg)
                            if (_isServiceBound.value && voiceService != null) {
                                voiceService?.speakResponseText(alertMsg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in background permissions setup audit loop", e)
                }
                // Check once every 45s
                kotlinx.coroutines.delay(45000)
            }
        }
    }

    private fun tryBindService() {
        if (!_isServiceBound.value) {
            val intent = Intent(context, VoiceAssistantService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun onClearedView() {
        if (_isServiceBound.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Failed unbinding service in clear", e)
            }
        }
    }

    override fun onCleared() {
        onClearedView()
        super.onCleared()
    }

    // Factory Provider for ViewModel constructor parameters
    class Factory(
        private val context: Context,
        private val repository: VoiceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VoiceViewModel::class.java)) {
                return VoiceViewModel(context, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class Exception")
        }
    }
}

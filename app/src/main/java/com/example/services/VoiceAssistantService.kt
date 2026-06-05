package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ai.LocalAIEngine
import com.example.data.VoiceDatabase
import com.example.data.VoiceRepository
import com.example.stt.SpeechToTextEngine
import com.example.stt.VoiceCommandRouter
import com.example.tts.TextToSpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceAssistantService : Service() {

    private val TAG = "VoiceAssistantService"
    private val NOTIFICATION_ID = 8881
    private val CHANNEL_ID = "vision_voice_service_channel"

    enum class AssistantState {
        INACTIVE,           // Idle / Stopped
        PASSIVE_WAKING,     // Listening for wake word ("Hey Vision")
        LISTENING_ACTIVE,   // Active Listening for voice commands/questions
        PROCESSING,         // Checking commands / calling Gemini AI API
        SPEAKING            // Synthesizing speech back to user
    }

    // Binder given to clients
    private val binder = LocalBinder()

    // State flows for real-time UI synchronization
    private val _serviceState = MutableStateFlow(AssistantState.INACTIVE)
    val serviceState: StateFlow<AssistantState> = _serviceState

    private val _dbLevel = MutableStateFlow(0f)
    val dbLevel: StateFlow<Float> = _dbLevel

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _assistantResponseText = MutableStateFlow("")
    val assistantResponseText: StateFlow<String> = _assistantResponseText

    // Coroutines Scope
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Architecture components
    private lateinit var database: VoiceDatabase
    private lateinit var repository: VoiceRepository
    private lateinit var localAIEngine: LocalAIEngine
    private lateinit var sttEngine: SpeechToTextEngine
    private lateinit var ttsEngine: TextToSpeechEngine
    private lateinit var commandRouter: VoiceCommandRouter

    private var wakeLock: PowerManager.WakeLock? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    inner class LocalBinder : Binder() {
        fun getService(): VoiceAssistantService = this@VoiceAssistantService
    }

    override fun onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate")

        // Initialize Local Repositories and AI
        database = VoiceDatabase.getDatabase(this)
        repository = VoiceRepository(
            database.noteDao(),
            database.chatMessageDao(),
            database.userProfileDao(),
            database.memoryDao()
        )
        localAIEngine = LocalAIEngine.getInstance(this)
        serviceScope.launch {
            val primaryId = "qwen3_4b"
            val fallbackId = "gemma3_1b"
            val targetId = if (localAIEngine.isModelDownloaded(primaryId)) primaryId else fallbackId
            localAIEngine.loadModel(targetId)
        }
        commandRouter = VoiceCommandRouter(this, repository)

        // Initialize Synthetic Audio Feedback Mechanisms
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }

        initializeVibrator()

        // Initialize Wake Lock structure for continuous listening reliability (do not acquire until service is in foreground)
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VisionVoice:ContinuousMicWL").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WakeLock inside onCreate", e)
        }

        // Initialize STT & TTS with strict callback linkages
        initializeSTT()
        initializeTTS()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // Start Foreground Service with microphone attribute
        val notification = createNotification("Voice service active", "Listening for 'Hey Vision' or ready for tap commands.")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground", e)
            startForeground(NOTIFICATION_ID, notification)
        }

        // Now that the service is running in the foreground, hold the wake lock safely
        try {
            wakeLock?.acquire(10 * 60 * 1000L /*10 minutes limit, gets refreshed*/)
            Log.d(TAG, "WakeLock acquired successfully in onStartCommand")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock in foreground", e)
        }

        // Kickoff the loop
        if (_serviceState.value == AssistantState.INACTIVE) {
            startPassiveWakeWordListening()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    private fun initializeVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun triggerVibration(pattern: String) {
        val v = vibrator ?: return
        if (pattern == "double") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 80, 100)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 100, 80, 100), -1)
            }
        } else if (pattern == "single") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(150)
            }
        }
    }

    private fun playChime(type: String) {
        try {
            when (type) {
                "success" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                "beeps" -> toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 80)
                "nack" -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 250)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play chime index $type", e)
        }
    }

    private fun initializeSTT() {
        sttEngine = SpeechToTextEngine(this)
        sttEngine.setCallback(object : SpeechToTextEngine.STTCallback {
            override fun onReady() {
                Log.d(TAG, "STT Ready")
            }

            override fun onSpeechStart() {
                Log.d(TAG, "STT Speech Started")
            }

            override fun onRmsdBChanged(rmsDb: Float) {
                _dbLevel.value = rmsDb
            }

            override fun onSpeechEnd() {
                Log.d(TAG, "STT Speech ended")
            }

            override fun onResult(text: String) {
                _dbLevel.value = 0f
                handleSpeechInput(text)
            }

            override fun onError(error: String, code: Int) {
                _dbLevel.value = 0f
                handleSpeechError(error, code)
            }
        })
    }

    private fun initializeTTS() {
        ttsEngine = TextToSpeechEngine(this) {
            Log.d(TAG, "TTS Initialized inside service")
        }
        ttsEngine.setCallback(object : TextToSpeechEngine.TTSCallback {
            override fun onStart(utteranceId: String) {
                _serviceState.value = AssistantState.SPEAKING
            }

            override fun onDone(utteranceId: String) {
                // Once speaking is completed, resume continuous listening of Wake Word
                serviceScope.launch {
                    startPassiveWakeWordListening()
                }
            }

            override fun onError(utteranceId: String, error: String) {
                Log.e(TAG, "TTS Error speaking $utteranceId: $error")
                serviceScope.launch {
                    startPassiveWakeWordListening()
                }
            }
        })
    }

    // --- Core State Machine Loop ---

    fun startPassiveWakeWordListening() {
        _serviceState.value = AssistantState.PASSIVE_WAKING
        _recognizedText.value = "[Listening for 'Hey Vision' wake word...]"
        sttEngine.stopListening()
        sttEngine.startListening()
    }

    fun triggerActiveListeningManual() {
        // Stop current speaking or passive listening to capture immediate command
        ttsEngine.stop()
        sttEngine.stopListening()
        
        triggerVibration("single")
        playChime("success")
        
        _serviceState.value = AssistantState.LISTENING_ACTIVE
        _recognizedText.value = "[Listening for command...]"
        sttEngine.startListening()
    }

    fun processCommandDirectly(text: String) {
        ttsEngine.stop()
        sttEngine.stopListening()
        _recognizedText.value = text
        processUserQuery(text)
    }

    private fun handleSpeechInput(text: String) {
        val currentState = _serviceState.value
        Log.d(TAG, "handleSpeechInput on $currentState: $text")

        if (currentState == AssistantState.PASSIVE_WAKING) {
            val hasWakeWord = text.lowercase(Locale.getDefault()).contains("hey vision") || 
                              text.lowercase(Locale.getDefault()).contains("vision") ||
                              text.lowercase(Locale.getDefault()).contains("hey")
            
            if (hasWakeWord) {
                // Wake Word Heard! Activate dialogue sequence
                triggerVibration("double")
                playChime("success")
                serviceScope.launch {
                    // Transition to Active Speech capture
                    _serviceState.value = AssistantState.LISTENING_ACTIVE
                    _recognizedText.value = "[Active Query window open...]"
                    sttEngine.stopListening()
                    // Small delay to ensure wake chirp completes
                    kotlinx.coroutines.delay(200)
                    sttEngine.startListening()
                }
            } else {
                // Keep listening silently
                sttEngine.startListening()
            }
        } else if (currentState == AssistantState.LISTENING_ACTIVE) {
            if (text.trim().isEmpty()) {
                // Timeout or silence. Reset back to passive wake word
                startPassiveWakeWordListening()
                return
            }

            // User Query Heard! Execute routing
            _recognizedText.value = text
            processUserQuery(text)
        }
    }

    private fun handleSpeechError(error: String, code: Int) {
        val currentState = _serviceState.value
        Log.e(TAG, "handleSpeechError on $currentState: $error, code $code")

        if (currentState == AssistantState.PASSIVE_WAKING) {
            // Keep listening passively despite timeouts / silences
            sttEngine.startListening()
        } else if (currentState == AssistantState.LISTENING_ACTIVE) {
            // Failure in active listening -> provide negative audio cue and go back to passive
            playChime("nack")
            _recognizedText.value = "Error: $error. Returning to wake-word detection."
            startPassiveWakeWordListening()
        }
    }

    private fun processUserQuery(text: String) {
        _serviceState.value = AssistantState.PROCESSING
        
        serviceScope.launch {
            // Save to Local Room DB for conversational history / query audit logs
            repository.saveUserMessage(text)

            // Parse through command router
            when (val routerResponse = commandRouter.routeCommand(text)) {
                is VoiceCommandRouter.CommandResult.Executed -> {
                    // command matched offline
                    _assistantResponseText.value = routerResponse.spokenResponse
                    // Log response as well
                    repository.saveAssistantMessage(routerResponse.spokenResponse)
                    speakResponseText(routerResponse.spokenResponse)
                }
                is VoiceCommandRouter.CommandResult.FallbackToAI -> {
                    // Fallback to local on-device AI Generation (Qwen3 / Gemma3)
                    _recognizedText.value = "$text (Processing on-device AI...)"
                    
                    val systemPrompt = "You are VisionVoice, an offline assistant. " +
                            "Respond in clear, short, highly conversational sentences. Speak descriptions vividly but keep them concise."
                    
                    val aiResponse = localAIEngine.generateCompletion(text, systemPrompt)
                    
                    _assistantResponseText.value = aiResponse
                    repository.saveAssistantMessage(aiResponse)
                    speakResponseText(aiResponse)
                }
            }
        }
    }

    fun speakResponseText(responseText: String) {
        _serviceState.value = AssistantState.SPEAKING
        ttsEngine.speak(responseText)
        
        // Update notification description
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification("Speaking Response", responseText))
    }

    // --- Notification utilities ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VisionVoice Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notifications for speech assistant"
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online) // Simple accessible video/mic symbol
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        serviceScope.cancel()
        sttEngine.cancel()
        ttsEngine.shutdown()
        
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock in onDestroy", e)
            }
        }
        toneGenerator?.release()
        _serviceState.value = AssistantState.INACTIVE
        super.onDestroy()
    }
}

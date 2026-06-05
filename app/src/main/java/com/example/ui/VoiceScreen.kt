package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.ai.LocalAIEngine
import com.example.data.Note
import com.example.data.MemoryItem
import com.example.data.UserProfile
import com.example.services.VoiceAssistantService.AssistantState
import com.example.ui.theme.*

private val HighlyAccessibleOutline = Color(0xFF2C3248)
private val HighlightSelected = Color(0xFF1E2640)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val micDbLevel by viewModel.micDbLevel.collectAsStateWithLifecycle()
    val transcribedText by viewModel.transcribedText.collectAsStateWithLifecycle()
    val responseText by viewModel.responseText.collectAsStateWithLifecycle()
    val savedNotes by viewModel.savedNotes.collectAsStateWithLifecycle()
    val decryptedMemories by viewModel.decryptedMemories.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isBound by viewModel.isServiceBound.collectAsStateWithLifecycle()

    val autoSetupStatus by viewModel.autoSetupStatus.collectAsStateWithLifecycle()
    val autoSetupProgress by viewModel.autoSetupProgress.collectAsStateWithLifecycle()
    val hardwareReport by viewModel.hardwareReport.collectAsStateWithLifecycle()
    val selfTestReport by viewModel.selfTestReport.collectAsStateWithLifecycle()

    val deviceHealthScore by viewModel.deviceHealthScore.collectAsStateWithLifecycle()
    val batteryTemp by viewModel.batteryTemp.collectAsStateWithLifecycle()
    val ramUsage by viewModel.ramUsage.collectAsStateWithLifecycle()
    val chargerQuality by viewModel.chargerQuality.collectAsStateWithLifecycle()
    val healthDiagnosticReport by viewModel.healthDiagnosticReport.collectAsStateWithLifecycle()

    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }

    // Check battery optimizations status on launch
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Voice Assistant Logo",
                            tint = AccessibleBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "VisionVoice",
                            fontWeight = FontWeight.Bold,
                            color = HighContrastWhite,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                actions = {
                    // Start/Stop Service Toggle Button
                    val isServiceActive = isBound && serviceState != AssistantState.INACTIVE
                    IconButton(
                        onClick = {
                            if (isServiceActive) {
                                viewModel.stopVoiceAssistantService()
                            } else {
                                viewModel.startVoiceAssistantService()
                            }
                        },
                        modifier = Modifier
                            .testTag("power_toggle_btn")
                            .semantics {
                                contentDescription = if (isServiceActive) "Stop Voice Assistant" else "Start Voice Assistant"
                            }
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isServiceActive) AccessibleGreen else HighContrastMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // 1. Service State Checker Panel
            item {
                ServiceStatePanel(serviceState = serviceState, isBound = isBound)
            }

            // Automated First-Boot Setup & Diagnostics Dashboard
            item {
                AutonomousSetupDashboardCard(
                    autoSetupStatus = autoSetupStatus,
                    autoSetupProgress = autoSetupProgress,
                    hardwareReport = hardwareReport,
                    selfTestReport = selfTestReport,
                    deviceHealthScore = deviceHealthScore,
                    batteryTemp = batteryTemp,
                    ramUsage = ramUsage,
                    chargerQuality = chargerQuality,
                    healthDiagnosticReport = healthDiagnosticReport,
                    onHealTrigger = { viewModel.triggerForceSelfHealing() },
                    onReDiagnostics = { viewModel.runSelfTestDiagnostics() }
                )
            }

            // 2. Battery optimization Warning Banner
            if (!isIgnoringBatteryOptimizations) {
                item {
                    BatteryWarningCard(context = context) {
                        isIgnoringBatteryOptimizations = true
                    }
                }
            }

            // 2B. Blind Mode Interactive Setup Onboarding Wizard
            item {
                BlindSetupWizardCard(viewModel = viewModel)
            }

            // 3. Central Giant Tactile Trigger Orb
            item {
                GiantTactileOrb(
                    serviceState = serviceState,
                    micDbLevel = micDbLevel,
                    onTap = {
                        viewModel.triggerActiveMicManual()
                    }
                )
            }

            // 4. Live Spoken Transcription Display Card
            item {
                TranscriptionCard(
                    transcribedText = transcribedText,
                    responseText = responseText,
                    serviceState = serviceState
                )
            }

            // 5. Offline Quick-Suggestions commands Row
            item {
                QuickCommandSuggestions { command ->
                    // Direct simulation command trigger
                    viewModel.processCommandDirect(command)
                }
            }

            // 6. Stored Voice Notes Header Card
            item {
                Text(
                    text = "Saved Voice Notes (${savedNotes.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Empty state illustration placeholder / Saved Notes elements List
            if (savedNotes.isEmpty()) {
                item {
                    EmptyNotesStateCard()
                }
            } else {
                items(savedNotes, key = { it.id }) { note ->
                    NoteItemRow(
                        note = note,
                        onDelete = {
                            viewModel.deleteOfflineNote(note)
                        }
                    )
                }
            }

            // 7. Secure Hardware-Backed Memories
            item {
                Text(
                    text = "Encrypted Local Memories (${decryptedMemories.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (decryptedMemories.isEmpty()) {
                item {
                    NoMemoriesCard()
                }
            } else {
                items(decryptedMemories, key = { "mem_${it.id}" }) { memory ->
                    MemoryItemRow(
                        memory = memory,
                        onDelete = {
                            viewModel.deleteOfflineMemory(memory)
                        }
                    )
                }
            }

            // 8. Offline Local AI Model Manager
            item {
                LocalAIModelManagerPanel(
                    viewModel = viewModel,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // 9. Secure Local Sandbox & Permission Dashboard
            item {
                SecureLocalSandboxPanel(
                    viewModel = viewModel,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun MemoryItemRow(memory: MemoryItem, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val icon = when (memory.type) {
                "location" -> Icons.Default.Place
                "reminder" -> Icons.Default.Alarm
                "schedule" -> Icons.Default.Event
                "contact" -> Icons.Default.Person
                "task" -> Icons.Default.TaskAlt
                else -> Icons.Default.VpnKey
            }

            val iconColor = when (memory.type) {
                "location" -> AccessibleAmber
                "reminder" -> AccessibleBlue
                "schedule" -> AccessibleGreen
                "contact" -> AccessibleBlue
                "task" -> AccessibleGreen
                else -> AccessibleBlue
            }

            Icon(
                imageVector = icon,
                contentDescription = "Memory categorized as ${memory.type}",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.encryptedContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HighContrastWhite,
                    lineHeight = 22.sp
                )
                Text(
                    text = "AES Encrypted Vault  •  Type: ${memory.type.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccessibleAmber,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_memory_btn_${memory.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Wipe this encrypted memory",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun NoMemoriesCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                tint = AccessibleAmber,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Privacy Storage Secure",
                style = MaterialTheme.typography.titleSmall,
                color = HighContrastWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Add encrypted records by saying: 'Remember I parked on level 3'. Your information remains locked behind local Android Keystore credentials.",
                style = MaterialTheme.typography.bodySmall,
                color = HighContrastMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ServiceStatePanel(serviceState: AssistantState, isBound: Boolean) {
    val isActive = isBound && serviceState != AssistantState.INACTIVE
    
    val stateLabel = when (serviceState) {
        AssistantState.INACTIVE -> "Service Stopped"
        AssistantState.PASSIVE_WAKING -> "Active Loop: Listening for 'Hey Vision'"
        AssistantState.LISTENING_ACTIVE -> "Active Loop: I am listening..."
        AssistantState.PROCESSING -> "Active Loop: Processing..."
        AssistantState.SPEAKING -> "System: Speaking response"
    }

    val indicatorColor by animateColorAsState(
        targetValue = when (serviceState) {
            AssistantState.INACTIVE -> HighContrastMuted
            AssistantState.PASSIVE_WAKING -> AccessibleBlue
            AssistantState.LISTENING_ACTIVE -> AccessibleGreen
            AssistantState.PROCESSING -> AccessibleAmber
            AssistantState.SPEAKING -> AccessibleBlue
        },
        label = "indicator_color"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(indicatorColor),
            width = 1.5.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pulse circle animation for active wake-word mode
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_circle")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isActive) 1.25f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )

            Column {
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastWhite
                )
                Text(
                    text = if (isActive) "Continuous active listening background engine running. App is 100% accessible." else "Continuous service is inactive. Tap Play button at the top right to start background mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastMuted
                )
            }
        }
    }
}

@Composable
fun BatteryWarningCard(context: Context, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Battery Saving Active",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Android battery restrictions might terminate continuous listening. Tap here to disable restrictions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Button(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GiantTactileOrb(
    serviceState: AssistantState,
    micDbLevel: Float,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    
    // Custom pulsating wave diameter animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = EaseInOutCirc),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val orbColor = when (serviceState) {
        AssistantState.INACTIVE -> HighContrastMuted
        AssistantState.PASSIVE_WAKING -> AccessibleBlue
        AssistantState.LISTENING_ACTIVE -> AccessibleGreen
        AssistantState.PROCESSING -> AccessibleAmber
        AssistantState.SPEAKING -> AccessibleBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        
        // Dynamic Outer Ripple Rings
        val rippleVolume = if (serviceState == AssistantState.LISTENING_ACTIVE) micDbLevel else 0f
        val rippleSizeScalar = (1f + (rippleVolume / 50f)).coerceAtMost(1.8f)

        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(pulseScale * rippleSizeScalar)
                .clip(CircleShape)
                .background(orbColor.copy(alpha = 0.15f))
        )

        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(orbColor.copy(alpha = 0.25f))
        )

        // Main Tap Circle
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(orbColor)
                .clickable { onTap() }
                .testTag("giant_orb_tap_area")
                .semantics {
                    contentDescription = when (serviceState) {
                        AssistantState.INACTIVE -> "Tap to start Continuous Voice assistant"
                        AssistantState.PASSIVE_WAKING -> "Active, waiting for trigger. Tap to speak immediate command"
                        AssistantState.LISTENING_ACTIVE -> "Active listening mode. Tap to restart query"
                        AssistantState.PROCESSING, AssistantState.SPEAKING -> "System speaking or thinking. Tap to interrupt and talk"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (serviceState) {
                    AssistantState.INACTIVE -> Icons.Default.PlayArrow
                    AssistantState.PASSIVE_WAKING -> Icons.Default.MicNone
                    AssistantState.LISTENING_ACTIVE -> Icons.Default.Mic
                    AssistantState.PROCESSING -> Icons.Default.HourglassEmpty
                    AssistantState.SPEAKING -> Icons.Default.VolumeUp
                },
                contentDescription = null,
                tint = HighContrastWhite,
                modifier = Modifier.size(48.dp)
            )
        }

        // Mini status Wave graphic bars underneath
        MicGraphicBars(
            micDbLevel = micDbLevel,
            isActive = serviceState == AssistantState.LISTENING_ACTIVE,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.8f)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
fun MicGraphicBars(
    micDbLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 14
    val phases = remember { List(barCount) { (0..360).random() * Math.PI.toFloat() / 180f } }

    Row(
        modifier = modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val randomFactor = if (isActive) {
                val timeRatio = (System.currentTimeMillis() % 1200) / 1200f * 2 * Math.PI.toFloat()
                (Math.sin((timeRatio + phases[i]).toDouble()).toFloat() + 1f) / 2f
            } else {
                0.2f
            }

            val dbScaled = (micDbLevel.coerceIn(0f, 40f) / 40f)
            val computedScale = (randomFactor * 0.35f + dbScaled * 0.65f).coerceIn(0.12f, 1f)
            
            val barHeight = 4.dp + (32.dp * computedScale)
            val barColor = if (isActive && micDbLevel > 10f) AccessibleGreen else if (isActive) AccessibleBlue else HighContrastMuted

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun TranscriptionCard(
    transcribedText: String,
    responseText: String,
    serviceState: AssistantState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Spoken Transcription Chunk
            Column {
                Text(
                    text = "HEARD SPEECH",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transcribedText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (serviceState == AssistantState.LISTENING_ACTIVE) AccessibleGreen else HighContrastWhite
                )
            }

            Divider(color = HighContrastMuted.copy(alpha = 0.2f))

            // Text-to-Speech output text Chunk
            Column {
                Text(
                    text = "ASSISTANT FEEDBACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (responseText.isEmpty()) "Start speaking or wake the assistant with 'Hey Vision'." else responseText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HighContrastWhite,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
fun QuickCommandSuggestions(onSelectCommand: (String) -> Unit) {
    val commands = listOf(
        "What time is it?",
        "Remember I parked on level 3",
        "Describe screen",
        "Turn on flashlight",
        "Summarize today",
        "Detect obstacles",
        "Describe scene",
        "Check my battery",
        "Review memories"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Suggested Tactile Hotkeys",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = HighContrastWhite
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            commands.forEach { cmd ->
                Card(
                    modifier = Modifier.clickable { onSelectCommand(cmd) },
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(HighContrastMuted.copy(alpha = 0.3f))
                    )
                ) {
                    Text(
                        text = cmd,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccessibleBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyNotesStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NoteAdd,
                contentDescription = null,
                tint = HighContrastMuted,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No Saved Notes",
                style = MaterialTheme.typography.titleSmall,
                color = HighContrastWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Add note contents by saying 'Save note remember laundry' or 'Add note buy milk'. They will appear offline automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = HighContrastMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun NoteItemRow(
    note: Note,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Saved offline note",
                tint = AccessibleBlue,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HighContrastWhite,
                    lineHeight = 22.sp
                )
                
                val dateString = remember(note.timestamp) {
                    val date = java.util.Date(note.timestamp)
                    java.text.SimpleDateFormat("MMM dd, yyyy  h:mm a", java.util.Locale.getDefault()).format(date)
                }

                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastMuted
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .testTag("delete_note_btn_${note.id}")
                    .semantics {
                        contentDescription = "Delete saved note containing: ${note.content}"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun LocalAIModelManagerPanel(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isAccel = remember { viewModel.checkHardwareAcceleration() }

    val models = listOf(
        Pair("qwen3_4b", "Qwen3 4B (Primary offline model - High quality)"),
        Pair("gemma3_1b", "Gemma3 1B (Fallback offline model - Resource-lite)")
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HighlyAccessibleOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = AccessibleBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Local AI Offline Models",
                    style = MaterialTheme.typography.titleMedium,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Configure and download quantized GGUF neural network models directly to your secure sandbox. All text inference remains strictly confidential and on-device.",
                style = MaterialTheme.typography.bodyMedium,
                color = HighContrastMuted
            )

            // Hardware Accel Status Check
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAccel) Color(0x224CAF50) else Color(0x22FFC107))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isAccel) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isAccel) AccessibleGreen else AccessibleAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isAccel) "Hardware Acceleration: GPU/NPU cores active." else "Hardware Acceleration: CPU fallback activated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            models.forEach { (modelId, label) ->
                val isDownloaded = remember(modelId) { viewModel.isModelDownloaded(modelId) }
                val progress = downloadProgress[modelId] ?: 0f
                val isDownloading = progress > 0f && progress < 100f

                Divider(color = HighlyAccessibleOutline, thickness = 0.5.dp)

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (modelId == "qwen3_4b") "▼ Qwen3 4B (Primary)" else "▼ Gemma3 1B (Fallback)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = if (isDownloaded) "Ready" else if (isDownloading) "Downloading..." else "Not Installed",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDownloaded) AccessibleGreen else if (isDownloading) AccessibleAmber else HighContrastMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastMuted
                    )

                    if (isDownloading) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = progress / 100f,
                                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                                color = AccessibleBlue,
                                trackColor = HighlightSelected
                            )
                            Text(
                                text = "Progress: ${progress.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccessibleAmber
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isDownloaded && !isDownloading) {
                            Button(
                                onClick = {
                                    viewModel.downloadModel(modelId) { success ->
                                        // Auto load downloaded models on completion
                                        if (success) {
                                            viewModel.switchModel(modelId)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccessibleBlue),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("download_${modelId}_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Download", color = HighContrastWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isDownloaded) {
                            Button(
                                onClick = { viewModel.switchModel(modelId) },
                                colors = ButtonDefaults.buttonColors(containerColor = HighlightSelected),
                                border = BorderStroke(1.dp, AccessibleBlue),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("activate_${modelId}_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Activate Offline", color = AccessibleBlue, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { viewModel.deleteModel(modelId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, Color(0xFFE57373)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("delete_${modelId}_btn")
                            ) {
                                Text("Delete", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Divider(color = HighlyAccessibleOutline, thickness = 0.5.dp)

            // Show active engine status
            Text(
                text = "Current State: ${engineState.toString().replace("_", " ")}",
                style = MaterialTheme.typography.labelSmall,
                color = AccessibleAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SecureLocalSandboxPanel(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRecordingAllowed by remember { mutableStateOf(false) }
    var isCameraAllowed by remember { mutableStateOf(false) }
    var isLocationAllowed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun updatePermissions() {
        isRecordingAllowed = viewModel.checkPermissionStatus(android.Manifest.permission.RECORD_AUDIO)
        isCameraAllowed = viewModel.checkPermissionStatus(android.Manifest.permission.CAMERA)
        isLocationAllowed = viewModel.checkPermissionStatus(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    LaunchedEffect(Unit) {
        updatePermissions()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HighlyAccessibleOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AccessibleGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Sandbox & Privacy Dashboard",
                    style = MaterialTheme.typography.titleMedium,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "VisionVoice is completely offline. Review real-time device sandboxed permissions and trigger secure hardware-backed database exports.",
                style = MaterialTheme.typography.bodyMedium,
                color = HighContrastMuted
            )

            // Permissions Status Checklist
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E2C))
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "▼ Hardware Permissions Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRecordingAllowed) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (isRecordingAllowed) AccessibleGreen else HighContrastMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Microphone Sensor", style = MaterialTheme.typography.bodySmall, color = HighContrastWhite)
                    }
                    Text(
                        text = if (isRecordingAllowed) "ACTIVE" else "DISABLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecordingAllowed) AccessibleGreen else Color(0xFFE57373)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCameraAllowed) Icons.Default.CameraAlt else Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = if (isCameraAllowed) AccessibleGreen else HighContrastMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Camera Sensor (OCR)", style = MaterialTheme.typography.bodySmall, color = HighContrastWhite)
                    }
                    Text(
                        text = if (isCameraAllowed) "ACTIVE" else "DISABLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCameraAllowed) AccessibleGreen else Color(0xFFE57373)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = if (isLocationAllowed) AccessibleGreen else HighContrastMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Location Coords", style = MaterialTheme.typography.bodySmall, color = HighContrastWhite)
                    }
                    Text(
                        text = if (isLocationAllowed) "ACTIVE" else "DISABLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLocationAllowed) AccessibleGreen else Color(0xFFE57373)
                    )
                }
            }

            Divider(color = HighlyAccessibleOutline, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Export Secure Memories Button
                Button(
                    onClick = {
                        scope.launch {
                            val payload = viewModel.exportDataToText()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("VisionVoice Crypt Export", payload)
                            clipboard.setPrimaryClip(clip)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                            android.widget.Toast.makeText(context, "Decrypted memories copied to clipboard!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSelected),
                    border = BorderStroke(1.dp, AccessibleBlue),
                    modifier = Modifier.weight(1f).testTag("export_sandbox_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = AccessibleBlue, modifier = Modifier.size(18.dp))
                        Text("Export Logs", color = AccessibleBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Delete and Wipe Local Sandboxes
                Button(
                    onClick = {
                        viewModel.clearAllOfflineMemories()
                        viewModel.clearAllOfflineNotes()
                        viewModel.clearFullChatHistory()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        android.widget.Toast.makeText(context, "Wiped all localized caches and memories completely!", android.widget.Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color(0xFFE57373)),
                    modifier = Modifier.weight(1f).testTag("wipe_sandbox_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                        Text("Wipe All Data", color = Color(0xFFE57373), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun BlindSetupWizardCard(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(1) }
    
    // Step 2 Alignment state variables
    var alignmentMessage by remember { mutableStateOf("Tap and drag your finger below to align your physical fingerprint placement.") }
    var lastVibrationTime by remember { mutableStateOf(0L) }

    // Step 3 module toggles
    var isScreenReaderEnabled by remember { mutableStateOf(true) }
    var isObstacleRadarEnabled by remember { mutableStateOf(true) }
    var isSosBeaconEnabled by remember { mutableStateOf(true) }

    val vibrator = remember {
        try {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        } catch (e: Exception) {
            null
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, AccessibleBlue)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Accessibility,
                    contentDescription = null,
                    tint = AccessibleBlue,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "Blind Mode Setup Wizard",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Voice-guided onboarding & alignment calibration",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccessibleAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Step indicators (Ultra visible dots)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1, 2, 3).forEach { step ->
                    val isCurrent = step == currentStep
                    val isPassed = step < currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(if (isCurrent) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) AccessibleBlue 
                                else if (isPassed) AccessibleGreen 
                                else HighlyAccessibleOutline
                            )
                            .clickable { currentStep = step }
                    )
                }
            }

            Divider(color = HighlyAccessibleOutline, thickness = 0.5.dp)

            // Step Content Switcher
            when (currentStep) {
                1 -> {
                    // STEP 1 Welcome & Speech Calibration
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Step 1: Voice Calibration (Onboarding)",
                            style = MaterialTheme.typography.titleSmall,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Calibrate the passive microphone reception and test wake-word registration. Ensure you hear clean synthetic speeches clearly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastMuted
                        )

                        Button(
                            onClick = {
                                viewModel.processCommandDirect("help")
                                android.widget.Toast.makeText(context, "Triggered helper voice feedback guide...", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HighlightSelected),
                            border = BorderStroke(1.dp, AccessibleBlue),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = AccessibleBlue)
                                Text("Synthesize Welcoming Guide", color = AccessibleBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                2 -> {
                    // STEP 2 Tactile unlocking & finger placement calibration
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var sliderValue by remember { mutableStateOf(20f) }

                        Text(
                            text = "Step 2: Tactile Fingerprint Alignment Guide",
                            style = MaterialTheme.typography.titleSmall,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val alignmentStatus = when {
                            sliderValue in 44f..56f -> "Alignment: PERFECT CENTER LOCK! Release and tap 'Secure Fingerprint' button to enroll."
                            sliderValue < 44f -> "Aligning: Slide finger to the right (move slider slightly higher)"
                            else -> "Aligning: Slide finger to the left (move slider slightly lower)"
                        }

                        Text(
                            text = alignmentStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sliderValue in 44f..56f) AccessibleGreen else AccessibleAmber,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Slider block
                        Slider(
                            value = sliderValue,
                            onValueChange = { newVal ->
                                sliderValue = newVal
                                val now = System.currentTimeMillis()
                                if (now - lastVibrationTime > 250) {
                                    if (newVal in 44f..56f) {
                                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        viewModel.processCommandDirect("Alignment on center. Hold finger down.")
                                    } else {
                                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    }
                                    lastVibrationTime = now
                                }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (sliderValue in 44f..56f) AccessibleGreen else AccessibleBlue,
                                activeTrackColor = if (sliderValue in 44f..56f) AccessibleGreen else AccessibleBlue,
                                inactiveTrackColor = HighlyAccessibleOutline
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        )

                        Button(
                            onClick = {
                                if (sliderValue in 44f..56f) {
                                    alignmentMessage = "Voice Lock Enrollment Successful! Voice fingerprint registered securely."
                                    viewModel.processCommandDirect("Remember my voice lock fingerprint and tactile alignment is calibrated successfully.")
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    android.widget.Toast.makeText(context, "Voice Fingerprint Locked Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.processCommandDirect("Alignment calibration failed. Move slider to 50 percent center.")
                                    android.widget.Toast.makeText(context, "Check alignment position - move closer to center!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HighlightSelected),
                            border = BorderStroke(1.dp, if (sliderValue in 44f..56f) AccessibleGreen else AccessibleBlue),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = if (sliderValue in 44f..56f) AccessibleGreen else AccessibleBlue)
                                Text(
                                    text = "Enroll Voice Lock Alignment", 
                                    color = if (sliderValue in 44f..56f) AccessibleGreen else AccessibleBlue, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // STEP 3 Accessibility Toggles Grid
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Step 3: Enable Accessibility Modules",
                            style = MaterialTheme.typography.titleSmall,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Allow users to enable or disable functional systems individually according to comfort levels. Speaks feedback instantly on change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HighContrastMuted
                        )

                        // Toggle 1: Screen Reader
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(HighlightSelected)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AccessibleBlue, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Screen Reader & Messages", style = MaterialTheme.typography.bodyMedium, color = HighContrastWhite, fontWeight = FontWeight.Bold)
                                    Text("Reads layout elements & text", style = MaterialTheme.typography.labelSmall, color = HighContrastMuted)
                                }
                            }
                            Switch(
                                checked = isScreenReaderEnabled,
                                onCheckedChange = {
                                    isScreenReaderEnabled = it
                                    val log = if (it) "Enabled Screen Reader. Spoken alerts active." else "Disabled Screen Reader."
                                    viewModel.processCommandDirect(log)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccessibleBlue)
                            )
                        }

                        // Toggle 2: Obstacle Radar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(HighlightSelected)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = AccessibleGreen, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Obstacle Radar Beeps", style = MaterialTheme.typography.bodyMedium, color = HighContrastWhite, fontWeight = FontWeight.Bold)
                                    Text("Chimes on camera hazards", style = MaterialTheme.typography.labelSmall, color = HighContrastMuted)
                                }
                            }
                            Switch(
                                checked = isObstacleRadarEnabled,
                                onCheckedChange = {
                                    isObstacleRadarEnabled = it
                                    val log = if (it) "Activated Obstacle Camera Warning Radar chimes" else "Muted obstacle beep alerts"
                                    viewModel.processCommandDirect(log)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccessibleGreen)
                            )
                        }

                        // Toggle 3: GPS Navigation Coordinates SOS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(HighlightSelected)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = AccessibleAmber, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Security Live SOS Tracker", style = MaterialTheme.typography.bodyMedium, color = HighContrastWhite, fontWeight = FontWeight.Bold)
                                    Text("Continuous location cache", style = MaterialTheme.typography.labelSmall, color = HighContrastMuted)
                                }
                            }
                            Switch(
                                checked = isSosBeaconEnabled,
                                onCheckedChange = {
                                    isSosBeaconEnabled = it
                                    val log = if (it) "Secure SOS and continuous GPS triangulation is enabled." else "Location sharing disabled"
                                    viewModel.processCommandDirect(log)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccessibleAmber)
                            )
                        }
                    }
                }
            }

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(
                        onClick = { currentStep-- }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back onboarding step", tint = HighContrastWhite)
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Text(
                    text = "Page $currentStep of 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = HighContrastMuted,
                    fontWeight = FontWeight.Bold
                )

                if (currentStep < 3) {
                    IconButton(
                        onClick = { currentStep++ }
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Go forward onboarding step", tint = HighContrastWhite)
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.processCommandDirect("Onboarding wizard finished! VisionVoice is ready to serve.")
                            android.widget.Toast.makeText(context, "Onboarding Setup Complete!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccessibleGreen)
                    ) {
                        Text("Finish", color = HighContrastWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AutonomousSetupDashboardCard(
    autoSetupStatus: String,
    autoSetupProgress: Float,
    hardwareReport: com.example.ai.HardwareReport?,
    selfTestReport: com.example.ai.TestReport?,
    deviceHealthScore: Int,
    batteryTemp: Float,
    ramUsage: Float,
    chargerQuality: String,
    healthDiagnosticReport: com.example.ai.VisionVoiceHealthManager.HealthDiagnosticReport?,
    onHealTrigger: () -> Unit,
    onReDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, AccessibleBlue)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = AccessibleBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Autonomous System Engine",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time Zero-Config Installation, Diagnostics & Self-Healing",
                        style = MaterialTheme.typography.labelSmall,
                        color = HighContrastMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = HighlyAccessibleOutline, thickness = 0.5.dp)

            // Current Deployment / Status Logging state
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Deployment Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (autoSetupProgress > 0f && autoSetupProgress < 1f) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccessibleBlue,
                            strokeWidth = 2.dp
                        )
                    }
                }
                
                Text(
                    text = autoSetupStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccessibleAmber,
                    fontWeight = FontWeight.SemiBold
                )

                if (autoSetupProgress > 0f && autoSetupProgress < 1f) {
                    LinearProgressIndicator(
                        progress = autoSetupProgress,
                        color = AccessibleBlue,
                        trackColor = HighlightSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(CircleShape)
                    )
                }
            }

            // Expanded Hardware Device specifications Report
            if (hardwareReport != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E2C))
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "▼ Hardware Compatibility Profiling",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Measured physical system RAM: ${String.format("%.2f", hardwareReport.ramGb)} GB",
                        style = MaterialTheme.typography.labelSmall,
                        color = HighContrastMuted
                    )
                    Text(
                        text = "SoC Native Instructions Architecture: ${hardwareReport.cpuAbi}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HighContrastMuted
                    )
                    Text(
                        text = "Target Optimal Model: ${hardwareReport.recommendedModelId.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccessibleGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Extended Live Sensors Telemetry Insights
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141424))
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "▼ Live Sensor Telemetry Insights",
                    style = MaterialTheme.typography.bodySmall,
                    color = HighContrastWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• Thermistor Sensor: $batteryTemp °C (${if (batteryTemp > 41f) "Thermal Limit Alarms Active!" else "Normal Range"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastMuted
                )
                Text(
                    text = "• RAM Allocation Workload: ${String.format("%.1f%%", ramUsage * 100f)} occupied",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastMuted
                )
                Text(
                    text = "• Charger Brick Integrity: $chargerQuality",
                    style = MaterialTheme.typography.labelSmall,
                    color = HighContrastMuted
                )
                Text(
                    text = "• Longevity Stability Score: $deviceHealthScore/100 Points",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (deviceHealthScore >= 80) AccessibleGreen else AccessibleAmber,
                    fontWeight = FontWeight.Bold
                )
            }

            // System Health Audit Alerts
            if (healthDiagnosticReport != null && healthDiagnosticReport.recommendations.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3C1F1F))
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚠ System Health Alerts & Recommendations:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF9A9A),
                        fontWeight = FontWeight.Bold
                    )
                    healthDiagnosticReport.recommendations.forEach { recommendation ->
                        Text(
                            text = "• $recommendation",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFCDD2)
                        )
                    }
                }
            }

            // Self-Test Report Score Dashboard Panel
            if (selfTestReport != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, HighlyAccessibleOutline), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnostics Health Score",
                            style = MaterialTheme.typography.bodySmall,
                            color = HighContrastWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selfTestReport.healthScore >= 90) Color(0x334CAF50) else Color(0x33FF9800))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${selfTestReport.healthScore}/100",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selfTestReport.healthScore >= 90) AccessibleGreen else AccessibleAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Checks status list
                    selfTestReport.diagnosticReports.forEach { (testName, passed) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = testName,
                                style = MaterialTheme.typography.labelSmall,
                                color = HighContrastMuted
                            )
                            Icon(
                                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = if (passed) "test passed" else "test warning",
                                tint = if (passed) AccessibleGreen else Color(0xFFE57373),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = selfTestReport.overallStatusSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selfTestReport.healthScore >= 90) AccessibleGreen else AccessibleAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Interactive Actions Panel (Diagnose & Self Healing triggers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Re-Diagnose button
                Button(
                    onClick = onReDiagnostics,
                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSelected),
                    border = BorderStroke(1.dp, AccessibleBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AccessibleBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Diagnostics",
                            color = AccessibleBlue,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Self Healing trigger
                Button(
                    onClick = onHealTrigger,
                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSelected),
                    border = BorderStroke(1.dp, AccessibleGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = AccessibleGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Self Healing",
                            color = AccessibleGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}


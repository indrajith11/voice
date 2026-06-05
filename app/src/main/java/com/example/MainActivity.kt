package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.ui.VoiceScreen
import com.example.ui.VoiceViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.AccessibleBlue
import com.example.ui.theme.HighContrastWhite
import com.example.ui.theme.HighContrastMuted

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Let content render full edge to edge under system transparent status/navigation bars
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val app = application as VoiceAssistantApplication
                    val factory = remember { VoiceViewModel.Factory(applicationContext, app.repository) }
                    val viewModel: VoiceViewModel = viewModel(factory = factory)

                    val context = LocalContext.current
                    val sharedPrefs = remember { context.getSharedPreferences("vision_voice_prefs", android.content.Context.MODE_PRIVATE) }
                    var onboardingFinished by remember {
                        mutableStateOf(sharedPrefs.getBoolean("onboarding_done_v1", false))
                    }
                    var forceTriggerSetup by remember { mutableStateOf(false) }

                    // Catch and handle setup wizard re-triggers
                    LaunchedEffect(intent) {
                        if (intent?.getBooleanExtra("trigger_setup", false) == true) {
                            forceTriggerSetup = true
                        }
                    }

                    if (!onboardingFinished || forceTriggerSetup) {
                        com.example.ui.VisionVoiceSetupWizard(
                            onFinished = {
                                onboardingFinished = true
                                forceTriggerSetup = false
                                sharedPrefs.edit().putBoolean("onboarding_done_v1", true).apply()
                                viewModel.startVoiceAssistantService()
                            }
                        )
                    } else {
                        VoiceScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = AccessibleBlue,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Microphone Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastWhite,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "VisionVoice is an active voice assistant designed for blind and visually impaired users. " +
                            "We need microphone permission to hear the wake word 'Hey Vision' and receive your speech queries offline and online. " +
                            "No voice data is stored or processed outside of your secure assistant interactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(28.dp))
                
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccessibleBlue,
                        contentColor = HighContrastWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Grant Permission",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

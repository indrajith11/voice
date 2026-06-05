# VisionVoice — System Architecture & Documentation

VisionVoice is a production-grade, voice-first Android assistant designed to empower blind and visually impaired users. Built on modern Android architectural guidelines, it combines background continuous microphone listening, offline voice command routing, deep conversational AI via the Gemini API, and an accessibility-first touch-and-feedback user interface.

---

## 🚀 Key Achievements & Feature Implementation

### 1. Robust Continuous Microphone Service (Foreground Service Architecture)
- Designed around a dedicated, started foreground service (`VoiceAssistantService.kt`) with `foregroundServiceType="microphone"`, strictly compliant with Android 14 and Android 15.
- Employs a continuous wakelock (`PowerManager.PARTIAL_WAKE_LOCK`) and handles task restart strategies (`START_STICKY`) to remain running in the background even if the screen turns off or the system faces memory density optimization.
- Binds a persistent, spoken Android notification channel with screen readers (TalkBack) so context remains accessible at the OS tray.

### 2. Double-Pulse Wake Word Detection ("Hey Vision")
- Features sound-wave filtering inside a passive listener loop (`PASSIVE_WAKING` state).
- When the phrase `"Hey Vision"` is detected, the service registers a fast transition:
  - Generates a tactile double-pulse physical vibration using the native `Vibrator` subsystem.
  - Plays an immediate high-frequency tone chirp via `ToneGenerator` to signal that the active speech capture frame is open.
- Auto-restarts the recording session into `LISTENING_ACTIVE` without requiring button presses.

### 3. Speech-to-Text & Text-to-Speech Cohesion
- **Speech-to-Text (`SpeechToTextEngine.kt`)**: Interfaces with Android's `SpeechRecognizer` with automatic silence-timeout restarts. Relays live dB volume changes to drive smooth fluid screen waveforms.
- **Text-to-Speech (`TextToSpeechEngine.kt`)**: Wraps native synthesis with built-in pitch/speed properties, fallback locales, and an `UtteranceProgressListener`.
- **Active Dialogue Loop**: The engines are strictly coupled. When the assistant speaks responses, STT is temporarily disabled to prevent the microphone from "hearing itself". Once TTS completes speaking, the passive wake-word detection is automatically reintroduced, establishing an elegant hands-free chat experience.

### 4. Dual-Mode Voice Command Parser & Offline Support
Spoken phrases are sent to `VoiceCommandRouter.kt` first to prioritize local execution without requiring internet access:
1. **Time Command**: *"What time is it?"* $\rightarrow$ Reads current localized hours.
2. **Date Command**: *"What today's date?"* $\rightarrow$ Speaks day of the week.
3. **Battery Status**: *"What's my battery level?"* $\rightarrow$ Informs battery percentage remaining.
4. **Voice Note Creation**: *"Save note buy groceries"* $\rightarrow$ Creates and appends note to Room DB.
5. **Notes Reading**: *"Read notes"* $\rightarrow$ Reads aloud all saved text logs.
6. **Erase Notes**: *"Erase notes"* $\rightarrow$ Truncates saved logs.
7. **System Status**: *"Status checklist"* $\rightarrow$ Announces remaining power, online connection, and count of saved voice logs.
- **AI Fallback**: If no offline command patterns are matched, the router delegates the spoken utterance to our `GeminiManager` via standard REST API (`gemini-3.5-flash`) for multi-turn Q&A conversations.

### 5. Secure Persistent Room Storage
- Designed using **Room Persistent Library** with modern Kotlin DSL KSP configuration.
- Saves:
  - **`notes` Table**: Voice notes generated via command router.
  - **`chat_history` Table**: Logs the chat transcript lines (both `user` and `model` roles) so that the Gemini API maintains context memory across conversational turns.

### 6. Accessibility-First Jetpack Compose User Interface (`VoiceScreen.kt`)
- **Space Slate Theme**: A deep high-contrast dark visual theme designed to conserve battery on OLED screens and minimize screen glare.
- **Giant Tactile Orb**: A massive, circular button in the center of the viewport that acts as a fallback gesture point. A simple screen tap instantly interrupts background reading or sleeping states and activates speech-command listening.
- **Living Soundwaves Waveform**: Driven in real-time by incoming microphone RMS decibel volume levels, rendering a dynamic wave animation.
- **Battery Saver Warning Alerts**: Proactively monitors if background battery restrictions are active (which can terminate background wake-word systems), offering a direct prompt to ignore optimizations.

---

## 📂 Codebase File Structure

```text
/app/src/main/
├── AndroidManifest.xml                        # Configures permissions & Foreground type
├── java/com/example/
│   ├── VoiceAssistantApplication.kt           # Custom Application; hosts persistent database & repo
│   ├── MainActivity.kt                        # Edge-to-edge frame; handles runtime permissions onboarding
│   ├── ai/
│   │   └── GeminiClient.kt                    # Gemini REST API service client, OkHttp, and conversational manager
│   ├── data/
│   │   ├── Entities.kt                        # Room database Entities (Note, ChatMessage)
│   │   ├── NoteDao.kt                         # DAO for saving and deleting note entities
│   │   ├── ChatMessageDao.kt                  # DAO for keeping multi-turn conversation logs
│   │   ├── VoiceDatabase.kt                   # Room database holder singleton
│   │   └── VoiceRepository.kt                 # Repositories abstract layer
│   ├── services/
│   │   └── VoiceAssistantService.kt           # Core background continuous listener (Foreground service)
│   ├── stt/
│   │   ├── SpeechToTextEngine.kt              # Native SpeechRecognizer wrapper and listeners
│   │   └── VoiceCommandRouter.kt              # Command parser (Time, Battery, Note logger, clear)
│   ├── tts/
│   │   └── TextToSpeechEngine.kt              # Native TextToSpeech synthesizer and listener events
│   └── ui/
│       ├── VoiceViewModel.kt                  # Binder ViewModel; manages state flows, mic volumes, DB entries
│       ├── VoiceScreen.kt                     # High-contrast accessible single screen layout; wavy waveform drawing
│       └── theme/
│           ├── Color.kt                       # Accessible AAA contrast colors (AccessibleBlue, AccessibleGreen)
│           ├── Theme.kt                       # Material 3 Dark theme mapping
│           └── Type.kt                        # Typography specifications
```

---

## 🛠️ Build and Testing Guides

### Running Tests
All unit tests have been updated to reflect the new accessibility configurations. Run standard local JVM tests using:
```bash
gradle :app:testDebugUnitTest
```

- **ExampleRobolectricTest**: Verifies that the context and string resources resolve `"VisionVoice"` correctly.
- **GreetingScreenshotTest**: Compiles and renders a snapshot verification of our high-contrast permission onboarding screen.

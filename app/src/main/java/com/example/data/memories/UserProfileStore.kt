package com.example.data.memories

import com.example.data.UserProfile
import com.example.data.VoiceRepository
import kotlinx.coroutines.flow.Flow

class UserProfileStore(private val repository: VoiceRepository) {

    val activeProfileFlow: Flow<UserProfile?> = repository.userProfileFlow

    suspend fun getProfile(): UserProfile {
        return repository.getUserProfile()
    }

    suspend fun updateSpecs(username: String, speechRate: Float, speechPitch: Float, useBiometrics: Boolean, wakeWord: Boolean) {
        val current = getProfile()
        val updated = current.copy(
            username = username,
            speechRate = speechRate,
            speechPitch = speechPitch,
            useBiometrics = useBiometrics,
            wakeWordEnabled = wakeWord
        )
        repository.saveUserProfile(updated)
    }
}

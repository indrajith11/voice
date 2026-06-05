package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_history")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Single active user profile
    val username: String = "Companion User",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val useBiometrics: Boolean = false,
    val wakeWordEnabled: Boolean = true
)

@Entity(tableName = "memories")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "note", "person", "place", "location", "schedule", "task", "reminder"
    val encryptedTitle: String, // AES/GCM encrypted base64 payload
    val encryptedContent: String, // AES/GCM encrypted base64 payload
    val timestamp: Long = System.currentTimeMillis(),
    val dateTag: String, // "YYYY-MM-DD" formatted for quick logical querying
    val keywords: String = "", // Comma-separated tags for retrieval matching
    val isCompleted: Boolean = false
)

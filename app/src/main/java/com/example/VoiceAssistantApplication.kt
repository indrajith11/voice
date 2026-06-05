package com.example

import android.app.Application
import com.example.data.VoiceDatabase
import com.example.data.VoiceRepository

class VoiceAssistantApplication : Application() {

    // Global properties representing our modular manual dependency graph
    lateinit var database: VoiceDatabase
        private set

    lateinit var repository: VoiceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize our persistent offline database and repository layer
        database = VoiceDatabase.getDatabase(this)
        repository = VoiceRepository(
            database.noteDao(),
            database.chatMessageDao(),
            database.userProfileDao(),
            database.memoryDao()
        )
    }
}

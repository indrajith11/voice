package com.example.data.memories

import com.example.data.MemoryItem
import com.example.data.VoiceRepository

/**
 * Clean architectural store for managing local encrypted reminders and calendars.
 */
class ReminderStore(private val repository: VoiceRepository) {

    suspend fun scheduleReminder(reminderText: String): Long {
        return repository.saveEncryptedMemory(
            type = "reminder",
            title = "Reminder - " + reminderText.take(15),
            content = reminderText,
            keywords = "reminder,schedule,alarm"
        )
    }

    suspend fun getActiveReminders(): List<MemoryItem> {
        return repository.getDecryptedMemoriesByType("reminder")
    }

    suspend fun getAgendaByDate(dateTag: String): List<MemoryItem> {
        val reminders = repository.getDecryptedMemoriesByDate(dateTag)
        return reminders.filter { it.type == "reminder" || it.type == "schedule" }
    }

    suspend fun removeReminder(item: MemoryItem) {
        repository.deleteMemory(item)
    }
}

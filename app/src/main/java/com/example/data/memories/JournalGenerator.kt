package com.example.data.memories

import com.example.data.MemoryItem
import com.example.data.VoiceRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JournalGenerator(private val repository: VoiceRepository) {

    /**
     * Aggregates memories created on a given date tag to compile a local structured journal.
     */
    suspend fun compileDailyJournal(dateTag: String): String {
        val lists = repository.getDecryptedMemoriesByDate(dateTag)
        if (lists.isEmpty()) {
            return "No offline notes, events, or reminders found for date: $dateTag."
        }

        val parsedDate = try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.parse(dateTag) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        val displayDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(parsedDate)
        val sb = StringBuilder("Daily Voice Journal entry for $displayDate:\n")
        sb.append("You added ${lists.size} offline secure logs.\n\n")

        val grouped = lists.groupBy { it.type }
        grouped.forEach { (type, items) ->
            sb.append("[${type.uppercase()}]\n")
            items.forEachIndexed { index, item ->
                sb.append("• ${item.encryptedContent}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

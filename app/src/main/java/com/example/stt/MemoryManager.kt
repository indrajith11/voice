package com.example.stt

import com.example.data.MemoryItem
import com.example.data.VoiceRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MemoryManager(private val repository: VoiceRepository) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Parse and extract a semantic memory statement from natural speech.
     * e.g., "Remember I parked on level 3", "Remember my appointment on Monday"
     */
    suspend fun saveSpeechToMemory(text: String): String {
        val cleanSpeech = text.replace(Regex("(?i)^(\\s*remember\\s*that|\\s*remember\\s*my|\\s*remember\\s*)"), "").trim()
        if (cleanSpeech.isEmpty()) {
            return "I heard you say remember, but I couldn't extract any specific content. Try saying: Remember my doctor's appointment tomorrow."
        }

        // 1. Semantic classification based on keywords
        val type = determineMemoryType(cleanSpeech)
        val title = generateSmartTitle(cleanSpeech, type)
        val keywords = extractKeywords(cleanSpeech)

        // 2. Persist to database encrypted under GCM
        val memoryId = repository.saveEncryptedMemory(
            type = type,
            title = title,
            content = cleanSpeech,
            keywords = keywords
        )

        return when (type) {
            "location" -> "Saved location. I'll remember where that is. Stored as: $cleanSpeech"
            "reminder", "schedule" -> "Reminder scheduled. I've noted down: $cleanSpeech"
            "contact" -> "Contact entry stored. I have secured: $cleanSpeech"
            "task" -> "Task appended to your list. Stored as: $cleanSpeech"
            else -> "Memory captured and secured offline. I've stored: $cleanSpeech"
        }
    }

    /**
     * Offline local semantic matching (Simulates Local Vector DB indexing).
     * Tokenizes queries, filters stopwords, ranks matching items based on co-occurrence density.
     */
    suspend fun queryMemoryLocal(query: String): String {
        val cleanQuery = query.lowercase()
        val memories = repository.getAllDecryptedMemories()

        if (memories.isEmpty()) {
            return "Your local memory storage is currently empty. Tell me to remember something first."
        }

        // Special patterns: dates (today, yesterday, tomorrow)
        if (cleanQuery.contains("today") || cleanQuery.contains("tasks today") || cleanQuery.contains("schedule today")) {
            val todayStr = dateFormat.format(Date())
            val todayMemories = memories.filter { it.dateTag == todayStr }
            return formatMemoryCollection("today's logs", todayMemories)
        }
        if (cleanQuery.contains("yesterday")) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayStr = dateFormat.format(cal.time)
            val yesterdayMemories = memories.filter { it.dateTag == yesterdayStr }
            return formatMemoryCollection("yesterday's logs", yesterdayMemories)
        }

        // Extract search tokens (exclude short stopwords)
        val queryTokens = extractSearchTokens(cleanQuery)
        if (queryTokens.isEmpty()) {
            // General query fallback: read most recent memories
            val recent = memories.take(3)
            return "Here are your 3 most recent memories:\n" + recent.joinToString("\n") { "- " + it.encryptedContent }
        }

        // Score models based on token occurrences
        val scoredMemories = memories.map { memory ->
            val titleMatches = queryTokens.count { memory.encryptedTitle.lowercase().contains(it) } * 3
            val contentMatches = queryTokens.count { memory.encryptedContent.lowercase().contains(it) } * 2
            val keywordMatches = queryTokens.count { memory.keywords.lowercase().contains(it) } * 2
            val typeMatches = if (cleanQuery.contains(memory.type)) 1 else 0

            val totalScore = titleMatches + contentMatches + keywordMatches + typeMatches
            Pair(memory, totalScore)
        }.filter { it.second > 0 }
         .sortedByDescending { it.second }

        if (scoredMemories.isEmpty()) {
            return "I couldn't recall anything locally matching '$query'. Say 'Erase memories' if you'd like to wipe or clear your slate."
        }

        val match = scoredMemories.first().first
        val score = scoredMemories.first().second

        return if (score >= 3) {
            "From your memories, I recall: ${match.encryptedContent}."
        } else {
            "I found a close memory regarding that: ${match.encryptedContent}."
        }
    }

    /**
     * Synthesize all memory items created today into a concise morning/daily journal.
     */
    suspend fun generateDailyJournal(): String {
        val todayStr = dateFormat.format(Date())
        val memories = repository.getDecryptedMemoriesByDate(todayStr)

        if (memories.isEmpty()) {
            return "You haven't logged any personal notes or memories today. Try speaking to me hands-free!"
        }

        val builder = java.lang.StringBuilder("Here is your Daily Memory Journal for today, ")
        builder.append(SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())).append(". ")
        builder.append("You captured ${memories.size} logs.\n")

        memories.groupBy { it.type }.forEach { (type, items) ->
            builder.append("Under ${type.uppercase()}: ")
            items.forEachIndexed { idx, item ->
                builder.append("${idx + 1}. ${item.encryptedContent}. ")
            }
        }

        return builder.toString()
    }

    /**
     * Format a collection of memory logs into text for TTS voice readout.
     */
    private fun formatMemoryCollection(label: String, items: List<MemoryItem>): String {
        if (items.isEmpty()) {
            return "I found no records stored under $label."
        }
        val sb = java.lang.StringBuilder("Regarding $label, I found ${items.size} logs:\n")
        items.forEachIndexed { index, item ->
            sb.append("${index + 1}: ${item.encryptedContent}. ")
        }
        return sb.toString()
    }

    /**
     * Determine memory category.
     */
    private fun determineMemoryType(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("parked") || lower.contains("parking") || lower.contains("location") || lower.contains("address") -> "location"
            lower.contains("appointment") || lower.contains("schedule") || lower.contains("meeting") || lower.contains("doctor") -> "schedule"
            lower.contains("remind") || lower.contains("remember to") || lower.contains("alarm") -> "reminder"
            lower.contains("phone") || lower.contains("email") || lower.contains("contact") || lower.contains("address book") -> "contact"
            lower.contains("todo") || lower.contains("task") || lower.contains("buy") || lower.contains("groceries") -> "task"
            else -> "note"
        }
    }

    /**
     * Generate a short title tag for the item.
     */
    private fun generateSmartTitle(text: String, type: String): String {
        val trimmed = text.trim()
        val words = trimmed.split(" ")
        return if (words.size <= 4) trimmed else words.take(4).joinToString(" ") + "..."
    }

    /**
     * Extract comma-separated keywords for local indexing.
     */
    private fun extractKeywords(text: String): String {
        val tokens = extractSearchTokens(text)
        return tokens.joinToString(",")
    }

    /**
     * Extract clean search tokens from queries/sentences.
     */
    private fun extractSearchTokens(input: String): List<String> {
        val stopwords = setOf(
            "where", "did", "my", "is", "of", "the", "a", "an", "to", "at", "remember",
            "i", "what", "how", "am", "are", "do", "does", "have", "has", "today", "yesterday",
            "tomorrow", "find", "search", "get", "recall", "list", "and", "in", "on", "for", "with"
        )
        return input.replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .lowercase()
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 2 && !stopwords.contains(it) }
    }
}

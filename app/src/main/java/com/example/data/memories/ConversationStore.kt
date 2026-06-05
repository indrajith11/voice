package com.example.data.memories

import com.example.data.ChatMessage
import com.example.data.VoiceRepository
import kotlinx.coroutines.flow.Flow

/**
 * Clean architectural abstraction for conversational logs.
 */
class ConversationStore(private val repository: VoiceRepository) {

    val chatHistoryFlow: Flow<List<ChatMessage>> = repository.chatHistoryFlow

    suspend fun saveUserIntent(content: String): Long {
        return repository.saveUserMessage(content)
    }

    suspend fun saveAssistantResponse(content: String): Long {
        return repository.saveAssistantMessage(content)
    }

    suspend fun loadRecentDialogContext(turns: Int): List<ChatMessage> {
        return repository.getRecentHistoryMessages(turns)
    }

    suspend fun wipeFullLog() {
        repository.clearHistory()
    }
}

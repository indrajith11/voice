package com.example.data

import com.example.security.SecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceRepository(
    private val noteDao: NoteDao,
    private val chatMessageDao: ChatMessageDao,
    private val userProfileDao: UserProfileDao,
    private val memoryDao: MemoryDao,
    val securityManager: SecurityManager = SecurityManager()
) {

    // --- Notes operations ---
    val allNotesFlow: Flow<List<Note>> = noteDao.getAllNotesFlow()

    suspend fun getAllNotes(): List<Note> {
        return noteDao.getAllNotes()
    }

    suspend fun addNote(content: String): Long {
        return noteDao.insertNote(Note(content = content))
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun clearAllNotes() {
        noteDao.clearAllNotes()
    }

    // --- ChatHistory operations ---
    val chatHistoryFlow: Flow<List<ChatMessage>> = chatMessageDao.getChatHistoryFlow()

    suspend fun getRecentHistoryMessages(limit: Int): List<ChatMessage> {
        return chatMessageDao.getRecentChatHistory(limit)
    }

    suspend fun saveUserMessage(message: String): Long {
        return chatMessageDao.insertMessage(ChatMessage(role = "user", message = message))
    }

    suspend fun saveAssistantMessage(message: String): Long {
        return chatMessageDao.insertMessage(ChatMessage(role = "model", message = message))
    }

    suspend fun clearHistory() {
        chatMessageDao.clearChatHistory()
    }

    // --- User Profile operations ---
    val userProfileFlow: Flow<UserProfile?> = userProfileDao.getUserProfileFlow()

    suspend fun getUserProfile(): UserProfile {
        return userProfileDao.getUserProfile() ?: UserProfile().also {
            userProfileDao.saveUserProfile(it)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.saveUserProfile(profile)
    }

    // --- Memory Item Encrypted Operations ---
    // Return decrypted memory items in flow
    val allDecryptedMemoriesFlow: Flow<List<MemoryItem>> = memoryDao.getAllMemoriesFlow().map { list ->
        list.map { decryptMemoryItem(it) }
    }

    suspend fun getAllDecryptedMemories(): List<MemoryItem> {
        return memoryDao.getAllMemories().map { decryptMemoryItem(it) }
    }

    suspend fun getDecryptedMemoriesByType(type: String): List<MemoryItem> {
        return memoryDao.getMemoriesByType(type).map { decryptMemoryItem(it) }
    }

    suspend fun getDecryptedMemoriesByDate(dateTag: String): List<MemoryItem> {
        return memoryDao.getMemoriesByDate(dateTag).map { decryptMemoryItem(it) }
    }

    suspend fun saveEncryptedMemory(type: String, title: String, content: String, keywords: String = ""): Long {
        val dateTag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val encryptedTitle = securityManager.encrypt(title)
        val encryptedContent = securityManager.encrypt(content)
        val memoryItem = MemoryItem(
            type = type,
            encryptedTitle = encryptedTitle,
            encryptedContent = encryptedContent,
            dateTag = dateTag,
            keywords = keywords
        )
        return memoryDao.insertMemory(memoryItem)
    }

    suspend fun deleteMemory(memoryItem: MemoryItem) {
        memoryDao.deleteMemory(memoryItem)
    }

    suspend fun clearAllMemories() {
        memoryDao.clearAllMemories()
    }

    private fun decryptMemoryItem(item: MemoryItem): MemoryItem {
        return item.copy(
            encryptedTitle = securityManager.decrypt(item.encryptedTitle),
            encryptedContent = securityManager.decrypt(item.encryptedContent)
        )
    }
}

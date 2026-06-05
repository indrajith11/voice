package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getAllMemories(): List<MemoryItem>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY timestamp DESC")
    fun getMemoriesByTypeFlow(type: String): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getMemoriesByType(type: String): List<MemoryItem>

    @Query("SELECT * FROM memories WHERE dateTag = :dateTag ORDER BY timestamp DESC")
    suspend fun getMemoriesByDate(dateTag: String): List<MemoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryItem): Long

    @Delete
    suspend fun deleteMemory(memory: MemoryItem)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

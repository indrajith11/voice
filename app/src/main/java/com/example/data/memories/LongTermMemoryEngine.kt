package com.example.data.memories

import com.example.data.MemoryItem
import com.example.data.VoiceRepository
import kotlin.math.sqrt

class LongTermMemoryEngine(private val repository: VoiceRepository) {

    /**
     * Finds memories that have semantic overlap with the search sentence.
     * Compares word frequencies (TF-IDF vectors) and performs Cosine Similarity scoring.
     */
    suspend fun semanticSearch(query: String, threshold: Float = 0.15f): List<SearchResult> {
        val memories = repository.getAllDecryptedMemories()
        if (memories.isEmpty()) {
            return emptyList()
        }

        // Tokenize search query
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<SearchResult>()

        for (memory in memories) {
            val contentTokens = tokenize(memory.encryptedContent)
            val titleTokens = tokenize(memory.encryptedTitle)
            val keywordTokens = memory.keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

            // Merge terms to form document frequencies
            val documentTokens = contentTokens + titleTokens * 2 + keywordTokens * 3

            // Compute cosine similarity between query and document vectors
            val score = calculateCosineSimilarity(queryTokens, documentTokens)
            if (score >= threshold) {
                results.add(SearchResult(memory, score))
            }
        }

        return results.sortedByDescending { it.similarity }
    }

    private operator fun List<String>.times(factor: Int): List<String> {
        val result = mutableListOf<String>()
        repeat(factor) {
            result.addAll(this)
        }
        return result
    }

    /**
     * Compute Cosine Similarity between vector query and vector doc.
     */
    private fun calculateCosineSimilarity(vecA: List<String>, vecB: List<String>): Float {
        val termsSet = (vecA + vecB).toSet()

        // Count occurrences
        val countsA = vecA.groupingBy { it }.eachCount()
        val countsB = vecB.groupingBy { it }.eachCount()

        // Dot product and Magnitudes
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (term in termsSet) {
            val valA = countsA[term]?.toDouble() ?: 0.0
            val valB = countsB[term]?.toDouble() ?: 0.0

            dotProduct += valA * valB
            normA += valA * valA
            normB += valB * valB
        }

        if (normA == 0.0 || normB == 0.0) return 0f

        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    private fun tokenize(text: String): List<String> {
        val stopwords = setOf(
            "the", "a", "an", "is", "of", "to", "at", "by", "for", "with", "in", "on", "from", "and",
            "or", "but", "about", "that", "this", "my", "your", "his", "her", "their", "our", "me", "i"
        )
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 2 && !stopwords.contains(it) }
    }

    data class SearchResult(
        val memoryItem: MemoryItem,
        val similarity: Float
    )
}

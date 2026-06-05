package com.example.ai

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null, // "user" or "model"
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Moshi configuration with Kotlin representation adapter
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

class GeminiManager {
    suspend fun generateResponse(
        prompt: String,
        history: List<com.example.data.ChatMessage> = emptyList()
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Hello. The Gemini API key is not configured yet. Please configure it in the Secrets panel in AI Studio."
        }

        val requestContents = mutableListOf<Content>()
        
        // Add chat history context
        history.forEach { msg ->
            val role = if (msg.role == "user") "user" else "model"
            requestContents.add(
                Content(
                    role = role,
                    parts = listOf(Part(text = msg.message))
                )
            )
        }
        
        // Add the current prompt
        requestContents.add(
            Content(
                role = "user",
                parts = listOf(Part(text = prompt))
            )
        )

        val systemPrompt = "You are VisionVoice, a voice-first assistant designed for blind and visually impaired users. " +
                "Respond in clear, short, highly conversational sentences. Speak descriptions vividly but keep them concise " +
                "to make reading them out sound natural. Avoid markdown formatting, bullet points, asterisks, and long tables."

        val systemInstruction = Content(
            role = "system",
            parts = listOf(Part(text = systemPrompt))
        )

        val requestBody = GeminiRequest(
            contents = requestContents,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(temperature = 0.7f, maxOutputTokens = 250)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I apologize, but I didn't catch that. Could you repeat?"
        } catch (e: Exception) {
            e.printStackTrace()
            "I ran into some communication issues. please check your internet connection."
        }
    }
}

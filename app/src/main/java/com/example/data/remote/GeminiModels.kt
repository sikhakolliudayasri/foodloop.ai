package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @param:Json(name = "contents") val contents: List<GeminiContent>,
    @param:Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @param:Json(name = "parts") val parts: List<GeminiPart>,
    @param:Json(name = "role") val role: String? = "user"
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @param:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @param:Json(name = "temperature") val temperature: Float? = 0.4f,
    @param:Json(name = "topP") val topP: Float? = 0.9f
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @param:Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @param:Json(name = "content") val content: GeminiContent? = null
)

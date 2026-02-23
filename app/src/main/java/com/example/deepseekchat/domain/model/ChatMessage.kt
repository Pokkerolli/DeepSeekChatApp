package com.example.deepseekchat.domain.model

data class ChatMessage(
    val id: Long,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long
)

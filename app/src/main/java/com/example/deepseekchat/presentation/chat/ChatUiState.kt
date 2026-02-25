package com.example.deepseekchat.presentation.chat

import com.example.deepseekchat.domain.model.MessageRole

data class ChatSessionUi(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val systemPrompt: String?
)

data class ChatMessageUi(
    val stableId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false
)

data class ChatUiState(
    val sessions: List<ChatSessionUi> = emptyList(),
    val activeSessionId: String? = null,
    val activeSessionTitle: String = "New chat",
    val activeSessionSystemPrompt: String? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String? = null
)

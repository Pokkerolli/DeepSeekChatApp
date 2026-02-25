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
    val isStreaming: Boolean = false,
    val userTokens: Int? = null,
    val userCacheHitTokens: Int? = null,
    val userCacheMissTokens: Int? = null,
    val inputCostCacheHitUsd: Double? = null,
    val inputCostCacheMissUsd: Double? = null,
    val assistantTokens: Int? = null,
    val requestTotalTokens: Int? = null,
    val outputCostUsd: Double? = null,
    val requestTotalCostUsd: Double? = null
)

data class ConversationUsageUi(
    val contextLength: Int = 0,
    val cumulativeTotalCostUsd: Double = 0.0
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
    val errorMessage: String? = null,
    val usage: ConversationUsageUi = ConversationUsageUi()
)

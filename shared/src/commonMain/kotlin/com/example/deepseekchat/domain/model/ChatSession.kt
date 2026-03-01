package com.example.deepseekchat.domain.model

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val systemPrompt: String?,
    val contextCompressionEnabled: Boolean,
    val contextSummary: String?,
    val summarizedMessagesCount: Int,
    val isContextSummarizationInProgress: Boolean
)

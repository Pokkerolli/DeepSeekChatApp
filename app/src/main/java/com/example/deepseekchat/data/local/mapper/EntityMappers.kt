package com.example.deepseekchat.data.local.mapper

import com.example.deepseekchat.data.local.entity.MessageEntity
import com.example.deepseekchat.data.local.entity.SessionEntity
import com.example.deepseekchat.data.local.entity.UserProfilePresetEntity
import com.example.deepseekchat.domain.model.ChatMessage
import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.model.ContextWindowMode
import com.example.deepseekchat.domain.model.MessageRole
import com.example.deepseekchat.domain.model.UserProfilePreset

fun SessionEntity.toDomain(): ChatSession {
    return ChatSession(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        systemPrompt = systemPrompt,
        userProfileName = userProfileName,
        contextWindowMode = ContextWindowMode.fromStored(contextWindowMode),
        isStickyFactsExtractionInProgress = isStickyFactsExtractionInProgress,
        contextSummary = contextSummary,
        summarizedMessagesCount = summarizedMessagesCount,
        isContextSummarizationInProgress = isContextSummarizationInProgress
    )
}

fun MessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        sessionId = sessionId,
        role = MessageRole.fromStored(role),
        content = content,
        timestamp = createdAt,
        promptTokens = promptTokens,
        promptCacheHitTokens = promptCacheHitTokens,
        promptCacheMissTokens = promptCacheMissTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens
    )
}

fun UserProfilePresetEntity.toDomain(): UserProfilePreset {
    return UserProfilePreset(
        profileName = profileName,
        label = label,
        payloadJson = payloadJson,
        isBuiltIn = false
    )
}

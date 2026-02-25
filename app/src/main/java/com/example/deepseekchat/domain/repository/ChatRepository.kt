package com.example.deepseekchat.domain.repository

import com.example.deepseekchat.domain.model.ChatMessage
import com.example.deepseekchat.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun createSession(): ChatSession
    suspend fun setActiveSession(sessionId: String)
    suspend fun setSessionSystemPrompt(sessionId: String, systemPrompt: String?)
    fun observeActiveSessionId(): Flow<String?>
    fun sendMessageStreaming(sessionId: String, content: String): Flow<String>
}

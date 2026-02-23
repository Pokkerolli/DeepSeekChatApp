package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class SendMessageUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(sessionId: String, content: String): Flow<String> {
        return repository.sendMessageStreaming(sessionId, content)
    }
}

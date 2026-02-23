package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.ChatMessage
import com.example.deepseekchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveMessagesUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(sessionId: String): Flow<List<ChatMessage>> {
        return repository.observeMessages(sessionId)
    }
}

package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveSessionsUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(): Flow<List<ChatSession>> {
        return repository.observeSessions()
    }
}

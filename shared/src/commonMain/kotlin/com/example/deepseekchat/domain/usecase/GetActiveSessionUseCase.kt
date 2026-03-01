package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetActiveSessionUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(): Flow<String?> {
        return repository.observeActiveSessionId()
    }
}

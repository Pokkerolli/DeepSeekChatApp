package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository

class DeleteSessionUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String) {
        repository.deleteSession(sessionId)
    }
}

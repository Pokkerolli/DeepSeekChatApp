package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository

class SetSessionContextCompressionEnabledUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String, enabled: Boolean) {
        repository.setSessionContextCompressionEnabled(sessionId, enabled)
    }
}

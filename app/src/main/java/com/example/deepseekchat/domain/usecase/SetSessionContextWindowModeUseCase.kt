package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.ContextWindowMode
import com.example.deepseekchat.domain.repository.ChatRepository

class SetSessionContextWindowModeUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String, mode: ContextWindowMode) {
        repository.setSessionContextWindowMode(sessionId, mode)
    }
}

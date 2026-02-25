package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository

class SetSessionSystemPromptUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String, systemPrompt: String?) {
        repository.setSessionSystemPrompt(sessionId, systemPrompt)
    }
}

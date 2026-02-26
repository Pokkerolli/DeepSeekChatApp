package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository

class RunContextSummarizationIfNeededUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String) {
        repository.runContextSummarizationIfNeeded(sessionId)
    }
}

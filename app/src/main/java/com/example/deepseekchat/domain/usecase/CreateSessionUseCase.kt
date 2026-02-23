package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.repository.ChatRepository

class CreateSessionUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(): ChatSession {
        return repository.createSession()
    }
}

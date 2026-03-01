package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.repository.ChatRepository

class CreateSessionBranchUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        sourceSessionId: String,
        upToMessageIdInclusive: Long
    ): ChatSession {
        return repository.createSessionBranch(
            sourceSessionId = sourceSessionId,
            upToMessageIdInclusive = upToMessageIdInclusive
        )
    }
}

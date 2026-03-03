package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.UserProfileBuilderMessage
import com.example.deepseekchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class StreamUserProfileBuilderReplyUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(
        history: List<UserProfileBuilderMessage>,
        userMessage: String?
    ): Flow<String> {
        return repository.streamUserProfileBuilderReply(
            history = history,
            userMessage = userMessage
        )
    }
}

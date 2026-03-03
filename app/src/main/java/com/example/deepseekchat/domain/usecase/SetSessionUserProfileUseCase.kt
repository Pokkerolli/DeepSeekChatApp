package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.repository.ChatRepository

class SetSessionUserProfileUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String, userProfileName: String?) {
        repository.setSessionUserProfile(sessionId, userProfileName)
    }
}

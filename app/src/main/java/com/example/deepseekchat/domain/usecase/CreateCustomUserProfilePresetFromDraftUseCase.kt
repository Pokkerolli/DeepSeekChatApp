package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.UserProfilePreset
import com.example.deepseekchat.domain.repository.ChatRepository

class CreateCustomUserProfilePresetFromDraftUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(rawDraft: String): UserProfilePreset {
        return repository.createCustomUserProfilePresetFromDraft(rawDraft)
    }
}

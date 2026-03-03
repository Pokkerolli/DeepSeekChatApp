package com.example.deepseekchat.domain.usecase

import com.example.deepseekchat.domain.model.UserProfilePreset
import com.example.deepseekchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveUserProfilePresetsUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(): Flow<List<UserProfilePreset>> {
        return repository.observeUserProfilePresets()
    }
}

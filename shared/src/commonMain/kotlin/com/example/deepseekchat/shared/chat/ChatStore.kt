package com.example.deepseekchat.shared.chat

import com.example.deepseekchat.shared.platformName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ChatMessage(
    val id: Long,
    val author: String,
    val text: String
)

data class ChatUiState(
    val input: String = "",
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            id = 1L,
            author = "assistant",
            text = "DeepSeek KMP запущен на ${platformName()}"
        )
    )
)

class ChatStore {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChanged(value: String) {
        _uiState.update { state -> state.copy(input = value) }
    }

    fun onSendClicked() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return

        val userMessage = ChatMessage(
            id = nextId(),
            author = "user",
            text = text
        )
        val assistantReply = ChatMessage(
            id = nextId(),
            author = "assistant",
            text = "Эхо: $text"
        )

        _uiState.update { state ->
            state.copy(
                input = "",
                messages = state.messages + userMessage + assistantReply
            )
        }
    }

    private fun nextId(): Long = System.nanoTime()
}

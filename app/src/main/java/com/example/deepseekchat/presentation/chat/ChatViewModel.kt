package com.example.deepseekchat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deepseekchat.domain.model.ChatMessage
import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.model.MessageRole
import com.example.deepseekchat.domain.usecase.CreateSessionUseCase
import com.example.deepseekchat.domain.usecase.GetActiveSessionUseCase
import com.example.deepseekchat.domain.usecase.ObserveMessagesUseCase
import com.example.deepseekchat.domain.usecase.ObserveSessionsUseCase
import com.example.deepseekchat.domain.usecase.SendMessageUseCase
import com.example.deepseekchat.domain.usecase.SetActiveSessionUseCase
import com.example.deepseekchat.domain.usecase.SetSessionSystemPromptUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val observeSessionsUseCase: ObserveSessionsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val setActiveSessionUseCase: SetActiveSessionUseCase,
    private val setSessionSystemPromptUseCase: SetSessionSystemPromptUseCase,
    private val getActiveSessionUseCase: GetActiveSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionsCache: List<ChatSession> = emptyList()
    private var activeSessionId: String? = null

    private var messageJob: Job? = null
    private var streamJob: Job? = null
    private var systemPromptJob: Job? = null
    private var creatingInitialSession = false

    init {
        observeSessions()
        observeActiveSession()
    }

    fun onInputChanged(input: String) {
        _uiState.update { it.copy(input = input) }
    }

    fun onSendClicked() {
        val currentState = _uiState.value
        val sessionId = currentState.activeSessionId ?: return
        val userText = currentState.input.trim()
        if (userText.isBlank() || currentState.isSending) return

        _uiState.update {
            it.copy(
                input = "",
                isSending = true,
                streamingText = "",
                errorMessage = null
            )
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var failed = false
            try {
                systemPromptJob?.join()
                sendMessageUseCase(sessionId, userText).collect { partial ->
                    _uiState.update { state ->
                        if (state.activeSessionId == sessionId) {
                            state.copy(streamingText = partial)
                        } else {
                            state
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Stream was cancelled explicitly (e.g. session switch).
            } catch (throwable: Throwable) {
                failed = true
                _uiState.update { state ->
                    state.copy(
                        errorMessage = throwable.toUiMessage(),
                        streamingText = ""
                    )
                }
            } finally {
                _uiState.update { state ->
                    if (state.activeSessionId == sessionId) {
                        if (failed) {
                            state.copy(
                                isSending = false,
                                streamingText = ""
                            )
                        } else {
                            // If the final assistant message is already in Room state, drop the temporary stream bubble now.
                            val hasFinalAssistantMessage =
                                state.streamingText.isNotEmpty() &&
                                    state.messages.lastOrNull()?.let { last ->
                                        last.role == MessageRole.ASSISTANT &&
                                            isEquivalentAssistantText(last.content, state.streamingText)
                                    } == true

                            state.copy(
                                isSending = false,
                                streamingText = if (hasFinalAssistantMessage) "" else state.streamingText
                            )
                        }
                    } else {
                        state.copy(
                            isSending = false,
                            streamingText = ""
                        )
                    }
                }
            }
        }
    }

    fun onSessionSelected(sessionId: String) {
        if (sessionId == activeSessionId) return

        cancelCurrentStream()
        viewModelScope.launch {
            setActiveSessionUseCase(sessionId)
        }
    }

    fun onCreateNewSession() {
        cancelCurrentStream()
        viewModelScope.launch {
            val session = createSessionUseCase()
            setActiveSessionUseCase(session.id)
        }
    }

    fun onSystemPromptSelected(systemPrompt: String) {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        if (state.messages.isNotEmpty() || state.isSending) return

        val normalizedPrompt = systemPrompt.trim()
        if (normalizedPrompt.isBlank()) return

        _uiState.update { it.copy(activeSessionSystemPrompt = normalizedPrompt) }
        systemPromptJob?.cancel()
        systemPromptJob = viewModelScope.launch {
            setSessionSystemPromptUseCase(sessionId, normalizedPrompt)
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            observeSessionsUseCase().collect { sessions ->
                sessionsCache = sessions

                if (sessions.isEmpty() && !creatingInitialSession) {
                    creatingInitialSession = true
                    val created = createSessionUseCase()
                    setActiveSessionUseCase(created.id)
                    creatingInitialSession = false
                    return@collect
                }

                val currentActive = activeSessionId
                if (currentActive == null || sessions.none { it.id == currentActive }) {
                    sessions.firstOrNull()?.id?.let { firstSessionId ->
                        setActiveSessionUseCase(firstSessionId)
                    }
                }

                syncHeaderAndSessions()
            }
        }
    }

    private fun observeActiveSession() {
        viewModelScope.launch {
            getActiveSessionUseCase().collect { sessionId ->
                if (sessionId == null) return@collect
                if (sessionId == activeSessionId) return@collect

                activeSessionId = sessionId
                subscribeToMessages(sessionId)
                syncHeaderAndSessions()
            }
        }
    }

    private fun subscribeToMessages(sessionId: String) {
        messageJob?.cancel()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                streamingText = ""
            )
        }

        messageJob = viewModelScope.launch {
            observeMessagesUseCase(sessionId).collect { messages ->
                val mappedMessages = messages.map { it.toUiModel() }
                _uiState.update { state ->
                    val lastMessage = mappedMessages.lastOrNull()
                    val shouldDropTemporaryStreamingBubble =
                        !state.isSending &&
                            state.streamingText.isNotEmpty() &&
                            lastMessage?.role == MessageRole.ASSISTANT &&
                            isEquivalentAssistantText(lastMessage.content, state.streamingText)

                    state.copy(
                        messages = mappedMessages,
                        streamingText = if (shouldDropTemporaryStreamingBubble) {
                            ""
                        } else {
                            state.streamingText
                        }
                    )
                }
            }
        }
    }

    private fun syncHeaderAndSessions() {
        val selectedSession = sessionsCache.firstOrNull { it.id == activeSessionId }
        _uiState.update { state ->
            state.copy(
                sessions = sessionsCache.map {
                    ChatSessionUi(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt,
                        systemPrompt = it.systemPrompt
                    )
                },
                activeSessionId = selectedSession?.id,
                activeSessionTitle = selectedSession?.title ?: "New chat",
                activeSessionSystemPrompt = selectedSession?.systemPrompt
            )
        }
    }

    private fun cancelCurrentStream() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update {
            it.copy(
                isSending = false,
                streamingText = ""
            )
        }
    }

    private fun ChatMessage.toUiModel(): ChatMessageUi {
        return ChatMessageUi(
            stableId = id.toString(),
            role = role,
            content = content,
            timestamp = timestamp,
            isStreaming = false
        )
    }

    private fun Throwable.toUiMessage(): String {
        return when (this) {
            is UnknownHostException -> "No internet connection."
            is IOException -> "Network error. Check your connection and try again."
            else -> message ?: "Request failed"
        }
    }

    private fun isEquivalentAssistantText(saved: String, streaming: String): Boolean {
        return saved.normalizeAssistantText() == streaming.normalizeAssistantText()
    }

    private fun String.normalizeAssistantText(): String {
        return replace("\r\n", "\n").trimEnd()
    }
}

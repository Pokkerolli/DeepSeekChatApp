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
                streamingText = "",
                usage = ConversationUsageUi()
            )
        }

        messageJob = viewModelScope.launch {
            observeMessagesUseCase(sessionId).collect { messages ->
                val usageResult = messages.toUiModelsWithUsage()
                val mappedMessages = usageResult.messages
                _uiState.update { state ->
                    val lastMessage = mappedMessages.lastOrNull()
                    val shouldDropTemporaryStreamingBubble =
                        !state.isSending &&
                            state.streamingText.isNotEmpty() &&
                            lastMessage?.role == MessageRole.ASSISTANT &&
                            isEquivalentAssistantText(lastMessage.content, state.streamingText)

                    state.copy(
                        messages = mappedMessages,
                        usage = usageResult.usage,
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

    private fun List<ChatMessage>.toUiModelsWithUsage(): UsageCalculation {
        if (isEmpty()) {
            return UsageCalculation(
                messages = emptyList(),
                usage = ConversationUsageUi()
            )
        }

        val pendingRequests = ArrayDeque<PendingRequestCost>()
        val totalDialogTokens = sumOf { message ->
            (message.totalTokens ?: 0).coerceAtLeast(0)
        }
        var cumulativeInputCostCacheHitUsd = 0.0
        var cumulativeInputCostCacheMissUsd = 0.0
        var cumulativeOutputCostUsd = 0.0

        val uiMessages = map { message ->
            when (message.role) {
                MessageRole.USER -> {
                    val input = message.toInputCostBreakdown()
                    input?.let { usage ->
                        cumulativeInputCostCacheHitUsd += usage.inputCostCacheHitUsd
                        cumulativeInputCostCacheMissUsd += usage.inputCostCacheMissUsd
                        pendingRequests.addLast(
                            PendingRequestCost(
                                inputTokens = usage.promptTokens,
                                inputCacheHitTokens = usage.promptCacheHitTokens,
                                inputCacheMissTokens = usage.promptCacheMissTokens,
                                inputCostCacheHitUsd = usage.inputCostCacheHitUsd,
                                inputCostCacheMissUsd = usage.inputCostCacheMissUsd,
                                inputTotalCostUsd = usage.inputTotalCostUsd
                            )
                        )
                    }
                    message.toUiModel(
                        userTokens = input?.promptTokens,
                        userCacheHitTokens = input?.promptCacheHitTokens,
                        userCacheMissTokens = input?.promptCacheMissTokens,
                        inputCostCacheHitUsd = input?.inputCostCacheHitUsd,
                        inputCostCacheMissUsd = input?.inputCostCacheMissUsd
                    )
                }

                MessageRole.ASSISTANT -> {
                    val outputTokens = message.completionTokens
                    val outputCostUsd = outputTokens?.let(TokenPricing::outputCostUsd)
                    if (outputCostUsd != null) {
                        cumulativeOutputCostUsd += outputCostUsd
                    }

                    val matchedRequest = if (pendingRequests.isEmpty()) {
                        null
                    } else {
                        pendingRequests.removeFirst()
                    }

                    val requestTotalTokens = message.totalTokens ?: if (
                        matchedRequest != null &&
                        outputTokens != null
                    ) {
                        matchedRequest.inputTokens + outputTokens
                    } else {
                        null
                    }

                    message.toUiModel(
                        userTokens = matchedRequest?.inputTokens,
                        userCacheHitTokens = matchedRequest?.inputCacheHitTokens,
                        userCacheMissTokens = matchedRequest?.inputCacheMissTokens,
                        inputCostCacheHitUsd = matchedRequest?.inputCostCacheHitUsd,
                        inputCostCacheMissUsd = matchedRequest?.inputCostCacheMissUsd,
                        assistantTokens = outputTokens,
                        requestTotalTokens = requestTotalTokens,
                        outputCostUsd = outputCostUsd,
                        requestTotalCostUsd = if (matchedRequest != null && outputCostUsd != null) {
                            matchedRequest.inputTotalCostUsd + outputCostUsd
                        } else {
                            null
                        }
                    )
                }
            }
        }

        val cumulativeTotalCostUsd = cumulativeInputCostCacheHitUsd + cumulativeInputCostCacheMissUsd + cumulativeOutputCostUsd

        return UsageCalculation(
            messages = uiMessages,
            usage = ConversationUsageUi(
                contextLength = totalDialogTokens,
                cumulativeTotalCostUsd = cumulativeTotalCostUsd
            )
        )
    }

    private fun ChatMessage.toUiModel(
        userTokens: Int? = null,
        userCacheHitTokens: Int? = null,
        userCacheMissTokens: Int? = null,
        inputCostCacheHitUsd: Double? = null,
        inputCostCacheMissUsd: Double? = null,
        assistantTokens: Int? = null,
        requestTotalTokens: Int? = null,
        outputCostUsd: Double? = null,
        requestTotalCostUsd: Double? = null
    ): ChatMessageUi {
        return ChatMessageUi(
            stableId = id.toString(),
            role = role,
            content = content,
            timestamp = timestamp,
            isStreaming = false,
            userTokens = userTokens,
            userCacheHitTokens = userCacheHitTokens,
            userCacheMissTokens = userCacheMissTokens,
            inputCostCacheHitUsd = inputCostCacheHitUsd,
            inputCostCacheMissUsd = inputCostCacheMissUsd,
            assistantTokens = assistantTokens,
            requestTotalTokens = requestTotalTokens,
            outputCostUsd = outputCostUsd,
            requestTotalCostUsd = requestTotalCostUsd
        )
    }

    private fun ChatMessage.toInputCostBreakdown(): InputCostBreakdown? {
        val rawPromptTokens = promptTokens ?: return null
        if (rawPromptTokens <= 0) return null

        val normalizedHitTokens = (promptCacheHitTokens ?: 0).coerceAtLeast(0)
        val normalizedMissTokens = (promptCacheMissTokens ?: (rawPromptTokens - normalizedHitTokens))
            .coerceAtLeast(0)
        val normalizedPromptTokens = maxOf(rawPromptTokens, normalizedHitTokens + normalizedMissTokens)

        val inputCostCacheHitUsd = TokenPricing.inputCostCacheHitUsd(normalizedHitTokens)
        val inputCostCacheMissUsd = TokenPricing.inputCostCacheMissUsd(normalizedMissTokens)

        return InputCostBreakdown(
            promptTokens = normalizedPromptTokens,
            promptCacheHitTokens = normalizedHitTokens,
            promptCacheMissTokens = normalizedMissTokens,
            inputCostCacheHitUsd = inputCostCacheHitUsd,
            inputCostCacheMissUsd = inputCostCacheMissUsd,
            inputTotalCostUsd = inputCostCacheHitUsd + inputCostCacheMissUsd
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

    private data class PendingRequestCost(
        val inputTokens: Int,
        val inputCacheHitTokens: Int,
        val inputCacheMissTokens: Int,
        val inputCostCacheHitUsd: Double,
        val inputCostCacheMissUsd: Double,
        val inputTotalCostUsd: Double
    )

    private data class InputCostBreakdown(
        val promptTokens: Int,
        val promptCacheHitTokens: Int,
        val promptCacheMissTokens: Int,
        val inputCostCacheHitUsd: Double,
        val inputCostCacheMissUsd: Double,
        val inputTotalCostUsd: Double
    )

    private data class UsageCalculation(
        val messages: List<ChatMessageUi>,
        val usage: ConversationUsageUi
    )
}

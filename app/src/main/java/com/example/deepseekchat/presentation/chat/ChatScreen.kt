package com.example.deepseekchat.presentation.chat

import android.annotation.SuppressLint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.deepseekchat.domain.model.MessageRole
import com.example.deepseekchat.presentation.theme.DeepSeekChatTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatRoute(
    viewModel: ChatViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSendClick = viewModel::onSendClicked,
        onSessionSelected = viewModel::onSessionSelected,
        onCreateSession = viewModel::onCreateNewSession,
        onSystemPromptSelected = viewModel::onSystemPromptSelected,
        onConsumeError = viewModel::consumeError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onCreateSession: () -> Unit,
    onSystemPromptSelected: (String) -> Unit,
    onConsumeError: () -> Unit
) {
    var showSessionsSheet by rememberSaveable { mutableStateOf(false) }
    var didInitialScrollForSession by remember(state.activeSessionId) { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var shouldAutoScroll by remember { mutableStateOf(true) }

    val displayMessages = remember(
        state.messages,
        state.streamingText,
        state.activeSessionId,
        state.isSending
    ) {
        val shouldShowStreamingBubble = state.isSending || state.streamingText.isNotEmpty()
        if (!shouldShowStreamingBubble) {
            state.messages
        } else {
            state.messages + ChatMessageUi(
                stableId = "streaming_${state.activeSessionId}",
                role = MessageRole.ASSISTANT,
                content = state.streamingText,
                timestamp = System.currentTimeMillis(),
                isStreaming = state.isSending
            )
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) {
                true
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 3
            }
        }
            .distinctUntilChanged()
            .collect { shouldFollow ->
                shouldAutoScroll = shouldFollow
            }
    }

    LaunchedEffect(state.activeSessionId, state.messages.size) {
        if (!didInitialScrollForSession && state.messages.isNotEmpty()) {
            withFrameNanos { }
            listState.scrollToBottom()
            withFrameNanos { }
            listState.scrollToBottom()
            didInitialScrollForSession = true
            shouldAutoScroll = true
        }
    }

    LaunchedEffect(
        displayMessages.lastOrNull()?.stableId,
        displayMessages.lastOrNull()?.content,
        state.streamingText,
        state.isSending
    ) {
        if (displayMessages.isEmpty()) return@LaunchedEffect
        if (shouldAutoScroll) {
            withFrameNanos { }
            listState.scrollToBottom()
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.activeSessionTitle,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showSessionsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open sessions"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MessageInputBar(
                value = state.input,
                isSending = state.isSending,
                showSystemPromptPresets = state.messages.isEmpty() && !state.isSending,
                selectedSystemPrompt = state.activeSessionSystemPrompt,
                onValueChanged = onInputChanged,
                onSystemPromptSelected = onSystemPromptSelected,
                onSendClick = onSendClick
            )
        }
    ) { contentPadding ->
        val bottomPadding = contentPadding.calculateBottomPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    bottom = bottomPadding
                ),
        ) {
            ConversationUsagePanel(
                usage = state.usage,
                isAssistantResponding = state.isSending
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = displayMessages,
                    key = { it.stableId }
                ) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }

    if (showSessionsSheet) {
        SessionsBottomSheet(
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            onDismissRequest = { showSessionsSheet = false },
            onSessionSelected = { sessionId ->
                onSessionSelected(sessionId)
                showSessionsSheet = false
            },
            onCreateNewSession = {
                onCreateSession()
                showSessionsSheet = false
            }
        )
    }
}

@Composable
private fun ConversationUsagePanel(
    usage: ConversationUsageUi,
    isAssistantResponding: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Total tokens: ${TokenPricing.formatTokens(usage.contextLength)} (${TokenPricing.formatUsd(usage.cumulativeTotalCostUsd)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isAssistantResponding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun MessageBubble(message: ChatMessageUi) {
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.82f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Column(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                Surface(
                    color = bubbleColor,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = if (isUser) 1.dp else 0.dp
                ) {
                    val cursorVisible = rememberBlinkingCursorVisible(enabled = message.isStreaming)
                    val renderedText = if (message.isStreaming && cursorVisible) {
                        "${message.content}▍"
                    } else {
                        message.content
                    }

                    Text(
                        text = renderedText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                buildMessageUsageText(message)?.let { usageText ->
                    Text(
                        text = usageText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun buildMessageUsageText(message: ChatMessageUi): String? {
    return when (message.role) {
        MessageRole.USER -> null

        MessageRole.ASSISTANT -> {
            val outputTokens = message.assistantTokens ?: return null
            val outputCostUsd = message.outputCostUsd ?: return null
            val totalCostUsd = message.requestTotalCostUsd
            val requestTotalTokens = message.requestTotalTokens
            val inputTokens = message.userTokens
            val inputCacheHitTokens = message.userCacheHitTokens
            val inputCacheMissTokens = message.userCacheMissTokens
            val inputCostCacheHitUsd = message.inputCostCacheHitUsd
            val inputCostCacheMissUsd = message.inputCostCacheMissUsd

            val lines = mutableListOf<String>()

            if (
                inputTokens != null &&
                inputCacheHitTokens != null &&
                inputCacheMissTokens != null &&
                inputCostCacheHitUsd != null &&
                inputCostCacheMissUsd != null
            ) {
                val sumCost = TokenPricing.formatUsd(inputCostCacheHitUsd + inputCostCacheMissUsd)
                lines += "Prompt tokens: ${TokenPricing.formatTokens(inputTokens)} ($sumCost) Hit ${TokenPricing.formatTokens(inputCacheHitTokens)}, miss ${TokenPricing.formatTokens(inputCacheMissTokens)}"
            }

            lines += "Completion tokens: ${TokenPricing.formatTokens(outputTokens)} (${TokenPricing.formatUsd(outputCostUsd)})"

            if (requestTotalTokens != null && totalCostUsd != null) {
                lines += "Total tokens: ${TokenPricing.formatTokens(requestTotalTokens)} out of ${TokenPricing.formatTokens(TokenPricing.MAX_CONTEXT_LENGTH)} (${TokenPricing.formatUsd(totalCostUsd)})"
            }

            lines.joinToString(separator = "\n")
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    isSending: Boolean,
    showSystemPromptPresets: Boolean,
    selectedSystemPrompt: String?,
    onValueChanged: (String) -> Unit,
    onSystemPromptSelected: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showSystemPromptPresets) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(
                        items = SYSTEM_PROMPT_PRESETS,
                        key = { it.label }
                    ) { preset ->
                        FilterChip(
                            selected = selectedSystemPrompt == preset.prompt,
                            onClick = { onSystemPromptSelected(preset.prompt) },
                            label = {
                                Text(text = preset.label)
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = value,
                    onValueChange = onValueChanged,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = {
                        Text(text = "Send a message...")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = onSendClick,
                    enabled = value.isNotBlank() && !isSending
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberBlinkingCursorVisible(enabled: Boolean): Boolean {
    if (!enabled) return false

    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    return alpha > 0.5f
}

private suspend fun LazyListState.scrollToBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return

    var lastItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
    if (lastItemInfo == null) {
        scrollToItem(lastIndex)
        withFrameNanos { }
        lastItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
    }

    lastItemInfo?.let { item ->
        val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val overflow = (item.offset + item.size) - viewportBottom
        if (overflow > 0) {
            scrollBy(overflow.toFloat())
        }
    }
}

private data class SystemPromptPreset(
    val label: String,
    val prompt: String
)

private val SYSTEM_PROMPT_PRESETS = listOf(
    SystemPromptPreset(
        label = "Лаконичные ответы",
        prompt = "Ты — полезный ассистент. Отвечай кратко, по делу и структурно.\n" +
                "Правила:\n" +
                "Сначала дай итог/ответ в 1–2 предложениях.\n" +
                "Затем (если нужно) — короткий список действий/пунктов.\n" +
                "Не добавляй лишней теории, воды, предупреждений и “размышлений вслух”.\n" +
                "Если запрос неполный, не задавай много вопросов: сделай разумные допущения и явно отметь их одной строкой.\n" +
                "Используй форматирование: короткие абзацы, маркированные списки, мини-заголовки.\n" +
                "Если пользователь просит текст/письмо/план — сразу выдай готовый вариант.\n" +
                "Если нужна точность (цифры, сроки, формулировки) — уточни 1 ключевой вопрос, иначе продолжай с допущением.\n" +
                "Тон: спокойный, деловой, без фамильярности."
    ),
    SystemPromptPreset(
        label = "Наставник",
        prompt = "Ты — экспертный ассистент и наставник. Отвечай развернуто, понятно, с примерами и пошаговым разбором.\n" +
                "Правила:\n" +
                "Структура ответа:\n" +
                "Короткое резюме (2–4 строки)\n" +
                "Пошаговое объяснение (логика/алгоритм)\n" +
                "Примеры (минимум 1–2, лучше с вариациями)\n" +
                "Типичные ошибки и как избежать\n" +
                "Что делать дальше (чеклист/следующие шаги)\n" +
                "Для сложных задач: сначала опиши план решения, затем выполняй его.\n" +
                "Если данных не хватает, задай до 3 уточняющих вопросов, но параллельно предложи решение на основе допущений.\n" +
                "Пиши простым языком, раскрывай термины.\n" +
                "Можно использовать таблицы, списки, схемы, псевдокод.\n" +
                "Тон: дружелюбный, терпеливый, обучающий.\n" +
                "Если пользователь просит “быстро” или “только ответ” — сокращай объём, но всё равно сохраняй ясность."
    ),
)

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ChatScreenPreview() {
    DeepSeekChatTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChatScreen(
                state = ChatUiState(
                    sessions = listOf(
                        ChatSessionUi(
                            id = "preview-session",
                            title = "Preview chat",
                            updatedAt = System.currentTimeMillis(),
                            systemPrompt = null
                        )
                    ),
                    activeSessionId = "preview-session",
                    activeSessionTitle = "Preview chat",
                    messages = listOf(
                        ChatMessageUi(
                            stableId = "preview-user-1",
                            role = MessageRole.USER,
                            content = "Сделай краткий план запуска MVP за 2 недели.",
                            timestamp = System.currentTimeMillis() - 90_000,
                            userTokens = 854,
                            userCacheHitTokens = 832,
                            userCacheMissTokens = 22,
                            inputCostCacheHitUsd = TokenPricing.inputCostCacheHitUsd(832),
                            inputCostCacheMissUsd = TokenPricing.inputCostCacheMissUsd(22)
                        ),
                        ChatMessageUi(
                            stableId = "preview-assistant-1",
                            role = MessageRole.ASSISTANT,
                            content = "1) Определить scope и ключевые фичи.\n2) Поднять backend/API.\n3) Собрать UI и базовую аналитику.",
                            timestamp = System.currentTimeMillis() - 60_000,
                            userTokens = 854,
                            userCacheHitTokens = 832,
                            userCacheMissTokens = 22,
                            inputCostCacheHitUsd = TokenPricing.inputCostCacheHitUsd(832),
                            inputCostCacheMissUsd = TokenPricing.inputCostCacheMissUsd(22),
                            assistantTokens = 48,
                            requestTotalTokens = 902,
                            outputCostUsd = TokenPricing.outputCostUsd(48),
                            requestTotalCostUsd = TokenPricing.inputCostCacheHitUsd(832) +
                                    TokenPricing.inputCostCacheMissUsd(22) +
                                    TokenPricing.outputCostUsd(48)
                        )
                    ),
                    input = "Добавь риски и метрики.",
                    isSending = true,
                    streamingText = "4) Уточнить риски релиза и задать KPI...",
                    usage = ConversationUsageUi(
                        contextLength = 902,
                        cumulativeTotalCostUsd = TokenPricing.inputCostCacheHitUsd(832) +
                                TokenPricing.inputCostCacheMissUsd(22) +
                                TokenPricing.outputCostUsd(48)
                    )
                ),
                onInputChanged = {},
                onSendClick = {},
                onSessionSelected = {},
                onCreateSession = {},
                onSystemPromptSelected = {},
                onConsumeError = {}
            )
        }
    }
}

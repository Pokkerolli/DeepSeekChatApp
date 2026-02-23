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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.deepseekchat.domain.model.MessageRole
import kotlinx.coroutines.flow.collect
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
                onValueChanged = onInputChanged,
                onSendClick = onSendClick
            )
        }
    ) { contentPadding ->
        val bottomPadding = contentPadding.calculateBottomPadding()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = bottomPadding
                ),
            state = listState,
            contentPadding = PaddingValues(vertical = 16.dp),
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
            Surface(
                color = bubbleColor,
                shape = MaterialTheme.shapes.large,
                tonalElevation = if (isUser) 1.dp else 0.dp,
                modifier = Modifier.widthIn(max = maxBubbleWidth)
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
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    isSending: Boolean,
    onValueChanged: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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

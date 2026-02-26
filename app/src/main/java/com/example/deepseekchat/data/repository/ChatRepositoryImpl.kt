package com.example.deepseekchat.data.repository
import androidx.room.withTransaction
import com.example.deepseekchat.data.local.dao.MessageDao
import com.example.deepseekchat.data.local.dao.SessionDao
import com.example.deepseekchat.data.local.datastore.ActiveSessionPreferences
import com.example.deepseekchat.data.local.db.AppDatabase
import com.example.deepseekchat.data.local.entity.MessageCompressionState
import com.example.deepseekchat.data.local.entity.MessageEntity
import com.example.deepseekchat.data.local.entity.SessionEntity
import com.example.deepseekchat.data.local.mapper.toDomain
import com.example.deepseekchat.data.remote.api.DeepSeekApi
import com.example.deepseekchat.data.remote.dto.ChatCompletionMessage
import com.example.deepseekchat.data.remote.dto.ChatCompletionRequest
import com.example.deepseekchat.data.remote.dto.ChatCompletionUsage
import com.example.deepseekchat.data.remote.dto.StreamOptions
import com.example.deepseekchat.data.remote.stream.SseStreamEvent
import com.example.deepseekchat.data.remote.stream.SseStreamParser
import com.example.deepseekchat.domain.model.ChatMessage
import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.model.MessageRole
import com.example.deepseekchat.domain.repository.ChatRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ChatRepositoryImpl(
    private val database: AppDatabase,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val deepSeekApi: DeepSeekApi,
    private val sseStreamParser: SseStreamParser,
    private val activeSessionPreferences: ActiveSessionPreferences,
    private val json: Json
) : ChatRepository {

    private val summarizationMutexBySession = ConcurrentHashMap<String, Mutex>()

    override fun observeSessions(): Flow<List<ChatSession>> {
        return sessionDao.observeSessions().map { sessions ->
            sessions.map { it.toDomain() }
        }
    }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.observeMessages(sessionId).map { messages ->
            messages.map { it.toDomain() }
        }
    }

    override suspend fun createSession(): ChatSession {
        val now = System.currentTimeMillis()
        val entity = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = DEFAULT_SESSION_TITLE,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.insertSession(entity)
        return entity.toDomain()
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
        summarizationMutexBySession.remove(sessionId)
    }

    override suspend fun setActiveSession(sessionId: String) {
        activeSessionPreferences.setActiveSessionId(sessionId)
    }

    override suspend fun setSessionSystemPrompt(sessionId: String, systemPrompt: String?) {
        val now = System.currentTimeMillis()
        val normalizedPrompt = systemPrompt.normalizeSystemPrompt()
        val existingSession = sessionDao.getSessionById(sessionId)

        if (existingSession == null) {
            sessionDao.insertSession(
                SessionEntity(
                    id = sessionId,
                    title = DEFAULT_SESSION_TITLE,
                    createdAt = now,
                    updatedAt = now,
                    systemPrompt = normalizedPrompt
                )
            )
            return
        }

        sessionDao.updateSystemPrompt(
            sessionId = sessionId,
            systemPrompt = normalizedPrompt,
            updatedAt = now
        )
    }

    override suspend fun setSessionContextCompressionEnabled(sessionId: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        if (messageDao.getMessagesOnce(sessionId).isNotEmpty()) return

        val existingSession = sessionDao.getSessionById(sessionId)

        if (existingSession == null) {
            sessionDao.insertSession(
                SessionEntity(
                    id = sessionId,
                    title = DEFAULT_SESSION_TITLE,
                    createdAt = now,
                    updatedAt = now,
                    contextCompressionEnabled = enabled,
                    contextSummary = null,
                    summarizedMessagesCount = 0
                )
            )
            return
        }

        sessionDao.updateContextCompression(
            sessionId = sessionId,
            enabled = enabled,
            contextSummary = null,
            summarizedMessagesCount = 0,
            updatedAt = now
        )
    }

    override suspend fun runContextSummarizationIfNeeded(sessionId: String) {
        val mutex = summarizationMutexBySession.getOrPut(sessionId) { Mutex() }
        mutex.withLock {
            compressReadyMessages(sessionId)
        }
    }

    override fun observeActiveSessionId(): Flow<String?> {
        return activeSessionPreferences.observeActiveSessionId()
    }

    override fun sendMessageStreaming(sessionId: String, content: String): Flow<String> = flow {
        try {
            val cleanedContent = content.trim()
            if (cleanedContent.isEmpty()) return@flow

            val now = System.currentTimeMillis()
            val existingSession = sessionDao.getSessionById(sessionId)
            val session = existingSession ?: SessionEntity(
                id = sessionId,
                title = DEFAULT_SESSION_TITLE,
                createdAt = now,
                updatedAt = now
            )

            val resolvedTitle = if (session.title == DEFAULT_SESSION_TITLE) {
                cleanedContent.toSessionTitle()
            } else {
                session.title
            }

            val sessionSnapshot = session.copy(
                title = resolvedTitle,
                updatedAt = now
            )

            var userMessageId: Long = 0L
            database.withTransaction {
                sessionDao.insertSession(sessionSnapshot)
                userMessageId = messageDao.insertMessage(
                    MessageEntity(
                        sessionId = sessionId,
                        role = MessageRole.USER.name,
                        content = cleanedContent,
                        createdAt = now
                    )
                )
            }

            val allMessages = messageDao.getMessagesOnce(sessionId)
            val contextMessages = buildUserRequestContextMessages(
                sessionId = sessionId,
                session = sessionSnapshot,
                allMessages = allMessages
            )

            val systemPrompt = sessionSnapshot.systemPrompt.normalizeSystemPrompt()
            val requestMessages = buildList {
                if (systemPrompt != null) {
                    add(
                        ChatCompletionMessage(
                            role = SYSTEM_ROLE,
                            content = systemPrompt
                        )
                    )
                }
                addAll(contextMessages)
            }

            val request = ChatCompletionRequest(
                model = DEFAULT_MODEL,
                messages = requestMessages,
                stream = true,
                streamOptions = StreamOptions(includeUsage = true)
            )
            val response = deepSeekApi.streamChatCompletions(request)
            val responseBody = response.body()

            if (!response.isSuccessful || responseBody == null) {
                val rawError = response.errorBody()?.string()
                throw ChatApiException(
                    buildApiErrorMessage(
                        code = response.code(),
                        rawError = rawError
                    )
                )
            }

            var finalAssistantText = ""
            var finalUsage: ChatCompletionUsage? = null

            sseStreamParser.streamEvents(responseBody).collect { event ->
                when (event) {
                    is SseStreamEvent.Text -> {
                        finalAssistantText = event.value
                        emit(event.value)
                    }

                    is SseStreamEvent.Usage -> {
                        finalUsage = event.value
                    }
                }
            }

            val promptUsage = resolvePromptUsage(finalUsage)
            val doneTimestamp = System.currentTimeMillis()

            database.withTransaction {
                if (promptUsage != null && userMessageId > 0L) {
                    messageDao.updateUserMessageUsage(
                        messageId = userMessageId,
                        promptTokens = promptUsage.promptTokens,
                        promptCacheHitTokens = promptUsage.promptCacheHitTokens,
                        promptCacheMissTokens = promptUsage.promptCacheMissTokens
                    )
                }

                if (finalAssistantText.isNotBlank()) {
                    messageDao.insertMessage(
                        MessageEntity(
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = finalAssistantText,
                            createdAt = doneTimestamp,
                            completionTokens = finalUsage?.completionTokens,
                            totalTokens = finalUsage?.totalTokens
                        )
                    )
                    sessionDao.touchSession(sessionId, doneTimestamp)
                }
            }

        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun buildUserRequestContextMessages(
        sessionId: String,
        session: SessionEntity,
        allMessages: List<MessageEntity>
    ): List<ChatCompletionMessage> {
        if (!session.contextCompressionEnabled || allMessages.size <= CONTEXT_TAIL_MESSAGES_COUNT) {
            return allMessages.toApiMessages()
        }

        val splitIndex = (allMessages.size - CONTEXT_TAIL_MESSAGES_COUNT).coerceAtLeast(0)
        val olderMessages = allMessages.take(splitIndex)
        val recentMessages = allMessages.drop(splitIndex)

        markOlderMessagesReadyForCompression(
            sessionId = sessionId,
            olderMessages = olderMessages
        )

        val readyMessages = olderMessages.filter {
            it.compressionState.toCompressionState() != MessageCompressionState.SUMMARIZED
        }

        return buildList {
            session.contextSummary.normalizeSummary()?.let { summary ->
                add(
                    ChatCompletionMessage(
                        role = SYSTEM_ROLE,
                        content = CONTEXT_SUMMARY_PREFIX + "\n" + summary
                    )
                )
            }
            addAll(readyMessages.toApiMessages())
            addAll(recentMessages.toApiMessages())
        }
    }

    private suspend fun markOlderMessagesReadyForCompression(
        sessionId: String,
        olderMessages: List<MessageEntity>
    ) {
        val idsToReady = olderMessages
            .filter { it.compressionState.toCompressionState() == MessageCompressionState.ACTIVE }
            .map { it.id }

        if (idsToReady.isEmpty()) return

        messageDao.updateCompressionStateForIds(
            messageIds = idsToReady,
            state = MessageCompressionState.READY_FOR_SUMMARY.name
        )
    }

    private suspend fun compressReadyMessages(sessionId: String) {
        try {
            while (true) {
                val session = sessionDao.getSessionById(sessionId) ?: return
                if (!session.contextCompressionEnabled) return

                val allMessages = messageDao.getMessagesOnce(sessionId)
                if (allMessages.size <= CONTEXT_TAIL_MESSAGES_COUNT) return

                val splitIndex = (allMessages.size - CONTEXT_TAIL_MESSAGES_COUNT).coerceAtLeast(0)
                val olderMessages = allMessages.take(splitIndex)

                markOlderMessagesReadyForCompression(
                    sessionId = sessionId,
                    olderMessages = olderMessages
                )

                val readyBatch = olderMessages
                    .filter { it.compressionState.toCompressionState() != MessageCompressionState.SUMMARIZED }
                    .take(SUMMARY_BATCH_SIZE)

                if (readyBatch.size < SUMMARY_BATCH_SIZE) return

                sessionDao.updateContextSummarizationInProgress(sessionId, true)
                val updatedSummary = requestCompressedSummary(
                    currentSummary = session.contextSummary.normalizeSummary(),
                    messagesBatch = readyBatch
                ) ?: return

                val batchIds = readyBatch.map { it.id }

                database.withTransaction {
                    messageDao.updateCompressionStateForIds(
                        messageIds = batchIds,
                        state = MessageCompressionState.SUMMARIZED.name
                    )

                    val summarizedCount = messageDao.countByCompressionState(
                        sessionId = sessionId,
                        state = MessageCompressionState.SUMMARIZED.name
                    )

                    sessionDao.updateContextSummary(
                        sessionId = sessionId,
                        contextSummary = updatedSummary,
                        summarizedMessagesCount = summarizedCount
                    )
                }
            }
        } finally {
            sessionDao.updateContextSummarizationInProgress(sessionId, false)
        }
    }

    private suspend fun requestCompressedSummary(
        currentSummary: String?,
        messagesBatch: List<MessageEntity>
    ): String? {
        val payload = buildSummaryCompressionPayload(
            currentSummary = currentSummary,
            messagesBatch = messagesBatch
        )

        val request = ChatCompletionRequest(
            model = DEFAULT_MODEL,
            messages = listOf(
                ChatCompletionMessage(
                    role = SYSTEM_ROLE,
                    content = SUMMARY_COMPRESSION_SYSTEM_PROMPT
                ),
                ChatCompletionMessage(
                    role = USER_ROLE,
                    content = payload
                )
            ),
            stream = false
        )

        val response = deepSeekApi.chatCompletions(request)
        val body = response.body()

        if (!response.isSuccessful || body == null) {
            val rawError = response.errorBody()?.string()
            throw ChatApiException(
                buildApiErrorMessage(
                    code = response.code(),
                    rawError = rawError
                )
            )
        }

        return body
            .choices
            .firstOrNull()
            ?.message
            ?.content
            .normalizeSummary()
            ?.trimSummaryLength()
    }

    private fun buildSummaryCompressionPayload(
        currentSummary: String?,
        messagesBatch: List<MessageEntity>
    ): String {
        val batchText = messagesBatch.joinToString(separator = "\n") { message ->
            val roleLabel = when (MessageRole.fromStored(message.role)) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
            }
            "- $roleLabel: ${message.content.trim()}"
        }

        return buildString {
            append("Обнови краткое summary истории диалога.\n")
            append("Правила:\n")
            append("1) Сохрани факты, цели, ограничения, решения, договоренности и важные предпочтения.\n")
            append("2) Не добавляй выдумки и не теряй смысл.\n")
            append("3) Пиши без воды, компактно.\n")
            append("4) Верни только новое summary, без пояснений.\n")

            append("\nТекущее summary:\n")
            append(currentSummary?.ifBlank { "(пусто)" } ?: "(пусто)")

            append("\n\nНовые 10 сообщений для сжатия:\n")
            append(batchText)
        }
    }

    private fun List<MessageEntity>.toApiMessages(): List<ChatCompletionMessage> {
        return map { message ->
            ChatCompletionMessage(
                role = MessageRole.fromStored(message.role).apiValue,
                content = message.content
            )
        }
    }

    private fun buildApiErrorMessage(code: Int, rawError: String?): String {
        val parsedMessage = extractApiErrorMessage(rawError)
        if (!parsedMessage.isNullOrBlank()) {
            return "API error ($code): $parsedMessage"
        }

        return when (code) {
            400 -> "API error (400): invalid request. Check baseUrl (/v1/), model and payload."
            401 -> "API error (401): unauthorized. Check DEEPSEEK_API_KEY."
            403 -> "API error (403): access denied for this key/model."
            404 -> "API error (404): endpoint not found. Check DEEPSEEK_BASE_URL."
            429 -> "API error (429): rate limit exceeded."
            in 500..599 -> "API error ($code): DeepSeek server is unavailable."
            else -> {
                val safeRaw = rawError?.take(MAX_RAW_ERROR_LENGTH).orEmpty()
                if (safeRaw.isNotBlank()) "API error ($code): $safeRaw" else "API error ($code)"
            }
        }
    }

    private fun extractApiErrorMessage(rawError: String?): String? {
        if (rawError.isNullOrBlank()) return null

        val root = runCatching {
            json.parseToJsonElement(rawError)
        }.getOrNull() as? JsonObject ?: return null

        val nestedErrorMessage = (root["error"] as? JsonObject)
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
        if (!nestedErrorMessage.isNullOrBlank()) return nestedErrorMessage

        val topLevelMessage = root["message"]?.jsonPrimitive?.contentOrNull
        if (!topLevelMessage.isNullOrBlank()) return topLevelMessage

        return null
    }

    private fun String.toSessionTitle(): String {
        val firstLine = lineSequence().firstOrNull().orEmpty().trim()
        return firstLine.take(48).ifBlank { DEFAULT_SESSION_TITLE }
    }

    private fun String?.normalizeSystemPrompt(): String? {
        return this?.trim()?.ifBlank { null }
    }

    private fun String?.normalizeSummary(): String? {
        return this?.trim()?.ifBlank { null }
    }

    private fun String.trimSummaryLength(): String {
        if (length <= MAX_SUMMARY_LENGTH) return this
        return takeLast(MAX_SUMMARY_LENGTH).trim()
    }

    private fun String.toCompressionState(): MessageCompressionState {
        return MessageCompressionState.entries.firstOrNull { it.name == this }
            ?: MessageCompressionState.ACTIVE
    }

    private fun resolvePromptUsage(usage: ChatCompletionUsage?): PromptUsage? {
        if (usage == null) return null
        return PromptUsage(
            promptTokens = usage.promptTokens ?: 0,
            promptCacheHitTokens = usage.promptCacheHitTokens ?: 0,
            promptCacheMissTokens = usage.promptCacheMissTokens ?: 0
        )
    }

    private companion object {
        const val DEFAULT_SESSION_TITLE = "New chat"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val SYSTEM_ROLE = "system"
        const val USER_ROLE = "user"
        const val MAX_RAW_ERROR_LENGTH = 240

        const val CONTEXT_TAIL_MESSAGES_COUNT = 10
        const val SUMMARY_BATCH_SIZE = 10
        const val MAX_SUMMARY_LENGTH = 6_000

        const val CONTEXT_SUMMARY_PREFIX =
            "Summary of earlier messages (messages marked as previously compressed). " +
                "Use it as compressed context and prioritize newer raw messages if they conflict."

        const val SUMMARY_COMPRESSION_SYSTEM_PROMPT =
            "Ты модуль сжатия истории чата. Выдавай плотное summary без воды. " +
                "Сохраняй факты, намерения пользователя, ограничения, решения и открытые вопросы."
    }
}

private class ChatApiException(message: String) : RuntimeException(message)

private data class PromptUsage(
    val promptTokens: Int,
    val promptCacheHitTokens: Int,
    val promptCacheMissTokens: Int
)

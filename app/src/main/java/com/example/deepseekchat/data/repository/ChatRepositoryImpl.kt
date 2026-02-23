package com.example.deepseekchat.data.repository

import androidx.room.withTransaction
import com.example.deepseekchat.data.local.dao.MessageDao
import com.example.deepseekchat.data.local.dao.SessionDao
import com.example.deepseekchat.data.local.datastore.ActiveSessionPreferences
import com.example.deepseekchat.data.local.db.AppDatabase
import com.example.deepseekchat.data.local.entity.MessageEntity
import com.example.deepseekchat.data.local.entity.SessionEntity
import com.example.deepseekchat.data.local.mapper.toDomain
import com.example.deepseekchat.data.remote.api.DeepSeekApi
import com.example.deepseekchat.data.remote.dto.ChatCompletionMessage
import com.example.deepseekchat.data.remote.dto.ChatCompletionRequest
import com.example.deepseekchat.data.remote.stream.SseStreamParser
import com.example.deepseekchat.domain.model.ChatMessage
import com.example.deepseekchat.domain.model.ChatSession
import com.example.deepseekchat.domain.model.MessageRole
import com.example.deepseekchat.domain.repository.ChatRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    override suspend fun setActiveSession(sessionId: String) {
        activeSessionPreferences.setActiveSessionId(sessionId)
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

            database.withTransaction {
                sessionDao.insertSession(
                    session.copy(
                        title = resolvedTitle,
                        updatedAt = now
                    )
                )
                messageDao.insertMessage(
                    MessageEntity(
                        sessionId = sessionId,
                        role = MessageRole.USER.name,
                        content = cleanedContent,
                        createdAt = now
                    )
                )
            }

            val contextMessages = messageDao.getMessagesOnce(sessionId).map { message ->
                ChatCompletionMessage(
                    role = MessageRole.fromStored(message.role).apiValue,
                    content = message.content
                )
            }

            val request = ChatCompletionRequest(
                model = DEFAULT_MODEL,
                messages = contextMessages,
                stream = true
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

            sseStreamParser.streamText(responseBody).collect { partialText ->
                finalAssistantText = partialText
                emit(partialText)
            }

            if (finalAssistantText.isNotBlank()) {
                val doneTimestamp = System.currentTimeMillis()
                database.withTransaction {
                    messageDao.insertMessage(
                        MessageEntity(
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = finalAssistantText,
                            createdAt = doneTimestamp
                        )
                    )
                    sessionDao.touchSession(sessionId, doneTimestamp)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }.flowOn(Dispatchers.IO)

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

    private companion object {
        const val DEFAULT_SESSION_TITLE = "New chat"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val MAX_RAW_ERROR_LENGTH = 240
    }
}

private class ChatApiException(message: String) : RuntimeException(message)

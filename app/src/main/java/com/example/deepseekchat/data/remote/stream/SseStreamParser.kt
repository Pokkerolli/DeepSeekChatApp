package com.example.deepseekchat.data.remote.stream

import com.example.deepseekchat.data.remote.dto.ChatCompletionChunk
import com.example.deepseekchat.data.remote.dto.ChatCompletionUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody

class SseStreamParser(
    private val json: Json
) {
    fun streamEvents(body: ResponseBody): Flow<SseStreamEvent> = flow {
        body.use { responseBody ->
            val source = responseBody.source()
            val accumulated = StringBuilder()
            val eventPayload = StringBuilder()

            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break

                if (line.startsWith(":")) continue

                if (line.isEmpty()) {
                    consumeBufferedPayload(eventPayload, accumulated, emit = ::emit)
                    continue
                }

                if (!line.startsWith("data:")) continue
                val dataPart = line.substringAfter("data:").trimStart()

                if (dataPart == DONE_TOKEN) break

                if (consumePayloadIfComplete(dataPart, accumulated, emit = ::emit)) {
                    eventPayload.clear()
                    continue
                }

                if (eventPayload.isNotEmpty()) eventPayload.append('\n')
                eventPayload.append(dataPart)

                consumeBufferedPayload(eventPayload, accumulated, emit = ::emit)
            }

            consumeBufferedPayload(eventPayload, accumulated, emit = ::emit)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun consumeBufferedPayload(
        payloadBuffer: StringBuilder,
        accumulated: StringBuilder,
        emit: suspend (SseStreamEvent) -> Unit
    ) {
        val payload = payloadBuffer.toString().trim()
        if (payload.isEmpty() || payload == DONE_TOKEN) {
            payloadBuffer.clear()
            return
        }

        if (consumePayloadIfComplete(payload, accumulated, emit)) {
            payloadBuffer.clear()
        }
    }

    private suspend fun consumePayloadIfComplete(
        payload: String,
        accumulated: StringBuilder,
        emit: suspend (SseStreamEvent) -> Unit
    ): Boolean {
        val chunk = runCatching {
            json.decodeFromString(ChatCompletionChunk.serializer(), payload)
        }.getOrNull() ?: return false

        val piece = chunk.choices.joinToString(separator = "") { choice ->
            choice.delta.content.orEmpty()
        }

        if (piece.isNotEmpty()) {
            accumulated.append(piece)
            emit(SseStreamEvent.Text(accumulated.toString()))
        }

        chunk.usage?.let { usage ->
            emit(SseStreamEvent.Usage(usage))
        }

        return true
    }

    private companion object {
        const val DONE_TOKEN = "[DONE]"
    }
}

sealed interface SseStreamEvent {
    data class Text(val value: String) : SseStreamEvent
    data class Usage(val value: ChatCompletionUsage) : SseStreamEvent
}

package com.example.deepseekchat.data.remote.api

import com.example.deepseekchat.data.remote.dto.ChatCompletionRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface DeepSeekApi {
    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletions(
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>
}

package com.intellij.lm.copilot

import com.intellij.lm.LmChatMessage
import com.intellij.lm.LmChatModel
import com.intellij.lm.LmChatRequestOptions
import com.intellij.lm.LmChatResponse
import com.intellij.lm.StreamingLmChatResponse
import kotlinx.coroutines.flow.flow

class CopilotChatModel(
    override val id: String,
    override val name: String,
    override val family: String,
    override val maxInputTokens: Int
) : LmChatModel {

    override val vendor = "copilot"

    override suspend fun sendRequest(
        messages: List<LmChatMessage>,
        options: LmChatRequestOptions
    ): LmChatResponse {
        return StreamingLmChatResponse(flow {
            val client = CopilotHttpClient.getInstance()
            val response = client.chatCompletion(id, messages, options)
            emit(response)
        })
    }
}

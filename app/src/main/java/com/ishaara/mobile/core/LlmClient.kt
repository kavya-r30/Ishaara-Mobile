package com.ishaara.mobile.core

import kotlinx.serialization.json.JsonArray

data class ToolCall(val id: String, val name: String, val argumentsJson: String)

data class ChatMessage(
    val role: String,
    val text: String? = null,
    val imageJpegBase64: String? = null,
    val toolCall: ToolCall? = null,
    val toolCallId: String? = null,
) {
    companion object {
        fun system(text: String) = ChatMessage("system", text)
        fun user(text: String, imageJpegBase64: String? = null) = ChatMessage("user", text, imageJpegBase64)
        fun assistant(call: ToolCall) = ChatMessage("assistant", toolCall = call)
        fun tool(call: ToolCall, text: String) = ChatMessage("tool", text, toolCallId = call.id)
    }
}

class LlmException(message: String) : Exception(message)

interface LlmClient {
    /** Returns the model's single tool call. Throws [LlmException] on failure. */
    suspend fun complete(messages: List<ChatMessage>, tools: JsonArray): ToolCall
}

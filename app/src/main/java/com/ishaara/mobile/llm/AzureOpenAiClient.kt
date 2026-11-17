package com.ishaara.mobile.llm

import com.ishaara.mobile.core.ChatMessage
import com.ishaara.mobile.core.LlmClient
import com.ishaara.mobile.core.LlmException
import com.ishaara.mobile.core.ToolCall
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class AzureConfig(
    val endpoint: String,
    val deployment: String,
    val apiVersion: String,
    val apiKey: String,
) {
    val isComplete: Boolean
        get() = endpoint.isNotBlank() && deployment.isNotBlank() && apiVersion.isNotBlank() && apiKey.isNotBlank()
}

data class ConnectionTest(val textOk: Boolean, val visionOk: Boolean, val message: String)

class AzureOpenAiClient(
    private val http: OkHttpClient,
    private val config: suspend () -> AzureConfig,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : LlmClient {

    override suspend fun complete(messages: List<ChatMessage>, tools: JsonArray): ToolCall {
        val cfg = config()
        if (!cfg.isComplete) throw LlmException("Azure isn't set up yet. Add it in Settings.")
        val url = cfg.endpoint.trimEnd('/').toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("openai/deployments/${cfg.deployment}/chat/completions")
            ?.addQueryParameter("api-version", cfg.apiVersion)
            ?.build()
            ?: throw LlmException("The Azure endpoint isn't valid. Check it in Settings.")
        val request = Request.Builder()
            .url(url)
            .header("api-key", cfg.apiKey)
            .post(body(messages, tools).toString().toRequestBody(JSON))
            .build()

        var attempt = 0
        while (true) {
            val response = try {
                http.newCall(request).await()
            } catch (e: IOException) {
                if (attempt >= MAX_RETRIES) throw LlmException("Can't reach the network. Check your connection.")
                sleep(backoff(attempt))
                attempt++
                continue
            }
            response.use {
                val code = it.code
                when {
                    it.isSuccessful -> return parse(it.body?.string().orEmpty())
                    code == 401 || code == 403 || code == 404 ->
                        throw LlmException("Azure rejected the request ($code). Check the connection in Settings.")
                    (code == 429 || code >= 500) && attempt < MAX_RETRIES -> {
                        val retryAfter = it.header("Retry-After")?.toLongOrNull()?.times(1000)
                        sleep(retryAfter ?: backoff(attempt))
                    }
                    code == 429 -> throw LlmException("Azure is rate limiting requests. Try again in a minute.")
                    else -> throw LlmException("Azure returned an error ($code).")
                }
            }
            attempt++
        }
    }

    /** Sends a text call, then one with [sampleJpegBase64] to find out whether the deployment accepts images. */
    suspend fun testConnection(sampleJpegBase64: String): ConnectionTest {
        val prompt = listOf(ChatMessage.user("Call the ping tool."))
        try {
            complete(prompt, PING_TOOLS)
        } catch (e: LlmException) {
            return ConnectionTest(false, false, e.message ?: "Connection failed.")
        }
        return try {
            complete(listOf(ChatMessage.user("Call the ping tool.", sampleJpegBase64)), PING_TOOLS)
            ConnectionTest(true, true, "Connected. Screenshots are supported.")
        } catch (e: LlmException) {
            ConnectionTest(true, false, "Connected. This model doesn't take images, so screenshots stay off.")
        }
    }

    private fun backoff(attempt: Int): Long = 1000L * (attempt + 1)

    private fun body(messages: List<ChatMessage>, tools: JsonArray): JsonObject = buildJsonObject {
        putJsonArray("messages") {
            messages.forEach { m -> add(encode(m)) }
        }
        put("tools", tools)
        put("tool_choice", "required")
        put("parallel_tool_calls", false)
        put("max_completion_tokens", MAX_TOKENS)
    }

    private fun encode(m: ChatMessage): JsonObject = buildJsonObject {
        put("role", m.role)
        when {
            m.toolCall != null -> putJsonArray("tool_calls") {
                addJsonObject {
                    put("id", m.toolCall.id)
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", m.toolCall.name)
                        put("arguments", m.toolCall.argumentsJson)
                    }
                }
            }
            m.imageJpegBase64 != null -> putJsonArray("content") {
                addJsonObject {
                    put("type", "text")
                    put("text", m.text.orEmpty())
                }
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", "data:image/jpeg;base64,${m.imageJpegBase64}") }
                }
            }
            else -> put("content", m.text.orEmpty())
        }
        m.toolCallId?.let { put("tool_call_id", it) }
    }

    private fun parse(raw: String): ToolCall {
        try {
            val message = Json.parseToJsonElement(raw).jsonObject
                .getValue("choices").jsonArray[0].jsonObject
                .getValue("message").jsonObject
            val call = message["tool_calls"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw LlmException("The assistant didn't choose an action.")
            val function = call.getValue("function").jsonObject
            return ToolCall(
                id = call.getValue("id").jsonPrimitive.content,
                name = function.getValue("name").jsonPrimitive.content,
                argumentsJson = function.getValue("arguments").jsonPrimitive.content,
            )
        } catch (e: IllegalArgumentException) {
            throw LlmException("The assistant's reply couldn't be read.")
        } catch (e: NoSuchElementException) {
            throw LlmException("The assistant's reply couldn't be read.")
        } catch (e: IndexOutOfBoundsException) {
            throw LlmException("The assistant's reply couldn't be read.")
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val MAX_RETRIES = 2
        const val MAX_TOKENS = 1500
        val PING_TOOLS: JsonArray = Json.parseToJsonElement(
            """[{"type":"function","function":{"name":"ping","description":"Connectivity check.","parameters":{"type":"object","properties":{}}}}]""",
        ).jsonArray
    }
}

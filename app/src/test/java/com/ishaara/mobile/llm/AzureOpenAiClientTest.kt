package com.ishaara.mobile.llm

import com.ishaara.mobile.core.ChatMessage
import com.ishaara.mobile.core.LlmException
import com.ishaara.mobile.core.ToolCall
import com.ishaara.mobile.core.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureOpenAiClientTest {
    private val server = MockWebServer().apply { start() }
    private val sleeps = mutableListOf<Long>()
    private val client = AzureOpenAiClient(
        OkHttpClient(),
        { AzureConfig(server.url("/").toString(), "gpt", "2025-04-01-preview", "secret") },
        { sleeps += it },
    )

    private val toolResponse = """
        {"choices":[{"message":{"tool_calls":[{"id":"c1","type":"function","function":{"name":"tap","arguments":"{\"element\":1}"}}]}}]}
    """.trimIndent()

    @After
    fun tearDown() = server.shutdown()

    private fun sent(): JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test
    fun sendsAzureRequestAndParsesToolCall() = runBlocking {
        server.enqueue(MockResponse().setBody(toolResponse))
        val call = client.complete(listOf(ChatMessage.user("hi")), ToolSchema.EXECUTOR)
        assertEquals(ToolCall("c1", "tap", """{"element":1}"""), call)

        val request = server.takeRequest()
        assertEquals("/openai/deployments/gpt/chat/completions?api-version=2025-04-01-preview", request.path)
        assertEquals("secret", request.getHeader("api-key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("false", body["parallel_tool_calls"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("max_completion_tokens"))
        assertFalse(body.containsKey("temperature"))
        assertFalse(body.containsKey("max_tokens"))
    }

    @Test
    fun encodesImageAsContentParts() = runBlocking {
        server.enqueue(MockResponse().setBody(toolResponse))
        client.complete(listOf(ChatMessage.user("look", "abc")), JsonArray(emptyList()))
        val content = sent()["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("look", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        val url = content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("data:image/jpeg;base64,abc", url)
    }

    @Test
    fun encodesToolRoundTrip() = runBlocking {
        server.enqueue(MockResponse().setBody(toolResponse))
        val call = ToolCall("c9", "tap", """{"element":2}""")
        client.complete(listOf(ChatMessage.assistant(call), ChatMessage.tool(call, "OK")), JsonArray(emptyList()))
        val messages = sent()["messages"]!!.jsonArray
        val toolCall = messages[0].jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("c9", toolCall["id"]!!.jsonPrimitive.content)
        assertEquals("tap", toolCall["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("c9", messages[1].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("OK", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun retriesRateLimitHonouringRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7"))
        server.enqueue(MockResponse().setBody(toolResponse))
        client.complete(listOf(ChatMessage.user("hi")), JsonArray(emptyList()))
        assertEquals(listOf(7000L), sleeps)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun retriesServerErrorsThenGivesUp() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        assertThrows(LlmException::class.java) {
            runBlocking { client.complete(listOf(ChatMessage.user("hi")), JsonArray(emptyList())) }
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun authFailureDoesNotRetryAndPointsToSettings() {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = assertThrows(LlmException::class.java) {
            runBlocking { client.complete(listOf(ChatMessage.user("hi")), JsonArray(emptyList())) }
        }
        assertEquals(1, server.requestCount)
        assertTrue(e.message!!.contains("Settings"))
    }

    @Test
    fun missingToolCallIsAnError() {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"hello"}}]}"""))
        assertThrows(LlmException::class.java) {
            runBlocking { client.complete(listOf(ChatMessage.user("hi")), JsonArray(emptyList())) }
        }
    }

    @Test
    fun incompleteConfigFailsWithoutCallingServer() {
        val empty = AzureOpenAiClient(OkHttpClient(), { AzureConfig("", "", "", "") }, {})
        val e = assertThrows(LlmException::class.java) {
            runBlocking { empty.complete(listOf(ChatMessage.user("hi")), JsonArray(emptyList())) }
        }
        assertTrue(e.message!!.contains("Settings"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun connectionTestDetectsVisionSupport() = runBlocking {
        server.enqueue(MockResponse().setBody(toolResponse))
        server.enqueue(MockResponse().setBody(toolResponse))
        val result = client.testConnection("abc")
        assertTrue(result.textOk)
        assertTrue(result.visionOk)
    }

    @Test
    fun connectionTestReportsNoVision() = runBlocking {
        server.enqueue(MockResponse().setBody(toolResponse))
        server.enqueue(MockResponse().setResponseCode(400))
        val result = client.testConnection("abc")
        assertTrue(result.textOk)
        assertFalse(result.visionOk)
    }

    @Test
    fun connectionTestReportsTextFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.testConnection("abc")
        assertFalse(result.textOk)
        assertFalse(result.visionOk)
    }
}

package com.spendlens.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [OpenRouter] request building and response parsing. */
class OpenRouterTest {

    @Test fun `request body carries model and a single user message`() {
        val body = OpenRouter.buildRequestBody("deepseek/deepseek-chat-v3-0324:free", "hello world")
        val root = JSONObject(body)
        assertEquals("deepseek/deepseek-chat-v3-0324:free", root.getString("model"))
        val messages = root.getJSONArray("messages")
        assertEquals(1, messages.length())
        val msg = messages.getJSONObject(0)
        assertEquals("user", msg.getString("role"))
        assertEquals("hello world", msg.getString("content"))
    }

    @Test fun `request body escapes quotes and newlines so it round-trips`() {
        val tricky = "line1 \"quoted\"\nline2 \\ tail"
        val body = OpenRouter.buildRequestBody("m", tricky)
        val root = JSONObject(body)
        assertEquals(tricky, root.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun `parseContent extracts assistant text`() {
        val resp = """{"choices":[{"message":{"role":"assistant","content":"the answer"}}]}"""
        assertEquals("the answer", OpenRouter.parseContent(resp))
    }

    @Test fun `parseContent returns null for error envelope`() {
        val resp = """{"error":{"message":"invalid api key","code":401}}"""
        assertNull(OpenRouter.parseContent(resp))
    }

    @Test fun `parseContent returns null for empty choices`() {
        assertNull(OpenRouter.parseContent("""{"choices":[]}"""))
    }

    @Test fun `parseContent returns null for blank content`() {
        assertNull(OpenRouter.parseContent("""{"choices":[{"message":{"content":""}}]}"""))
    }

    @Test fun `parseContent returns null for malformed or empty input`() {
        assertNull(OpenRouter.parseContent("not json"))
        assertNull(OpenRouter.parseContent(""))
        assertNull(OpenRouter.parseContent(null))
    }

    @Test fun `parseModels extracts sorted distinct slugs from data ids`() {
        val resp = """{"data":[{"id":"openai/gpt-latest"},{"id":"deepseek/chat:free"},{"id":"openai/gpt-latest"}]}"""
        assertEquals(listOf("deepseek/chat:free", "openai/gpt-latest"), OpenRouter.parseModels(resp))
    }

    @Test fun `parseModels skips blank ids`() {
        val resp = """{"data":[{"id":""},{"id":"a/b"},{"name":"no id here"}]}"""
        assertEquals(listOf("a/b"), OpenRouter.parseModels(resp))
    }

    @Test fun `parseModels returns empty for malformed or missing data`() {
        assertTrue(OpenRouter.parseModels("not json").isEmpty())
        assertTrue(OpenRouter.parseModels("""{"foo":1}""").isEmpty())
        assertTrue(OpenRouter.parseModels("").isEmpty())
        assertTrue(OpenRouter.parseModels(null).isEmpty())
    }
}

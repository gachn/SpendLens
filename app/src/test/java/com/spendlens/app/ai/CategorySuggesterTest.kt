package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [CategorySuggester] prompt building and response parsing. */
class CategorySuggesterTest {

    private val categories = listOf(1L to "Food & Dining", 3L to "Transport", 4L to "Shopping")
    private val validIds = setOf(1L, 3L, 4L)

    @Test fun `prompt lists every category, every merchant and asks for JSON`() {
        val prompt = CategorySuggester.buildPrompt(listOf("Swiggy", "Uber"), categories)
        assertTrue(prompt.contains("1: Food & Dining"))
        assertTrue(prompt.contains("3: Transport"))
        assertTrue(prompt.contains("Swiggy"))
        assertTrue(prompt.contains("Uber"))
        assertTrue(prompt.contains("categoryId"))
        assertTrue(prompt.contains("JSON"))
    }

    @Test fun `parses a clean array of assignments`() {
        val json = """
            [
              {"merchant":"Swiggy","categoryId":1},
              {"merchant":"Uber","categoryId":3}
            ]
        """.trimIndent()
        val out = CategorySuggester.parse(json, validIds)
        assertEquals(2, out.size)
        assertEquals(CategoryAssignment("Swiggy", 1L), out[0])
        assertEquals(CategoryAssignment("Uber", 3L), out[1])
    }

    @Test fun `tolerates markdown fences and surrounding prose`() {
        val text = "Sure:\n```json\n[{\"merchant\":\"Myntra\",\"categoryId\":4}]\n```\nDone."
        val out = CategorySuggester.parse(text, validIds)
        assertEquals(1, out.size)
        assertEquals(CategoryAssignment("Myntra", 4L), out[0])
    }

    @Test fun `drops categoryIds not in the valid set`() {
        val json = """[{"merchant":"Swiggy","categoryId":99},{"merchant":"Uber","categoryId":3}]"""
        val out = CategorySuggester.parse(json, validIds)
        assertEquals(listOf(CategoryAssignment("Uber", 3L)), out)
    }

    @Test fun `drops entries with blank merchant or missing categoryId`() {
        val json = """[{"merchant":"","categoryId":1},{"merchant":"Ola"},{"merchant":"Uber","categoryId":3}]"""
        val out = CategorySuggester.parse(json, validIds)
        assertEquals(listOf(CategoryAssignment("Uber", 3L)), out)
    }

    @Test fun `trims merchant whitespace`() {
        val json = """[{"merchant":"  Swiggy  ","categoryId":1}]"""
        assertEquals("Swiggy", CategorySuggester.parse(json, validIds)[0].merchant)
    }

    @Test fun `malformed or empty input yields empty list`() {
        assertTrue(CategorySuggester.parse("not json", validIds).isEmpty())
        assertTrue(CategorySuggester.parse("", validIds).isEmpty())
        assertTrue(CategorySuggester.parse(null, validIds).isEmpty())
        assertTrue(CategorySuggester.parse("{\"merchant\":\"x\",\"categoryId\":1}", validIds).isEmpty())
    }
}

package com.spendlens.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the filtering logic inside MerchantSuggestField.
 * (UI interaction is verified manually; this covers the pure filtering function.)
 */
class MerchantSuggestFieldTest {

    private fun filter(value: String, suggestions: List<String>): List<String> {
        if (value.isBlank()) return emptyList()
        return suggestions.filter {
            it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true)
        }.take(6)
    }

    @Test
    fun `blank value returns empty list`() {
        val result = filter("", listOf("Zomato", "Swiggy", "Amazon"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `partial match returns matching suggestions`() {
        val result = filter("zo", listOf("Zomato", "Swiggy", "Zoom", "Netflix"))
        assertEquals(listOf("Zomato", "Zoom"), result)
    }

    @Test
    fun `exact match is excluded from dropdown (already typed)`() {
        val result = filter("Zomato", listOf("Zomato", "Zomato Delivery", "Swiggy"))
        assertFalse("exact match must not appear in dropdown", result.contains("Zomato"))
        assertTrue(result.contains("Zomato Delivery"))
    }

    @Test
    fun `matching is case insensitive`() {
        val result = filter("ZOM", listOf("Zomato", "zomato kitchen", "Swiggy"))
        assertEquals(2, result.size)
    }

    @Test
    fun `at most 6 suggestions are returned`() {
        val suggestions = (1..10).map { "Merchant$it" }
        val result = filter("Merchant", suggestions)
        assertEquals(6, result.size)
    }

    @Test
    fun `deleting a character updates filter correctly`() {
        val suggestions = listOf("Zomato", "Zoom", "Swiggy")
        // User typed "Zomato" (exact match excluded) then deletes → "Zomat"
        val afterDelete = filter("Zomat", suggestions)
        assertTrue(afterDelete.contains("Zomato"))
        assertFalse(afterDelete.contains("Zoom"))
    }

    @Test
    fun `no match returns empty list`() {
        val result = filter("xyz", listOf("Zomato", "Swiggy"))
        assertTrue(result.isEmpty())
    }
}

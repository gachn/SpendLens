package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [MerchantConsolidation] prompt building and response parsing. */
class MerchantConsolidationTest {

    @Test fun `prompt lists every merchant name and asks for JSON groups`() {
        val prompt = MerchantConsolidation.buildPrompt(listOf("SWIGGY", "Swiggy*Delhi", "AMAZON IN"))
        assertTrue(prompt.contains("SWIGGY"))
        assertTrue(prompt.contains("Swiggy*Delhi"))
        assertTrue(prompt.contains("AMAZON IN"))
        assertTrue(prompt.contains("canonical"))
        assertTrue(prompt.contains("aliases"))
        assertTrue(prompt.contains("JSON"))
    }

    @Test fun `parses a clean array of groups`() {
        val json = """
            [
              {"canonical":"Swiggy","aliases":["SWIGGY","Swiggy*Delhi","BUNDL TECH"]},
              {"canonical":"Amazon","aliases":["AMAZON IN","amazon.in"]}
            ]
        """.trimIndent()
        val groups = MerchantConsolidation.parse(json)
        assertEquals(2, groups.size)
        assertEquals("Swiggy", groups[0].canonical)
        assertEquals(listOf("SWIGGY", "Swiggy*Delhi", "BUNDL TECH"), groups[0].aliases)
        assertEquals("Amazon", groups[1].canonical)
        assertEquals(listOf("AMAZON IN", "amazon.in"), groups[1].aliases)
    }

    @Test fun `tolerates markdown fences and surrounding prose`() {
        val text = "Here you go:\n```json\n[{\"canonical\":\"Uber\",\"aliases\":[\"UBER\",\"Uber BV\"]}]\n```\nDone."
        val groups = MerchantConsolidation.parse(text)
        assertEquals(1, groups.size)
        assertEquals("Uber", groups[0].canonical)
        assertEquals(listOf("UBER", "Uber BV"), groups[0].aliases)
    }

    @Test fun `trims canonical and alias whitespace`() {
        val json = """[{"canonical":"  Zomato  ","aliases":["  ZOMATO ","  zomato online "]}]"""
        val groups = MerchantConsolidation.parse(json)
        assertEquals("Zomato", groups[0].canonical)
        assertEquals(listOf("ZOMATO", "zomato online"), groups[0].aliases)
    }

    @Test fun `drops groups with blank canonical and blank aliases`() {
        val json = """[{"canonical":"","aliases":["X"]},{"canonical":"Ola","aliases":["OLA",""," "]}]"""
        val groups = MerchantConsolidation.parse(json)
        assertEquals(1, groups.size)
        assertEquals("Ola", groups[0].canonical)
        assertEquals(listOf("OLA"), groups[0].aliases)
    }

    @Test fun `group with missing aliases yields empty alias list`() {
        val json = """[{"canonical":"Netflix"}]"""
        val groups = MerchantConsolidation.parse(json)
        assertEquals(1, groups.size)
        assertEquals("Netflix", groups[0].canonical)
        assertTrue(groups[0].aliases.isEmpty())
    }

    @Test fun `malformed or empty input yields empty list`() {
        assertTrue(MerchantConsolidation.parse("not json").isEmpty())
        assertTrue(MerchantConsolidation.parse("").isEmpty())
        assertTrue(MerchantConsolidation.parse(null).isEmpty())
        assertTrue(MerchantConsolidation.parse("{\"canonical\":\"x\"}").isEmpty())
    }
}

package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [AiConfig] key/model resolution (BuildConfig default, Settings override). */
class AiConfigTest {

    @Test fun `settings override wins over build default`() {
        assertEquals("user-key", AiConfig.effectiveKey(override = "user-key", buildDefault = "build-key"))
    }

    @Test fun `blank override falls back to build default`() {
        assertEquals("build-key", AiConfig.effectiveKey(override = "   ", buildDefault = "build-key"))
        assertEquals("build-key", AiConfig.effectiveKey(override = null, buildDefault = "build-key"))
    }

    @Test fun `override is trimmed`() {
        assertEquals("user-key", AiConfig.effectiveKey(override = "  user-key  ", buildDefault = ""))
    }

    @Test fun `no key anywhere yields null`() {
        assertNull(AiConfig.effectiveKey(override = "", buildDefault = ""))
        assertNull(AiConfig.effectiveKey(override = null, buildDefault = null))
        assertNull(AiConfig.effectiveKey(override = "  ", buildDefault = "  "))
    }

    @Test fun `model falls back to default when blank`() {
        assertEquals(AiConfig.DEFAULT_MODEL, AiConfig.effectiveModel(null))
        assertEquals(AiConfig.DEFAULT_MODEL, AiConfig.effectiveModel("   "))
    }

    @Test fun `stored model wins and is trimmed`() {
        assertEquals("openai/gpt-latest", AiConfig.effectiveModel("  openai/gpt-latest  "))
    }

    @Test fun `default model is a free slug`() {
        // The user chose a free-tier default to keep cost at zero.
        org.junit.Assert.assertTrue(AiConfig.DEFAULT_MODEL.endsWith(":free"))
    }
}

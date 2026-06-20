package com.spendlens.app.parser

import com.spendlens.app.data.MerchantDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantParsingTest {

    // --- AccountExtractor ---

    @Test
    fun `card tail from various formats`() {
        assertEquals("••••9999", AccountExtractor.extract("Spent Rs.2000 on card XX9999 at STARBUCKS"))
        assertEquals("••••1234", AccountExtractor.extract("Txn on card ending with 1234 done"))
        assertEquals("••••5678", AccountExtractor.extract("Rs.300 debited from a/c XXXXXX5678 on 1 Jun"))
    }

    @Test
    fun `no card context yields null`() {
        assertNull(AccountExtractor.extract("INR 320.50 debited for ELECTRICITY bill"))
    }

    // --- MerchantExtractor ---

    @Test
    fun `extracts upi vpa`() {
        assertEquals("john@okhdfc", MerchantExtractor.extract("Rs.500 sent to john@okhdfc via UPI Ref 123"))
    }

    @Test
    fun `extracts pos merchant after at`() {
        assertEquals("STARBUCKS", MerchantExtractor.extract("Spent Rs.2000 on card XX9999 at STARBUCKS on 12-06"))
    }

    @Test
    fun `extracts merchant from debit alert`() {
        assertEquals("AMAZON", MerchantExtractor.extract("Rs.1,250.00 debited from a/c XX1234 at AMAZON. Avl bal Rs.5,000"))
    }

    // --- MerchantNormalizer + Dictionary ---

    @Test
    fun `normalizer builds key and display`() {
        assertEquals("amazon", MerchantNormalizer.key("AMAZON PAY INDIA"))
        assertEquals("Starbucks", MerchantNormalizer.display("STARBUCKS"))
        assertEquals("John Doe", MerchantNormalizer.display("john.doe"))
    }

    @Test
    fun `dictionary resolves brands including single-token vpas`() {
        assertEquals("Swiggy", MerchantDictionary.lookup(MerchantNormalizer.key("swiggystores@hdfcbank")))
        assertEquals("Amazon", MerchantDictionary.lookup(MerchantNormalizer.key("AMAZON PAY INDIA")))
        assertNull(MerchantDictionary.lookup("zzzunknownmerchant"))
    }

    @Test
    fun `key drops noise words`() {
        assertTrue(MerchantNormalizer.key("THE COFFEE CO PVT LTD").isNotBlank())
        assertEquals("coffee", MerchantNormalizer.key("THE COFFEE CO PVT LTD"))
    }
}

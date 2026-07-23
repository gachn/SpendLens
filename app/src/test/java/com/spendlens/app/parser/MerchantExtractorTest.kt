package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantExtractorTest {

    @Test fun `FASTag toll SMS excludes the trailing vehicle number from the merchant`() {
        val body = "Rs.90 paid at ForumShantiniketanMall for KA01NE2494 on 18-07-2026 17:48:52 " +
            "with ICICI Bank FASTag. Bal Rs.18. Call 18002100104 for dispute"
        assertEquals("ForumShantiniketanMall", MerchantExtractor.extract(body))
    }

    @Test fun `autopay mandate debit picks the real counterparty after towards, not the own account after from`() {
        val body = "Rs 169.00 debited from ICICI Bank Savings Account XX336 on 19-Jul-26 towards " +
            "APPLE MEDIA SER for Create Mandate AutoPay Retrieval Ref No.103692464814"
        assertEquals("APPLE MEDIA SER", MerchantExtractor.extract(body))
    }

    @Test fun `plain at-merchant without a trailing for clause still works`() {
        val body = "Spent Rs.2000 on card XX9999 at STARBUCKS on 12-Jul-26"
        assertEquals("STARBUCKS", MerchantExtractor.extract(body))
    }

    @Test fun `plain P2P transfer with no towards clause still resolves via from`() {
        val body = "Rs 500.00 received from JOHN DOE on 12-Jul-26 Ref 123456"
        assertEquals("JOHN DOE", MerchantExtractor.extract(body))
    }

    @Test fun `unrecognisable body returns null`() {
        assertNull(MerchantExtractor.extract("Your OTP is 445566, valid for 10 minutes."))
    }
}

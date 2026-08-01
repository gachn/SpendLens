package com.spendlens.app.ui.theme

import androidx.compose.ui.graphics.Color

data class BankBrand(
    val primary: Color,
    val secondary: Color,
    val onCard: Color = Color.White,
)

/**
 * Brand colors sourced from official brand guidelines for each bank/fintech.
 * Matched by substring of accountKey (case-insensitive).
 */
object BankBranding {

    private val catalog = listOf(
        // ── Public sector banks ────────────────────────────────────────────────
        Pair("STATE BANK", BankBrand(Color(0xFF1A4A8E), Color(0xFF2E87D4))),
        Pair("SBI",        BankBrand(Color(0xFF1A4A8E), Color(0xFF2E87D4))),
        Pair("PNB",        BankBrand(Color(0xFF00437C), Color(0xFFD97B2B))),
        Pair("PUNJAB NATIONAL", BankBrand(Color(0xFF00437C), Color(0xFFD97B2B))),
        Pair("BANK OF BARODA", BankBrand(Color(0xFF1C2B59), Color(0xFFF47920))),
        Pair("BOB",        BankBrand(Color(0xFF1C2B59), Color(0xFFF47920))),
        Pair("CANARA",     BankBrand(Color(0xFF1D3C6C), Color(0xFFE8971A))),
        Pair("UNION BANK", BankBrand(Color(0xFF1B3A6E), Color(0xFFCF1B2B))),
        Pair("BANK OF INDIA", BankBrand(Color(0xFF0D2A6E), Color(0xFFE89A18))),
        Pair("BOI",        BankBrand(Color(0xFF0D2A6E), Color(0xFFE89A18))),
        Pair("INDIAN BANK",BankBrand(Color(0xFF0033A0), Color(0xFFC8A200))),
        Pair("CENTRAL BANK", BankBrand(Color(0xFF003E7E), Color(0xFFD4312A))),
        Pair("UCO BANK",   BankBrand(Color(0xFF003580), Color(0xFFD96B00))),
        Pair("ALLAHABAD",  BankBrand(Color(0xFF003580), Color(0xFFC04000))),
        Pair("CORPORATION", BankBrand(Color(0xFF003A78), Color(0xFFD05C00))),

        // ── Large private banks ────────────────────────────────────────────────
        Pair("HDFC",       BankBrand(Color(0xFF003D8F), Color(0xFFCC1B28))),
        Pair("ICICI",      BankBrand(Color(0xFF7B1A1E), Color(0xFFD97829))),
        Pair("AXIS",       BankBrand(Color(0xFF8B0D42), Color(0xFFD41250))),
        Pair("KOTAK",      BankBrand(Color(0xFFCC2A20), Color(0xFF8B1D18))),
        Pair("YES BANK",   BankBrand(Color(0xFF003087), Color(0xFF0096D6))),
        Pair("INDUSIND",   BankBrand(Color(0xFF5A2478), Color(0xFFD45600))),
        Pair("FEDERAL",    BankBrand(Color(0xFF1A3764), Color(0xFFCF211C))),
        Pair("RBL",        BankBrand(Color(0xFF1A3560), Color(0xFFD41F24))),
        Pair("IDFC",       BankBrand(Color(0xFF003087), Color(0xFF0099C2))),
        Pair("IDBI",       BankBrand(Color(0xFF003087), Color(0xFF4499D4))),
        Pair("SOUTH INDIAN", BankBrand(Color(0xFF004D8F), Color(0xFFD48018))),
        Pair("KARNATAKA",  BankBrand(Color(0xFF1A4A8A), Color(0xFFD09018))),
        Pair("CITY UNION", BankBrand(Color(0xFF003366), Color(0xFFD48C00))),
        Pair("DCB",        BankBrand(Color(0xFF003B6E), Color(0xFFD47A00))),
        Pair("LAKSHMI VILAS", BankBrand(Color(0xFF8B0000), Color(0xFFD4A020))),
        Pair("BANDHAN",    BankBrand(Color(0xFF005B96), Color(0xFFE8A018))),
        Pair("AU SMALL",   BankBrand(Color(0xFF004080), Color(0xFFCC2C1A))),
        Pair("EQUITAS",    BankBrand(Color(0xFF00557A), Color(0xFFE57A00))),

        // ── Fintech / neobanks ─────────────────────────────────────────────────
        Pair("PAYTM",      BankBrand(Color(0xFF002970), Color(0xFF0099CC))),
        Pair("PHONEPE",    BankBrand(Color(0xFF5A1F96), Color(0xFF03C27B))),
        Pair("PHONE PE",   BankBrand(Color(0xFF5A1F96), Color(0xFF03C27B))),
        Pair("GOOGLEPAY",  BankBrand(Color(0xFF1A5CB8), Color(0xFF28A745))),
        Pair("GOOGLE PAY", BankBrand(Color(0xFF1A5CB8), Color(0xFF28A745))),
        Pair("GPAY",       BankBrand(Color(0xFF1A5CB8), Color(0xFF28A745))),
        Pair("AMAZON PAY", BankBrand(Color(0xFF131921), Color(0xFFFF9900))),
        Pair("RAZORPAY",   BankBrand(Color(0xFF2D3FC5), Color(0xFF4A7FF5))),
        Pair("CRED",       BankBrand(Color(0xFF120220), Color(0xFF9B4DFA))),
        Pair("SLICE",      BankBrand(Color(0xFF1A0533), Color(0xFF7B3AC9))),
        Pair("JUPITER",    BankBrand(Color(0xFF1E0A4A), Color(0xFF7C4DFF))),
        Pair("NAVI",       BankBrand(Color(0xFF0D0F1A), Color(0xFF6B3BE8))),
        Pair("FI MONEY",   BankBrand(Color(0xFF1B2A56), Color(0xFF00C29E))),
        Pair("NIYO",       BankBrand(Color(0xFF1B5E20), Color(0xFF4CAF50))),
        Pair("ONECARD",    BankBrand(Color(0xFF101010), Color(0xFFB8B8B8), onCard = Color(0xFFE0E0E0))),
        Pair("ONE CARD",   BankBrand(Color(0xFF101010), Color(0xFFB8B8B8), onCard = Color(0xFFE0E0E0))),
        Pair("FREO",       BankBrand(Color(0xFF1A3050), Color(0xFF2AA8C4))),
        Pair("FAMPAY",     BankBrand(Color(0xFFFFCC00), Color(0xFFFF6B00), onCard = Color(0xFF1A1A1A))),
        Pair("EPIFI",      BankBrand(Color(0xFF1B2A56), Color(0xFF00C29E))),
        // International banks present in India
        Pair("HSBC",       BankBrand(Color(0xFFDB0011), Color(0xFF8B000A))),
        Pair("AMEX",       BankBrand(Color(0xFF006AC3), Color(0xFF003D73))),
        Pair("AMERICAN EXPRESS", BankBrand(Color(0xFF006AC3), Color(0xFF003D73))),
        Pair("CITI",       BankBrand(Color(0xFF003B8E), Color(0xFFE31837))),
        Pair("CITIBANK",   BankBrand(Color(0xFF003B8E), Color(0xFFE31837))),
        Pair("STANDARD CHARTERED", BankBrand(Color(0xFF0A3B5E), Color(0xFF00A19A))),
        Pair("STANCHART",  BankBrand(Color(0xFF0A3B5E), Color(0xFF00A19A))),
        Pair("DBS",        BankBrand(Color(0xFFD40511), Color(0xFF9C0000))),
        Pair("DCB",        BankBrand(Color(0xFF004B87), Color(0xFFE8600A))),
        Pair("DEUTSCHE",   BankBrand(Color(0xFF003189), Color(0xFF0018A8))),
    )

    private val fallback = BankBrand(Color(0xFF1C3A5E), Color(0xFF2D6FA8))

    /**
     * Indian telecom sender codes: the suffix after "-" (e.g. "VK-HDFCBK" → "HDFCBK")
     * mapped to the bank name key used in [catalog].
     */
    private val senderCodes = mapOf(
        // HDFC — confirmed: AD-HDFCBK, AD-HDFCBK-S
        "HDFCBK" to "HDFC", "HDFCBN" to "HDFC",
        // ICICI — confirmed: JD-ICICIT, AD-ICICIT-S  (real code is ICICIT not ICICIB)
        "ICICIT" to "ICICI", "ICICIB" to "ICICI", "ICICIN" to "ICICI",
        // SBI — confirmed: VA-SBIPSG
        "SBIPSG" to "STATE BANK", "SBIINB" to "STATE BANK", "SBICRD" to "STATE BANK",
        "SBIUPI" to "STATE BANK", "SBISMS" to "STATE BANK",
        // Axis Bank — confirmed: AX-AXISBK-S  (NOT AXISMF which is Axis Mutual Fund)
        "AXISBK" to "AXIS", "AXISBN" to "AXIS",
        // Kotak
        "KOTAKB" to "KOTAK", "KOTAKN" to "KOTAK",
        // Yes Bank
        "YESBNK" to "YES BANK", "YESBK" to "YES BANK",
        // IndusInd
        "INDUSB" to "INDUSIND", "INDUSL" to "INDUSIND",
        // PNB
        "PNBSMS" to "PNB", "PUNBNK" to "PNB",
        // Bank of Baroda
        "BOBSMS" to "BANK OF BARODA", "BOBBNK" to "BANK OF BARODA",
        // Canara
        "CANBNK" to "CANARA", "CANBKN" to "CANARA",
        // Union Bank
        "UBISMS" to "UNION BANK", "UNIONB" to "UNION BANK",
        // IDFC
        "IDFCBK" to "IDFC", "IDFCFB" to "IDFC",
        // RBL
        "RBLBNK" to "RBL", "RBLBK" to "RBL",
        // Federal Bank — confirmed: JM-FEDBNK, JM-FEDBNK-T
        "FEDBNK" to "FEDERAL", "FEDBKN" to "FEDERAL",
        // HSBC — confirmed: AD-HSBCIN-S
        "HSBCIN" to "HSBC", "HSBCBK" to "HSBC",
        // American Express — confirmed: JX-AMEXIN-S, TX-AMEXIN-S
        "AMEXIN" to "AMEX", "AMEXBK" to "AMEX",
        // DCB Bank — confirmed: CP-DCBANK
        "DCBANK" to "DCB",
        // DBS Bank India
        "DBSBNK" to "DBS", "DBSINB" to "DBS",
        // PayTM
        "PAYTMB" to "PAYTM", "PAYTMS" to "PAYTM",
        // PhonePe
        "PHNEPE" to "PHONEPE", "PHONPE" to "PHONEPE",
        // CRED
        "CREDAP" to "CRED",
    )

    /** Human-readable canonical names for each catalog key — shown in the UI. */
    private val bankDisplayNames = mapOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "STATE BANK" to "SBI",
        "PNB" to "Punjab National Bank",
        "PUNJAB NATIONAL" to "Punjab National Bank",
        "BANK OF BARODA" to "Bank of Baroda",
        "BOB" to "Bank of Baroda",
        "CANARA" to "Canara Bank",
        "UNION BANK" to "Union Bank",
        "BANK OF INDIA" to "Bank of India",
        "BOI" to "Bank of India",
        "INDIAN BANK" to "Indian Bank",
        "CENTRAL BANK" to "Central Bank",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "YES BANK" to "Yes Bank",
        "INDUSIND" to "IndusInd Bank",
        "FEDERAL" to "Federal Bank",
        "RBL" to "RBL Bank",
        "IDFC" to "IDFC First Bank",
        "DCB" to "DCB Bank",
        "BANDHAN" to "Bandhan Bank",
        "AU SMALL" to "AU Small Finance Bank",
        "HSBC" to "HSBC India",
        "AMEX" to "American Express",
        "AMERICAN EXPRESS" to "American Express",
        "CITI" to "Citibank",
        "CITIBANK" to "Citibank",
        "STANDARD CHARTERED" to "Standard Chartered",
        "DBS" to "DBS Bank",
        "DEUTSCHE" to "Deutsche Bank",
        "PAYTM" to "Paytm",
        "PHONEPE" to "PhonePe",
        "GOOGLEPAY" to "Google Pay",
        "GOOGLE PAY" to "Google Pay",
        "GPAY" to "Google Pay",
        "AMAZON PAY" to "Amazon Pay",
        "RAZORPAY" to "Razorpay",
        "CRED" to "CRED",
        "SLICE" to "Slice",
        "JUPITER" to "Jupiter",
        "NAVI" to "Navi",
        "ONECARD" to "OneCard",
        "ONE CARD" to "OneCard",
    )

    private fun senderCodeToBankKey(sender: String): String? {
        // Format: <2-char-prefix>-<bank-code>[-<optional-suffix>]  e.g. AX-AXISBK-S
        val parts = sender.uppercase().split("-")
        val code = parts.getOrNull(1) ?: return null
        return senderCodes[code] ?: senderCodes.entries
            .firstOrNull { (k, _) -> code.contains(k) }?.value
    }

    /**
     * Returns a human-readable bank name for the given SMS sender address,
     * e.g. "AX-AXISBK-S" → "Axis Bank". Returns null if sender not recognized.
     */
    fun detectedBankName(topSender: String?): String? {
        val bankKey = senderCodeToBankKey(topSender ?: return null) ?: return null
        return bankDisplayNames[bankKey]
            ?: bankDisplayNames.entries.firstOrNull { (k, _) -> bankKey.contains(k) }?.value
    }

    fun forSender(sender: String): BankBrand? {
        val bankKey = senderCodeToBankKey(sender) ?: return null
        return catalog.firstOrNull { (key, _) -> bankKey.contains(key) }?.second
    }

    /**
     * Resolve brand. Tries [topSender] first, then substring match on [accountKey].
     */
    fun forAccount(accountKey: String, topSender: String? = null): BankBrand {
        topSender?.let { forSender(it) }?.let { return it }
        val upper = accountKey.uppercase()
        return catalog.firstOrNull { (key, _) -> upper.contains(key) }?.second ?: fallback
    }
}

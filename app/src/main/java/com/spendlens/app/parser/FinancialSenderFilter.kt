package com.spendlens.app.parser

import com.spendlens.app.parser.model.SmsMessage

/**
 * Sender-side pre-filter used when the user enables "Financial senders only" in Settings.
 *
 * Indian bank/fintech SMS senders are registered under TRAI DLT and follow the format
 * `XX-BRANDCD` (e.g. `VK-HDFCBK`). This filter matches the brand-code portion against
 * a curated list of banks, payment wallets, and lending NBFCs that send actual
 * debit/credit notifications.
 *
 * This filter runs *before* [FinancialSmsFilter] — if the sender is not recognised as a
 * financial institution the SMS is immediately IGNORED without any regex parsing.
 * docs/DESIGN.md §3.1.
 */
object FinancialSenderFilter {

    /**
     * Matches known bank and financial-service DLT header codes embedded anywhere in
     * the sender address (handles both bare codes like `HDFCBK` and prefixed codes
     * like `VK-HDFCBK`).
     *
     * Rules for inclusion:
     *  - Must be a regulated bank, payment bank, wallet, or lending NBFC in India (or an
     *    international bank operating in India) that issues debit/credit notifications.
     *  - Generic words ("BANK", "CREDIT") are intentionally excluded to avoid false positives
     *    from comparison-site senders like BankBazaar.
     */
    private val financialSenderPattern = Regex(
        "(?i)(?:" +
            // ---- Scheduled commercial banks ----
            "HDFCBK|HDFCBN|" +                         // HDFC Bank
            "ICICIB|ICICIC|" +                          // ICICI Bank
            "SBIINB|SBINB|SBISMS|" +                   // State Bank of India
            "AXISBK|AXISBN|" +                          // Axis Bank
            "KOTAKB|KOTAKM|" +                          // Kotak Mahindra Bank
            "PNBSMS|PNBALR|" +                          // Punjab National Bank
            "CANBNK|CANARA|" +                          // Canara Bank
            "BOBCRD|BOBSMS|BANKOB|" +                   // Bank of Baroda
            "UNIONB|UBISMS|" +                          // Union Bank of India
            "INDBNK|INDIBN|" +                          // Indian Bank
            "IDFCFB|IDFCBN|" +                          // IDFC First Bank
            "INDUSL|INDUSB|" +                          // IndusInd Bank
            "YESBK|YESBNK|" +                           // Yes Bank
            "FDRLBK|FDRBNK|" +                          // Federal Bank
            "IDBIBK|IDBIBN|" +                          // IDBI Bank
            "UCOBK|UCOBNK|" +                           // UCO Bank
            "CENTBK|CENTBN|" +                          // Central Bank of India
            "RBLBNK|RBLSMS|" +                          // RBL Bank
            "DCBBNK|DCBANK|" +                          // DCB Bank
            "SCBANK|SCBBNK|" +                          // Standard Chartered
            "CITIBK|CITIBN|" +                          // Citibank
            "TMBLBK|TMBBNK|" +                          // Tamilnad Mercantile Bank
            "KVBBNK|KVBSMS|" +                          // Karur Vysya Bank
            "SVCBNK|SVCBAN|" +                          // SVC Co-operative Bank
            "BANDHN|BDNBNK|" +                          // Bandhan Bank
            "JKBANK|JKBBNK|" +                          // J&K Bank
            "CSBBNK|CSBSMS|" +                          // CSB Bank
            "AUBANK|AUBBNK|" +                          // AU Small Finance Bank
            "EQUBNK|EQUTAS|" +                          // Equitas Small Finance Bank
            "UJJBNK|UJJSMS|" +                          // Ujjivan Small Finance Bank
            // ---- Payment banks ----
            "PAYTMB|PAYTMK|" +                          // Paytm Payments Bank
            "AIRBNK|AIRBPB|" +                          // Airtel Payments Bank
            "JIOBNK|JIOPAY|" +                          // Jio Payments Bank
            "INDPAY|INDPSB|" +                          // India Post Payments Bank
            // ---- Wallets & UPI apps ----
            "PHONEP|PHPE|" +                            // PhonePe
            "GPAY|GOOGPAY|" +                           // Google Pay
            "AMAZPAY|AMZNPY|" +                         // Amazon Pay
            "FREECHARGE|FRECHG|" +                      // FreeCharge
            "MOBIKWK|MOBIKWIK|" +                       // MobiKwik
            "BHIMUPI|BHIMPAY|" +                        // BHIM UPI
            "CRED|CREDCLUB|" +                          // CRED
            // ---- Lending NBFCs / fintechs ----
            "BAJAJF|BAJFINL|" +                         // Bajaj Finance / Bajaj Finserv
            "TVSCRD|TVSCREDIT|" +                       // TVS Credit
            "HDBFIN|HDBFSL|" +                          // HDB Financial Services
            "FULLRT|FULLERTON|" +                       // Fullerton India
            "TATACAP|TATACP|" +                         // Tata Capital
            "LICHOU|LICHI|" +                           // LIC Housing Finance
            "SHRIRAM|SHRFIN|" +                         // Shriram Finance
            "MAHFINL|MAFIN|" +                          // Mahindra Finance
            "AMEXIN|AMEXBN" +                           // American Express India
            ")",
    )

    /** Returns true when [sender] is recognised as a bank or financial-service DLT sender. */
    fun isFinancialSender(sender: String): Boolean = financialSenderPattern.containsMatchIn(sender)

    /** Convenience overload that checks [SmsMessage.sender]. */
    fun isFinancialSender(sms: SmsMessage): Boolean = isFinancialSender(sms.sender)
}

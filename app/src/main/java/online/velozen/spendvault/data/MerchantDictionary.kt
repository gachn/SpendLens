package com.spendlens.app.data

/**
 * Bundled, offline map of common merchant keys → canonical brand names. Checked before
 * any network lookup so popular merchants resolve instantly and privately.
 * Keys are [MerchantNormalizer.key] forms (lowercase, noise-stripped).
 */
object MerchantDictionary {

    private val brands = mapOf(
        "swiggy" to "Swiggy", "zomato" to "Zomato", "amazon" to "Amazon", "flipkart" to "Flipkart",
        "myntra" to "Myntra", "ajio" to "AJIO", "uber" to "Uber", "ola" to "Ola", "rapido" to "Rapido",
        "irctc" to "IRCTC", "netflix" to "Netflix", "spotify" to "Spotify", "hotstar" to "Disney+ Hotstar",
        "bookmyshow" to "BookMyShow", "phonepe" to "PhonePe", "paytm" to "Paytm", "googlepay" to "Google Pay",
        "google" to "Google", "bigbasket" to "BigBasket", "blinkit" to "Blinkit", "zepto" to "Zepto",
        "dmart" to "DMart", "jio" to "Jio", "airtel" to "Airtel", "starbucks" to "Starbucks",
        "dominos" to "Domino's", "kfc" to "KFC", "mcdonalds" to "McDonald's", "apollo" to "Apollo",
        "makemytrip" to "MakeMyTrip", "indigo" to "IndiGo", "oyo" to "OYO", "cred" to "CRED",
        "razorpay" to "Razorpay", "lic" to "LIC",
        // Sender-token aliases (TRAI 6-char codes) and common abbreviations.
        "phonpe" to "PhonePe",       // AD-PHONPE-S sender token
        "paytmm" to "Paytm",         // AD-PAYTMM-S sender token
        "olacab" to "Ola",           // AD-OLACAB sender token
        "ekartl" to "Flipkart",      // AD-EKARTL sender token (Flipkart logistics)
        "flpkrt" to "Flipkart",      // AD-FLPKRT sender token
        "bundl" to "Swiggy",         // Bundl Technologies = Swiggy parent company
        "instamart" to "Swiggy Instamart",
        "linkedin" to "LinkedIn",
        "one97" to "Paytm",          // One97 Communications = Paytm
        "caratlane" to "CaratLane",
        "eazydiner" to "EazyDiner",
        "jubilant" to "Jubilant FoodWorks",
        "pvrcin" to "PVR",
        "nykaa" to "Nykaa",
        "zerodha" to "Zerodha",
        "urbanclap" to "Urban Company",
        "urbancompany" to "Urban Company",
    )

    // Longest keys first so "swiggy" beats a shorter accidental substring.
    private val byLengthDesc = brands.entries.sortedByDescending { it.key.length }

    fun lookup(key: String): String? {
        if (key.isBlank()) return null
        brands[key]?.let { return it }
        // Single-token VPAs like "swiggystores" → match the contained brand.
        return byLengthDesc.firstOrNull { key.contains(it.key) }?.value
    }
}


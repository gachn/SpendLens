package com.spendlens.app.data

import com.spendlens.app.data.db.CategoryEntity

/**
 * Seed categories + keyword rules. Ids are assigned explicitly so rules can reference
 * them deterministically on first run.
 */
object DefaultCategories {

    val categories = listOf(
        CategoryEntity(1, "Food & Dining", "🍽️", 0xFFEF6C50),
        CategoryEntity(2, "Groceries", "🛒", 0xFF66BB6A),
        CategoryEntity(3, "Transport", "🚕", 0xFF42A5F5),
        CategoryEntity(4, "Shopping", "🛍️", 0xFFAB47BC),
        CategoryEntity(5, "Bills & Utilities", "💡", 0xFFFFB300),
        CategoryEntity(6, "Entertainment", "🎬", 0xFFEC407A),
        CategoryEntity(7, "Health", "🩺", 0xFF26C6DA),
        CategoryEntity(8, "Travel", "✈️", 0xFF7E57C2),
        CategoryEntity(9, "Income", "💰", 0xFF2E9E7B),
        CategoryEntity(10, "Transfers", "🔁", 0xFF8D9CA8),
        CategoryEntity(11, "Other", "📦", 0xFF90A4AE),
    )

    /** matcher keyword (lowercase, substring) → categoryId. */
    val rules: List<Pair<String, Long>> = listOf(
        "swiggy" to 1, "zomato" to 1, "restaurant" to 1, "cafe" to 1, "pizza" to 1, "dominos" to 1,
        "instamart" to 1, "bundl" to 1, // Swiggy Instamart / Bundl Technologies (Swiggy parent)
        "jubilant" to 1, "barbeque" to 1, "eazydiner" to 1,
        "bigbasket" to 2, "blinkit" to 2, "grofers" to 2, "dmart" to 2, "supermarket" to 2,
        "zepto" to 2,
        "uber" to 3, "ola" to 3, "rapido" to 3, "metro" to 3, "fuel" to 3, "petrol" to 3,
        "amazon" to 4, "flipkart" to 4, "myntra" to 4, "ajio" to 4, "mall" to 4,
        "nykaa" to 4, "caratlane" to 4,
        "electricity" to 5, "recharge" to 5, "airtel" to 5, "jio" to 5, "broadband" to 5, "gas" to 5,
        "linkedin" to 5, // LinkedIn Premium subscription
        "netflix" to 6, "spotify" to 6, "hotstar" to 6, "bookmyshow" to 6, "prime" to 6,
        "pvr" to 6, "pvrcin" to 6,
        "pharmacy" to 7, "apollo" to 7, "hospital" to 7, "clinic" to 7, "medical" to 7,
        // Travel: flights, trains, buses and the big OTAs / ticketing apps.
        "makemytrip" to 8, "goibibo" to 8, "cleartrip" to 8, "easemytrip" to 8, "yatra" to 8, "ixigo" to 8,
        "indigo" to 8, "vistara" to 8, "spicejet" to 8, "akasa" to 8, "air india" to 8, "airlines" to 8,
        "redbus" to 8, "abhibus" to 8, "irctc" to 8, "railway" to 8, "confirmtkt" to 8,
        "hotel" to 8, "oyo" to 8,
        "salary" to 9, "interest" to 9, "credited by" to 9,
        "neft" to 10, "imps" to 10, "transfer" to 10,
    )
}

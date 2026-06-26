package com.spendlens.app.ai

import com.spendlens.app.data.db.RawSmsEntity

object PromptGenerator {

    fun generate(smsList: List<RawSmsEntity>): String {
        return buildString {
            append("You are a regex pattern generator for SpendLens, an Android transaction SMS parser.\n")
            if (smsList.size == 1) {
                val sms = smsList.first()
                append("I have an unparsed SMS:\n")
                append("Sender: ${sms.sender}\n")
                append("Body: \"${sms.body}\"\n\n")
            } else {
                append("I have a list of unparsed SMS messages. For each SMS, generate a regular expression:\n\n")
                smsList.forEachIndexed { idx, sms ->
                    append("${idx + 1}. Sender: ${sms.sender}, Body: \"${sms.body}\"\n")
                }
                append("\n")
            }
            append("Generate a regular expression matching this SMS format. It MUST extract these named capture groups:\n")
            append("- (?<amount>...) : The decimal amount (e.g. 150.00 or 450)\n")
            append("- (?<curr>...) : The currency (INR, USD, etc.)\n")
            append("- (?<dir>...) : The transaction direction (debited, credited, spent, etc.)\n")
            append("- (?<account>...) : The masked card or bank account tail (optional, e.g. XX1234)\n")
            append("- (?<party>...) : The counterparty/merchant name (e.g. SWIGGY*DELHI)\n")
            append("- (?<balance>...) : The post-transaction balance (optional)\n")
            append("- (?<ref>...) : The transaction reference or UTR number (optional)\n\n")

            if (smsList.size == 1) {
                append("Respond ONLY with a valid JSON block matching this schema:\n")
                append("{\n")
                append("  \"name\": \"Clean name for this pattern (e.g., ICICI Bank UPI)\",\n")
                append("  \"senderRegex\": \"Regex for sender or null\",\n")
                append("  \"bodyRegex\": \"The regex with named capture groups\"\n")
                append("}\n")
            } else {
                append("Respond ONLY with a valid JSON array of objects matching this schema (with exactly ${smsList.size} objects in the array):\n")
                append("[\n")
                append("  {\n")
                append("    \"name\": \"Clean name for this pattern (e.g., ICICI Bank UPI)\",\n")
                append("    \"senderRegex\": \"Regex for sender or null\",\n")
                append("    \"bodyRegex\": \"The regex with named capture groups\"\n")
                append("  }\n")
                append("]\n\n")
                append("CRITICAL: Generate exactly ${smsList.size} JSON objects in the array, one for each SMS provided above in that exact order. Do NOT truncate, do NOT use \"...\" placeholders, and do NOT omit any entries.\n")
            }
            append("Do not write any markdown code blocks, explanation, or conversational text. Just the raw JSON.")
        }
    }
}

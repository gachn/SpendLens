package online.velozen.spendvault.data.demo

import online.velozen.spendvault.data.db.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Demo data generator for Play Store screenshots.
 * Contains realistic-looking fake data that doesn't expose any real financial information.
 */
object DemoDataGenerator {

    private val now = Instant.now()
    private val zoneId = ZoneId.systemDefault()

    // Demo transactions (all fake, realistic-looking data)
    fun getDemoTransactions(): List<TransactionEntity> = listOf(
        // Recent spending (last 7 days)
        TransactionEntity(
            id = 1,
            rawSmsId = 1,
            amountMinor = 2450000L, // ₹2,450.00
            currency = "INR",
            amountBaseMinor = 2450000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "BigBasket",
            balanceMinor = 12450000L,
            referenceId = null,
            occurredAt = now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 1,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 2,
            rawSmsId = 2,
            amountMinor = 850000L, // ₹850.00
            currency = "INR",
            amountBaseMinor = 850000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Amazon",
            balanceMinor = 12205000L,
            referenceId = null,
            occurredAt = now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
            channel = "Card",
            categoryId = 2,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 3,
            rawSmsId = 3,
            amountMinor = 320000L, // ₹320.00
            currency = "INR",
            amountBaseMinor = 320000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Swiggy",
            balanceMinor = 11355000L,
            referenceId = null,
            occurredAt = now.minus(2, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 1,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 4,
            rawSmsId = 4,
            amountMinor = 2500000L, // ₹2,500.00
            currency = "INR",
            amountBaseMinor = 2500000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Electricity Board",
            balanceMinor = 8855000L,
            referenceId = null,
            occurredAt = now.minus(2, ChronoUnit.DAYS).toEpochMilli(),
            channel = "Card",
            categoryId = 3,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 5,
            rawSmsId = 5,
            amountMinor = 6240000L, // ₹6,240.00
            currency = "INR",
            amountBaseMinor = 6240000L,
            direction = "CREDIT",
            accountKey = "hdfc_savings",
            counterparty = "Salary Credit",
            balanceMinor = 15095000L,
            referenceId = null,
            occurredAt = now.minus(3, ChronoUnit.DAYS).toEpochMilli(),
            channel = "Bank",
            categoryId = 8,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 6,
            rawSmsId = 6,
            amountMinor = 1200000L, // ₹1,200.00
            currency = "INR",
            amountBaseMinor = 1200000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Zomato",
            balanceMinor = 8855000L,
            referenceId = null,
            occurredAt = now.minus(3, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 1,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 7,
            rawSmsId = 7,
            amountMinor = 450000L, // ₹450.00
            currency = "INR",
            amountBaseMinor = 450000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Netflix",
            balanceMinor = 8405000L,
            referenceId = null,
            occurredAt = now.minus(4, ChronoUnit.DAYS).toEpochMilli(),
            channel = "Card",
            categoryId = 5,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 8,
            rawSmsId = 8,
            amountMinor = 1800000L, // ₹1,800.00
            currency = "INR",
            amountBaseMinor = 1800000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Flipkart",
            balanceMinor = 6605000L,
            referenceId = null,
            occurredAt = now.minus(4, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 2,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 9,
            rawSmsId = 9,
            amountMinor = 950000L, // ₹950.00
            currency = "INR",
            amountBaseMinor = 950000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Uber",
            balanceMinor = 5655000L,
            referenceId = null,
            occurredAt = now.minus(5, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 4,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 10,
            rawSmsId = 10,
            amountMinor = 650000L, // ₹650.00
            currency = "INR",
            amountBaseMinor = 650000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Spotify",
            balanceMinor = 5005000L,
            referenceId = null,
            occurredAt = now.minus(5, ChronoUnit.DAYS).toEpochMilli(),
            channel = "Card",
            categoryId = 5,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 11,
            rawSmsId = 11,
            amountMinor = 2100000L, // ₹2,100.00
            currency = "INR",
            amountBaseMinor = 2100000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Myntra",
            balanceMinor = 2905000L,
            referenceId = null,
            occurredAt = now.minus(6, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 2,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 12,
            rawSmsId = 12,
            amountMinor = 580000L, // ₹580.00
            currency = "INR",
            amountBaseMinor = 580000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Starbucks",
            balanceMinor = 2325000L,
            referenceId = null,
            occurredAt = now.minus(6, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 1,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 13,
            rawSmsId = 13,
            amountMinor = 1500000L, // ₹1,500.00
            currency = "INR",
            amountBaseMinor = 1500000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "PhonePe Recharge",
            balanceMinor = 825000L,
            referenceId = null,
            occurredAt = now.minus(7, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 6,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 14,
            rawSmsId = 14,
            amountMinor = 890000L, // ₹890.00
            currency = "INR",
            amountBaseMinor = 890000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "BookMyShow",
            balanceMinor = -65000L,
            referenceId = null,
            occurredAt = now.minus(7, ChronoUnit.DAYS).toEpochMilli(),
            channel = "UPI",
            categoryId = 7,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        ),
        TransactionEntity(
            id = 15,
            rawSmsId = 15,
            amountMinor = 1200000L, // ₹1,200.00
            currency = "INR",
            amountBaseMinor = 1200000L,
            direction = "DEBIT",
            accountKey = "hdfc_savings",
            counterparty = "Electricity Bill",
            balanceMinor = -1265000L,
            referenceId = null,
            occurredAt = now.minus(8, ChronoUnit.DAYS).toEpochMilli(),
            channel = "Auto-pay",
            categoryId = 3,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = false,
            note = null,
            tags = null,
            receiptUri = null
        )
    )

    // Demo categories
    fun getDemoCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(id = 1, name = "Food & Dining", icon = "🍽️", color = 0xFFFF5722),
        CategoryEntity(id = 2, name = "Shopping", icon = "🛒", color = 0xFF3F51B5),
        CategoryEntity(id = 3, name = "Bills", icon = "📄", color = 0xFFF44336),
        CategoryEntity(id = 4, name = "Transport", icon = "🚗", color = 0xFF009688),
        CategoryEntity(id = 5, name = "Subscriptions", icon = "📱", color = 0xFF9C27B0),
        CategoryEntity(id = 6, name = "Utilities", icon = "⚡", color = 0xFFFF9800),
        CategoryEntity(id = 7, name = "Entertainment", icon = "🎬", color = 0xFFE91E63),
        CategoryEntity(id = 8, name = "Income", icon = "💰", color = 0xFF4CAF50),
        CategoryEntity(id = 9, name = "Health", icon = "🏥", color = 0xFF00BCD4),
        CategoryEntity(id = 10, name = "Education", icon = "📚", color = 0xFF607D8B)
    )

    // Demo budgets
    fun getDemoBudgets(): List<BudgetEntity> = listOf(
        BudgetEntity(
            categoryId = 1,
            monthlyLimitMinor = 1500000L,
            rolloverEnabled = false
        ),
        BudgetEntity(
            categoryId = 2,
            monthlyLimitMinor = 2000000L,
            rolloverEnabled = false
        ),
        BudgetEntity(
            categoryId = 3,
            monthlyLimitMinor = 1000000L,
            rolloverEnabled = false
        )
    )

    // Demo bills
    fun getDemoBills(): List<BillEntity> = listOf(
        BillEntity(
            id = 1,
            counterparty = "Netflix",
            typicalAmountMinor = 450000L,
            dayOfMonth = 15,
            categoryId = 5,
            lastPaidAt = now.minus(20, ChronoUnit.DAYS).toEpochMilli(),
            reminderEnabled = true
        ),
        BillEntity(
            id = 2,
            counterparty = "Spotify",
            typicalAmountMinor = 650000L,
            dayOfMonth = 20,
            categoryId = 5,
            lastPaidAt = now.minus(15, ChronoUnit.DAYS).toEpochMilli(),
            reminderEnabled = true
        ),
        BillEntity(
            id = 3,
            counterparty = "Electricity Board",
            typicalAmountMinor = 2500000L,
            dayOfMonth = 25,
            categoryId = 3,
            lastPaidAt = now.minus(5, ChronoUnit.DAYS).toEpochMilli(),
            reminderEnabled = true
        )
    )

    // Demo SMS for realistic parsing
    fun getDemoSms(): List<RawSmsEntity> = listOf(
        RawSmsEntity(
            id = 1,
            sender = "HDFCBK",
            body = "Your A/c XX1234 debited with ₹2,450.00 on 01-AUG-26 at BigBasket. Avl Bal ₹1,24,500.00.",
            receivedAt = now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
            contentHash = "hash1",
            status = RawStatus.PARSED,
            promoChecked = true
        ),
        RawSmsEntity(
            id = 2,
            sender = "HDFCBK",
            body = "Your Card XX5678 debited ₹850.00 at AMAZON PAY on 01-AUG-26. Avl Bal ₹1,22,050.00.",
            receivedAt = now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
            contentHash = "hash2",
            status = RawStatus.PARSED,
            promoChecked = true
        ),
        RawSmsEntity(
            id = 3,
            sender = "ICICIC",
            body = "₹320.00 debited from A/c XX9876 for SWIGGY via UPI. Ref no UPI123456789.",
            receivedAt = now.minus(2, ChronoUnit.DAYS).toEpochMilli(),
            contentHash = "hash3",
            status = RawStatus.PARSED,
            promoChecked = true
        )
    )

    // Helper to format money
    fun formatMoney(amountMinor: Long): String {
        val rupees = amountMinor / 100.0
        return "₹${String.format("%,.2f", rupees)}"
    }

    // Get all demo data as a complete package
    fun getAllDemoData(): DemoDataSet {
        return DemoDataSet(
            transactions = getDemoTransactions(),
            categories = getDemoCategories(),
            budgets = getDemoBudgets(),
            bills = getDemoBills(),
            sms = getDemoSms()
        )
    }
}

data class DemoDataSet(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val bills: List<BillEntity>,
    val sms: List<RawSmsEntity>
)
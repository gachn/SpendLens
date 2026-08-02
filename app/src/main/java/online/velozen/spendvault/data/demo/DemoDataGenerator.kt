package online.velozen.spendvault.data.demo

import online.velozen.spendvault.data.db.*
import online.velozen.spendvault.parser.model.Money
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
            date = now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "BigBasket",
            category = "Food & Dining",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 2,
            rawSmsId = 2,
            amountMinor = 850000L, // ₹850.00
            currency = "INR",
            date = now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Amazon",
            category = "Shopping",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "Card",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 3,
            rawSmsId = 3,
            amountMinor = 320000L, // ₹320.00
            currency = "INR",
            date = now.minus(2, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Swiggy",
            category = "Food & Dining",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 4,
            rawSmsId = 4,
            amountMinor = 2500000L, // ₹2,500.00
            currency = "INR",
            date = now.minus(2, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Electricity Board",
            category = "Bills",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "Card",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 5,
            rawSmsId = 5,
            amountMinor = 6240000L, // ₹6,240.00
            currency = "INR",
            date = now.minus(3, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Salary Credit",
            category = "Income",
            accountKey = "hdfc_savings",
            direction = "in",
            channel = "Bank",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 6,
            rawSmsId = 6,
            amountMinor = 1200000L, // ₹1,200.00
            currency = "INR",
            date = now.minus(3, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Zomato",
            category = "Food & Dining",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 7,
            rawSmsId = 7,
            amountMinor = 450000L, // ₹450.00
            currency = "INR",
            date = now.minus(4, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Netflix",
            category = "Subscriptions",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "Card",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 8,
            rawSmsId = 8,
            amountMinor = 1800000L, // ₹1,800.00
            currency = "INR",
            date = now.minus(4, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Flipkart",
            category = "Shopping",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 9,
            rawSmsId = 9,
            amountMinor = 950000L, // ₹950.00
            currency = "INR",
            date = now.minus(5, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Uber",
            category = "Transport",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 10,
            rawSmsId = 10,
            amountMinor = 650000L, // ₹650.00
            currency = "INR",
            date = now.minus(5, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Spotify",
            category = "Subscriptions",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "Card",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 11,
            rawSmsId = 11,
            amountMinor = 2100000L, // ₹2,100.00
            currency = "INR",
            date = now.minus(6, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Myntra",
            category = "Shopping",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 12,
            rawSmsId = 12,
            amountMinor = 580000L, // ₹580.00
            currency = "INR",
            date = now.minus(6, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Starbucks",
            category = "Food & Dining",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 13,
            rawSmsId = 13,
            amountMinor = 1500000L, // ₹1,500.00
            currency = "INR",
            date = now.minus(7, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "PhonePe Recharge",
            category = "Utilities",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 14,
            rawSmsId = 14,
            amountMinor = 890000L, // ₹890.00
            currency = "INR",
            date = now.minus(7, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "BookMyShow",
            category = "Entertainment",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "UPI",
            notes = null,
            reviewed = true,
            duplicateOf = null
        ),
        TransactionEntity(
            id = 15,
            rawSmsId = 15,
            amountMinor = 1200000L, // ₹1,200.00
            currency = "INR",
            date = now.minus(8, ChronoUnit.DAYS).toEpochMilli(),
            merchant = "Electricity Bill",
            category = "Bills",
            accountKey = "hdfc_savings",
            direction = "out",
            channel = "Auto-pay",
            notes = null,
            reviewed = true,
            duplicateOf = null
        )
    )

    // Demo categories
    fun getDemoCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(id = 1, name = "Food & Dining", icon = "🍽️", budgetMinor = 1500000L),
        CategoryEntity(id = 2, name = "Shopping", icon = "🛒", budgetMinor = 2000000L),
        CategoryEntity(id = 3, name = "Bills", icon = "📄", budgetMinor = 1000000L),
        CategoryEntity(id = 4, name = "Transport", icon = "🚗", budgetMinor = 800000L),
        CategoryEntity(id = 5, name = "Subscriptions", icon = "📱", budgetMinor = 600000L),
        CategoryEntity(id = 6, name = "Utilities", icon = "⚡", budgetMinor = 700000L),
        CategoryEntity(id = 7, name = "Entertainment", icon = "🎬", budgetMinor = 500000L),
        CategoryEntity(id = 8, name = "Income", icon = "💰", budgetMinor = 0L),
        CategoryEntity(id = 9, name = "Health", icon = "🏥", budgetMinor = 400000L),
        CategoryEntity(id = 10, name = "Education", icon = "📚", budgetMinor = 600000L)
    )

    // Demo budgets
    fun getDemoBudgets(): List<BudgetEntity> = listOf(
        BudgetEntity(
            id = 1,
            categoryId = 1,
            month = now.atZone(zoneId).toLocalDate().withDayOfMonth(1).toString(),
            amountMinor = 1500000L,
            alertThreshold = 0.8f
        ),
        BudgetEntity(
            id = 2,
            categoryId = 2,
            month = now.atZone(zoneId).toLocalDate().withDayOfMonth(1).toString(),
            amountMinor = 2000000L,
            alertThreshold = 0.8f
        ),
        BudgetEntity(
            id = 3,
            categoryId = 3,
            month = now.atZone(zoneId).toLocalDate().withDayOfMonth(1).toString(),
            amountMinor = 1000000L,
            alertThreshold = 0.8f
        )
    )

    // Demo accounts
    fun getDemoAccounts(): List<AccountEntity> = listOf(
        AccountEntity(
            id = 1,
            accountKey = "hdfc_savings",
            name = "HDFC Savings",
            type = "savings",
            balanceMinor = 12450000L, // ₹1,24,500.00
            currency = "INR",
            bank = "HDFC Bank"
        ),
        AccountEntity(
            id = 2,
            accountKey = "icici_credit",
            name = "ICICI Credit Card",
            type = "credit",
            balanceMinor = -456000L, // -₹4,560.00
            currency = "INR",
            bank = "ICICI Bank"
        )
    )

    // Demo bills
    fun getDemoBills(): List<BillEntity> = listOf(
        BillEntity(
            id = 1,
            counterparty = "Netflix",
            typicalAmountMinor = 450000L,
            currency = "INR",
            dueDayOfMonth = 15,
            lastPaidAt = now.minus(20, ChronoUnit.DAYS).toEpochMilli()
        ),
        BillEntity(
            id = 2,
            counterparty = "Spotify",
            typicalAmountMinor = 650000L,
            currency = "INR",
            dueDayOfMonth = 20,
            lastPaidAt = now.minus(15, ChronoUnit.DAYS).toEpochMilli()
        ),
        BillEntity(
            id = 3,
            counterparty = "Electricity Board",
            typicalAmountMinor = 2500000L,
            currency = "INR",
            dueDayOfMonth = 25,
            lastPaidAt = now.minus(5, ChronoUnit.DAYS).toEpochMilli()
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
            accounts = getDemoAccounts(),
            bills = getDemoBills(),
            sms = getDemoSms()
        )
    }
}

data class DemoDataSet(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val accounts: List<AccountEntity>,
    val bills: List<BillEntity>,
    val sms: List<RawSmsEntity>
)
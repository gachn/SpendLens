package com.spendlens.app.di

import android.content.Context
import com.spendlens.app.ai.HeuristicPatternGenerator
import com.spendlens.app.ai.MerchantResolver
import com.spendlens.app.ai.PatternGenerator
import com.spendlens.app.ai.WebMerchantResolver
import com.spendlens.app.data.ReceiptStore
import com.spendlens.app.data.crypto.DatabaseKeyManager
import com.spendlens.app.data.db.AppDatabase
import com.spendlens.app.data.fx.FxProvider
import com.spendlens.app.data.fx.FxRepository
import com.spendlens.app.data.fx.WebFxProvider
import com.spendlens.app.data.prefs.SettingsStore
import com.spendlens.app.data.prefs.VelocityAlertStore
import com.spendlens.app.data.repository.BillRepository
import com.spendlens.app.data.repository.BudgetRepository
import com.spendlens.app.data.repository.CategoryRepository
import com.spendlens.app.data.repository.MerchantRepository
import com.spendlens.app.data.repository.PatternRepository
import com.spendlens.app.data.repository.SavingsGoalRepository
import com.spendlens.app.data.repository.TransactionRepository
import com.spendlens.app.sms.SmsImporter
import com.spendlens.app.sms.SmsProcessor

/**
 * Manual dependency container held by the Application. Keeps the object graph
 * explicit and review-friendly; swap for Hilt later if desired. docs/DESIGN.md §1.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val keyManager = DatabaseKeyManager(appContext)
    val database: AppDatabase by lazy { AppDatabase.create(appContext, keyManager) }

    /** Non-sensitive UI preferences (theme mode, dynamic colour). */
    val settingsStore by lazy { SettingsStore(appContext) }

    /** Per-category throttle for spending-velocity alerts (issue #3). */
    val velocityAlertStore by lazy { VelocityAlertStore(appContext) }

    val patternRepository by lazy { PatternRepository(database.patternDao()) }
    val transactionRepository by lazy { TransactionRepository(database.transactionDao(), database.transactionSplitDao()) }
    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val budgetRepository by lazy { BudgetRepository(database.budgetDao()) }
    val billRepository by lazy { BillRepository(database.billDao()) }
    val savingsGoalRepository by lazy { SavingsGoalRepository(database.savingsGoalDao(), transactionRepository) }
    val rawSmsDao get() = database.rawSmsDao()
    val cardBillDao get() = database.cardBillDao()

    /** Encrypted on-device store for receipt images attached to transactions. */
    val receiptStore by lazy { ReceiptStore(appContext) }

    /** On-device, no-network heuristic pattern learner for unrecognised SMS formats. */
    val patternGenerator: PatternGenerator by lazy { HeuristicPatternGenerator() }

    /** Merchant-name resolver. Web-backed (Clearbit) per the user's choice; cached as metadata. */
    val merchantResolver: MerchantResolver = WebMerchantResolver()
    val merchantRepository by lazy { MerchantRepository(database.merchantDao(), merchantResolver) }

    /** FX rates for converting foreign-currency spend into the base currency (INR). */
    val fxProvider: FxProvider = WebFxProvider()
    val fxRepository by lazy { FxRepository(appContext, fxProvider) }

    val smsProcessor: SmsProcessor by lazy {
        SmsProcessor(
            rawDao = database.rawSmsDao(),
            txnRepo = transactionRepository,
            patternRepo = patternRepository,
            categoryRepo = categoryRepository,
            merchantRepo = merchantRepository,
            fxRepo = fxRepository,
            cardBillDao = cardBillDao,
            generator = patternGenerator,
        )
    }

    val smsImporter: SmsImporter by lazy { SmsImporter(appContext, smsProcessor) }

    /** Seeds built-in patterns and categories. Safe to call repeatedly. */
    suspend fun seed() {
        patternRepository.seedIfEmpty()
        categoryRepository.seedIfEmpty()
    }

    fun wipeAllData() {
        appContext.deleteDatabase("spendlens.db")
        keyManager.clear()
    }
}

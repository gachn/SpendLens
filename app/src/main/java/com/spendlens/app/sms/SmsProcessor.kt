package com.spendlens.app.sms

import com.spendlens.app.ai.PatternGenerator
import com.spendlens.app.ai.Pii
import com.spendlens.app.data.Hashing
import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.CardBillDao
import com.spendlens.app.data.db.CardBillEntity
import com.spendlens.app.data.db.RawSmsDao
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.data.fx.FxRepository
import com.spendlens.app.parser.CardBillParser
import com.spendlens.app.data.repository.CategoryRepository
import com.spendlens.app.data.repository.MerchantRepository
import com.spendlens.app.data.repository.PatternRepository
import com.spendlens.app.data.repository.TransactionRepository
import com.spendlens.app.parser.AccountExtractor
import com.spendlens.app.parser.DuplicateDetector
import com.spendlens.app.parser.FinancialSmsFilter
import com.spendlens.app.parser.MerchantExtractor
import com.spendlens.app.parser.PatternEngine
import com.spendlens.app.parser.SelfTransferDetector
import com.spendlens.app.parser.model.CompiledPattern
import com.spendlens.app.parser.model.MatchResult
import com.spendlens.app.parser.model.SmsMessage
import com.spendlens.app.parser.model.TxnDirection

/**
 * Orchestrates the full pipeline for one SMS: idempotent insert → financial filter →
 * pattern match → (AI/heuristic pattern generation) → duplicate detection →
 * categorisation → persist. docs/DESIGN.md §3. A single bad SMS never throws out of
 * here — failures are recorded as UNPARSED for the Review queue.
 */
class SmsProcessor(
    private val rawDao: RawSmsDao,
    private val txnRepo: TransactionRepository,
    private val patternRepo: PatternRepository,
    private val categoryRepo: CategoryRepository,
    private val merchantRepo: MerchantRepository,
    private val fxRepo: FxRepository,
    private val cardBillDao: CardBillDao,
    private val generator: PatternGenerator,
    private val engine: PatternEngine = PatternEngine(),
) {

    suspend fun process(msg: SmsMessage): TransactionEntity? {
        val hash = Hashing.contentHash(msg.sender, msg.body)
        val rawId = rawDao.insertIgnore(
            RawSmsEntity(
                sender = msg.sender,
                body = msg.body,
                receivedAt = msg.receivedAt,
                contentHash = hash,
                status = RawStatus.PENDING,
            ),
        )
        if (rawId == -1L) return null // identical SMS already ingested

        // Credit-card statement? Capture/refresh the latest bill as a side-effect — this does
        // not consume the SMS, so a payment-confirmation that also quotes a balance still parses
        // as a transaction below.
        captureCardBill(rawId, msg)

        if (!FinancialSmsFilter.isFinancial(msg)) {
            rawDao.updateStatus(rawId, RawStatus.IGNORED, null)
            return null
        }

        var result = engine.match(msg, patternRepo.compiled())
        var patternId = result?.patternId

        if (result == null) {
            patternId = tryLearnPattern(msg)
            if (patternId != null) {
                result = engine.match(msg, patternRepo.compiled())
            }
        }

        if (result == null) {
            rawDao.updateStatus(rawId, RawStatus.UNPARSED, null)
            return null
        }

        patternId?.takeIf { it > 0 }?.let { patternRepo.incrementMatch(it) }
        val txn = persistTransaction(rawId, msg, result)
        rawDao.updateStatus(rawId, RawStatus.PARSED, patternId)
        return txn.takeIf { !it.isDuplicate }
    }

    /** Parse a credit-card statement, if this SMS is one, keeping only the most recent per card. */
    private suspend fun captureCardBill(rawId: Long, msg: SmsMessage) {
        val bill = CardBillParser.parse(msg.sender, msg.body, msg.receivedAt) ?: return
        val existing = cardBillDao.get(bill.cardKey)
        if (existing != null && bill.statementAt < existing.statementAt) return
        cardBillDao.upsert(
            CardBillEntity(
                cardKey = bill.cardKey,
                totalDueMinor = bill.totalDueMinor,
                minDueMinor = bill.minDueMinor,
                currency = bill.currency,
                dueDate = bill.dueDate,
                statementAt = bill.statementAt,
                rawSmsId = rawId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Feed an unrecognised SMS to the generator; validate and store the pattern. */
    private suspend fun tryLearnPattern(msg: SmsMessage): Long? {
        val input = if (generator.requiresMasking) Pii.mask(msg.body) else msg.body
        val gen = generator.generate(input, msg.sender) ?: return null

        val body = runCatching { Regex(gen.bodyRegex) }.getOrNull() ?: return null
        val sender = gen.senderRegex?.let { runCatching { Regex(it) }.getOrNull() }
        // Validate: the generated pattern must actually extract a transaction from its source.
        val candidate = CompiledPattern(id = 0, priority = LEARNED_PRIORITY, body = body, sender = sender)
        engine.match(msg, listOf(candidate)) ?: return null

        return patternRepo.savePattern(
            SmsPatternEntity(
                name = gen.name,
                senderRegex = gen.senderRegex,
                bodyRegex = gen.bodyRegex,
                priority = LEARNED_PRIORITY,
                source = if (generator.requiresMasking) PatternSource.AI else PatternSource.HEURISTIC,
                sampleSms = msg.body,
            ),
        )
    }

    private suspend fun persistTransaction(rawId: Long, msg: SmsMessage, result: MatchResult): TransactionEntity {
        // Enrich the pattern output: better card/account tail and a resolved merchant name.
        val raw = result.transaction
        val betterAccount = AccountExtractor.extract(msg.body)
        val rawMerchant = MerchantExtractor.extract(msg.body)
        // Use sender as a fallback whenever body extraction found nothing — the sender brand
        // is more reliable than a pattern-captured counterparty (which may be a phone number
        // or aggregator code like "CAS" from "CAS*Swiggy").
        val senderHint = if (rawMerchant == null) MerchantExtractor.extractFromSender(msg.sender) else null
        val merchantName = when {
            rawMerchant != null -> merchantRepo.resolveDisplay(rawMerchant)
            senderHint != null -> merchantRepo.resolveDisplay(senderHint)
            raw.counterparty != "Unknown" -> merchantRepo.resolveDisplay(raw.counterparty)
            else -> raw.counterparty
        }
        val p = raw.copy(
            accountKey = betterAccount ?: raw.accountKey,
            counterparty = merchantName,
        )
        val amountBaseMinor = fxRepo.toBaseMinor(p.amountMinor, p.currency)

        val candidates = txnRepo.findCandidates(
            amount = p.amountMinor,
            account = p.accountKey,
            direction = p.direction.name,
            from = p.occurredAt - DAY,
            to = p.occurredAt + DAY,
        )
        val verdict = DuplicateDetector.classify(p, candidates)
        val isDuplicate = verdict is DuplicateDetector.Verdict.Duplicate

        // Self-transfer: opposite leg on another own account within the window → exclude both.
        val opposite = if (p.direction == TxnDirection.DEBIT) "CREDIT" else "DEBIT"
        val counterLeg = if (!isDuplicate) {
            txnRepo.findTransferCounterpart(
                amount = p.amountMinor,
                oppositeDirection = opposite,
                account = p.accountKey,
                from = p.occurredAt - SelfTransferDetector.WINDOW_MS,
                to = p.occurredAt + SelfTransferDetector.WINDOW_MS,
            )
        } else {
            null
        }
        val isSelfTransfer = counterLeg != null
        if (counterLeg != null) {
            txnRepo.update(
                counterLeg.copy(excludedFromExpense = true, categoryId = CATEGORY_TRANSFERS),
            )
        }

        val isSalary = p.direction == TxnDirection.CREDIT && SALARY_RE.containsMatchIn(msg.body)
        val categoryId = when {
            isSelfTransfer -> CATEGORY_TRANSFERS
            isSalary -> CATEGORY_INCOME
            else -> categoryRepo.categorizer().categorize(p.counterparty)
        }

        val groupId = when (verdict) {
            is DuplicateDetector.Verdict.Duplicate -> verdict.groupId
            is DuplicateDetector.Verdict.Probable -> verdict.groupId
            DuplicateDetector.Verdict.Unique -> null
        }
        // Make sure the original member also carries the group id so they cluster in Review.
        val originalId = when (verdict) {
            is DuplicateDetector.Verdict.Duplicate -> verdict.originalId
            is DuplicateDetector.Verdict.Probable -> verdict.originalId
            DuplicateDetector.Verdict.Unique -> null
        }
        if (groupId != null && originalId != null) {
            candidates.firstOrNull { it.id == originalId && it.dupGroupId == null }
                ?.let { txnRepo.update(it.copy(dupGroupId = groupId)) }
        }

        val entity = TransactionEntity(
            rawSmsId = rawId,
            amountMinor = p.amountMinor,
            currency = p.currency,
            amountBaseMinor = amountBaseMinor,
            direction = p.direction.name,
            accountKey = p.accountKey,
            counterparty = p.counterparty,
            balanceMinor = p.balanceMinor,
            referenceId = p.referenceId,
            occurredAt = p.occurredAt,
            channel = p.channel.name,
            categoryId = categoryId,
            dupGroupId = groupId,
            isDuplicate = isDuplicate,
            excludedFromExpense = isSelfTransfer,
        )
        val id = txnRepo.insert(entity)
        return entity.copy(id = id)
    }

    private companion object {
        const val DAY = 86_400_000L
        const val LEARNED_PRIORITY = 60 // above broad built-ins, learned-format specific
        const val CATEGORY_TRANSFERS = 10L // DefaultCategories "Transfers"
        const val CATEGORY_INCOME = 9L // DefaultCategories "Income"
        val SALARY_RE = Regex("(?i)\\b(salary|payroll|wages|stipend)\\b")
    }
}

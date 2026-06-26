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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.spendlens.app.parser.CardBillParser
import com.spendlens.app.data.repository.CategoryRepository
import com.spendlens.app.data.repository.MerchantRepository
import com.spendlens.app.data.repository.PatternRepository
import com.spendlens.app.data.repository.TransactionRepository
import com.spendlens.app.parser.AccountExtractor
import com.spendlens.app.parser.DuplicateDetector
import com.spendlens.app.parser.FinancialSenderFilter
import com.spendlens.app.parser.FinancialSmsFilter
import com.spendlens.app.parser.MerchantEchoDetector
import com.spendlens.app.parser.MerchantExtractor
import com.spendlens.app.parser.PatternEngine
import com.spendlens.app.parser.SelfTransferDetector
import com.spendlens.app.parser.model.CompiledPattern
import com.spendlens.app.parser.model.MatchResult
import com.spendlens.app.parser.model.SmsMessage
import com.spendlens.app.parser.model.TxnDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SmsProcessingProgress(
    val current: Int = 0,
    val total: Int = 0,
    val isProcessing: Boolean = false
)


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
    /**
     * Supplier evaluated on every [process] call. When it returns `true` only SMS from
     * senders recognised by [FinancialSenderFilter] are processed; all others are IGNORED.
     * Defaults to `false` (filter disabled) so callers that don't wire the setting still
     * behave as before.
     */
    private val financialSendersOnly: () -> Boolean = { false },
) {

    private val _progress = MutableStateFlow(SmsProcessingProgress())
    val progress: StateFlow<SmsProcessingProgress> = _progress.asStateFlow()


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

        // When "Financial senders only" is enabled in Settings, reject SMS from senders that
        // are not recognised banks or financial-service institutions before any content parsing.
        if (financialSendersOnly() && !FinancialSenderFilter.isFinancialSender(msg)) {
            rawDao.updateStatus(rawId, RawStatus.IGNORED, null)
            return null
        }

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

    /**
     * Immediately re-parse a specific set of raw SMS using the current pattern set and run each
     * through the full [persistTransaction] pipeline (merchant extractor, categoriser, duplicate
     * detector). Used to give the user instant feedback after AI pattern teaching — the exact SMS
     * that were submitted to the teacher update right away; the full inbox backlog is handled
     * by the background PatternApplyWorker.
     *
     * Only the SMS that MATCH a pattern are updated; unmatched rows are left unchanged.
     * Returns how many rows were updated.
     */
    suspend fun reprocessSpecificSms(rawList: List<RawSmsEntity>): Int = withContext(Dispatchers.IO) {
        val patterns = patternRepo.compiled()
        var changed = 0
        for (raw in rawList) {
            val msg = SmsMessage(sender = raw.sender, body = raw.body, receivedAt = raw.receivedAt)
            val result = engine.match(msg, patterns) ?: continue
            result.patternId.takeIf { it > 0 }?.let { patternRepo.incrementMatch(it) }
            txnRepo.deleteByRawSmsId(raw.id)
            persistTransaction(raw.id, msg, result)
            rawDao.updateStatus(raw.id, RawStatus.PARSED, result.patternId)
            changed++
        }
        return@withContext changed
    }

    /**
     * Re-run the filter + pattern match over every SMS currently stuck in UNPARSED. Newly added
     * builtin patterns turn matches into transactions (→ PARSED); the tightened financial filter
     * turns non-transactions into IGNORED. Does NOT invoke the AI/heuristic generator — bulk
     * backlog clearing stays offline and free. Returns how many rows changed status.
     */
    suspend fun reprocessUnparsed(): Int {
        val patterns = patternRepo.compiled()
        var changed = 0
        for (raw in rawDao.listByStatus(RawStatus.UNPARSED)) {
            val msg = SmsMessage(sender = raw.sender, body = raw.body, receivedAt = raw.receivedAt)
            if (!FinancialSmsFilter.isFinancial(msg)) {
                rawDao.updateStatus(raw.id, RawStatus.IGNORED, null)
                changed++
                continue
            }
            val result = engine.match(msg, patterns) ?: continue
            result.patternId.takeIf { it > 0 }?.let { patternRepo.incrementMatch(it) }
            persistTransaction(raw.id, msg, result)
            rawDao.updateStatus(raw.id, RawStatus.PARSED, result.patternId)
            changed++
        }
        return changed
    }

    /**
     * Targeted reprocess triggered when one or more patterns are created or modified.
     *
     * Only processes two narrow sets:
     * 1. SMS rows whose [RawSmsEntity.patternId] is in [patternIds] — these were previously parsed
     *    by the patterns that just changed and must be re-matched against the updated regexes.
     * 2. All [RawStatus.UNPARSED] rows — they may now match the new/updated patterns.
     *
     * Every other PARSED row is left untouched, making this vastly cheaper than [reprocessAllSms]
     * when only a handful of SMS share the affected pattern.
     *
     * Returns the number of rows whose status or transaction data changed.
     */
    suspend fun reprocessForPatterns(patternIds: List<Long>): Int = withContext(Dispatchers.IO) {
        if (patternIds.isEmpty()) return@withContext reprocessAllSms()

        val patterns = patternRepo.compiled()
        var changed = 0

        val affectedByPattern: List<RawSmsEntity> = patternIds
            .flatMap { id -> rawDao.listByPatternId(id) }
            .distinctBy { it.id }

        val unparsed: List<RawSmsEntity> = rawDao.listByStatus(RawStatus.UNPARSED)

        val toProcess: List<RawSmsEntity> = (affectedByPattern + unparsed).distinctBy { it.id }

        _progress.value = SmsProcessingProgress(current = 0, total = toProcess.size, isProcessing = true)

        val existingTxnsMap = txnRepo.getAllTransactions()
            .filter { it.rawSmsId != null }
            .associateBy { it.rawSmsId!! }

        for ((index, raw) in toProcess.withIndex()) {
            _progress.value = SmsProcessingProgress(
                current = index + 1,
                total = toProcess.size,
                isProcessing = true,
            )
            val msg = SmsMessage(sender = raw.sender, body = raw.body, receivedAt = raw.receivedAt)
            val existing = existingTxnsMap[raw.id]

            if (!FinancialSmsFilter.isFinancial(msg)) {
                if (existing != null && existing.userVerified) continue
                if (existing != null || raw.status != RawStatus.IGNORED) {
                    txnRepo.deleteByRawSmsId(raw.id)
                    rawDao.updateStatus(raw.id, RawStatus.IGNORED, null)
                    changed++
                }
                continue
            }

            val result = engine.match(msg, patterns)
            if (result != null) {
                val matchedPattern = patterns.firstOrNull { it.id == result.patternId }
                val isUserPattern = matchedPattern != null && matchedPattern.priority >= LEARNED_PRIORITY
                if (existing != null && existing.userVerified && !isUserPattern) continue
                txnRepo.deleteByRawSmsId(raw.id)
                result.patternId.takeIf { it > 0 }?.let { patternRepo.incrementMatch(it) }
                persistTransaction(raw.id, msg, result)
                rawDao.updateStatus(raw.id, RawStatus.PARSED, result.patternId)
                changed++
            } else {
                if (existing != null && existing.userVerified) continue
                if (existing != null || raw.status != RawStatus.UNPARSED) {
                    txnRepo.deleteByRawSmsId(raw.id)
                    rawDao.updateStatus(raw.id, RawStatus.UNPARSED, null)
                    changed++
                }
            }
        }

        _progress.value = SmsProcessingProgress(isProcessing = false)
        return@withContext changed
    }

    suspend fun reprocessAllSms(): Int = withContext(Dispatchers.IO) {
        val patterns = patternRepo.compiled()
        val userPatterns = patterns.filter { it.priority >= LEARNED_PRIORITY }
        var changed = 0
        val rawMessages = rawDao.listByStatus(RawStatus.UNPARSED) + rawDao.listByStatus(RawStatus.PARSED)
        
        _progress.value = SmsProcessingProgress(current = 0, total = rawMessages.size, isProcessing = true)

        // Fetch all transactions once and build a rawSmsId-to-transaction lookup map
        val existingTxnsMap = txnRepo.getAllTransactions()
            .filter { it.rawSmsId != null }
            .associateBy { it.rawSmsId!! }

        for ((index, raw) in rawMessages.withIndex()) {
            _progress.value = SmsProcessingProgress(current = index + 1, total = rawMessages.size, isProcessing = true)
            val msg = SmsMessage(sender = raw.sender, body = raw.body, receivedAt = raw.receivedAt)
            val existing = existingTxnsMap[raw.id]
            if (!FinancialSmsFilter.isFinancial(msg)) {
                if (existing != null && existing.userVerified) {
                    continue
                }
                if (existing != null || raw.status != RawStatus.IGNORED) {
                    txnRepo.deleteByRawSmsId(raw.id)
                    rawDao.updateStatus(raw.id, RawStatus.IGNORED, null)
                    changed++
                }
                continue
            }

            // OPTIMIZATION: If already parsed, only reprocess if it matches a user pattern
            if (raw.status == RawStatus.PARSED) {
                val matchesUserPattern = userPatterns.any { pat ->
                    (pat.sender == null || pat.sender.containsMatchIn(raw.sender)) &&
                    pat.body.containsMatchIn(raw.body)
                }
                if (!matchesUserPattern) {
                    continue
                }
            }

            val result = engine.match(msg, patterns)
            if (result != null) {
                val matchedPattern = patterns.firstOrNull { it.id == result.patternId }
                val isUserPattern = matchedPattern != null && matchedPattern.priority >= LEARNED_PRIORITY
                if (existing != null && existing.userVerified && !isUserPattern) {
                    continue
                }
                txnRepo.deleteByRawSmsId(raw.id)
                result.patternId.takeIf { it > 0 }?.let { patternRepo.incrementMatch(it) }
                persistTransaction(raw.id, msg, result)
                rawDao.updateStatus(raw.id, RawStatus.PARSED, result.patternId)
                changed++
            } else {
                if (existing != null && existing.userVerified) {
                    continue
                }
                if (existing != null || raw.status != RawStatus.UNPARSED) {
                    txnRepo.deleteByRawSmsId(raw.id)
                    rawDao.updateStatus(raw.id, RawStatus.UNPARSED, null)
                    changed++
                }
            }
        }
        _progress.value = SmsProcessingProgress(isProcessing = false)
        return@withContext changed
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

        // Merchant echo: a CREDIT the user never received — the merchant (e.g. Jio) acknowledging
        // a payment you already made. Suppress it so the real debit stands as the spend.
        val echoedDebit = if (!isDuplicate && !isSelfTransfer && p.direction == TxnDirection.CREDIT) {
            val recentDebits = txnRepo.findByAmountDirection(
                amount = p.amountMinor,
                direction = TxnDirection.DEBIT.name,
                from = p.occurredAt - MerchantEchoDetector.WINDOW_MS,
                to = p.occurredAt + MerchantEchoDetector.WINDOW_MS,
            )
            MerchantEchoDetector.echoedDebit(p, msg.body, recentDebits)
        } else {
            null
        }
        val isEcho = echoedDebit != null

        val isSalary = p.direction == TxnDirection.CREDIT && (
            SALARY_RE.containsMatchIn(msg.body) ||
            (p.counterparty.isNotBlank() && p.counterparty != "Unknown" && txnRepo.hasHistoricalIncome(p.counterparty, CATEGORY_INCOME))
        )
        // Mutual-fund / brokerage movements are not spend — file under Transfers and exclude,
        // same as self-transfers.
        val isInvestment = INVESTMENT_RE.containsMatchIn(msg.body)
        val categoryId = when {
            isSelfTransfer -> CATEGORY_TRANSFERS
            isInvestment -> CATEGORY_TRANSFERS
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
            tags = merchantRepo.tagsFor(p.counterparty),
            dupGroupId = groupId ?: echoedDebit?.let { it.dupGroupId ?: "grp-${it.id}" },
            isDuplicate = isDuplicate || isEcho,
            excludedFromExpense = isSelfTransfer || isInvestment || isEcho ||
                merchantRepo.isExcluded(p.counterparty),
        )
        val id = txnRepo.insert(entity)
        return entity.copy(id = id)
    }

    internal companion object {
        const val DAY = 86_400_000L
        const val LEARNED_PRIORITY = 60 // above broad built-ins, learned-format specific
        const val CATEGORY_TRANSFERS = 10L // DefaultCategories "Transfers"
        const val CATEGORY_INCOME = 9L // DefaultCategories "Income"
        val SALARY_RE = Regex("(?i)\\b(salary|payroll|wages|stipend|sal|pension|allowance|reimbursement|reimb|payout|direct deposit|direct dep|nach|ecs|ach)\\b")
        val INVESTMENT_RE = Regex("(?i)\\b(sip|folio|elss|redemption|settlement|mutual fund)\\b")
    }
}

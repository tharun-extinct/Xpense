package dev.expensetracker.app.ingestion

import dev.expensetracker.app.categorization.MerchantCategorizer
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.data.entity.AccountEntity
import dev.expensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.expensetracker.app.data.entity.TransactionDirection
import dev.expensetracker.app.data.entity.TransactionEntity
import dev.expensetracker.app.data.entity.TransactionState
import dev.expensetracker.app.parsing.MessageType
import dev.expensetracker.app.parsing.TransactionParser

/**
 * Shared pipeline used by both SmsReceiver (live) and SmsBackfillWorker (historical), so the
 * two entry points can never diverge in dedupe/parse/categorize behavior. See the
 * "SMS-to-transaction pipeline" cross-cutting flow in architecture.md.
 *
 * Never throws: any parsing/categorization exception is caught here and converted to a
 * silently-ignored message, per architecture.md #error-and-recovery.
 */
class SmsPipeline(
    private val repository: ExpenseRepository,
    private val parser: TransactionParser,
    private val categorizer: MerchantCategorizer,
) {

    /** issuerHint lets the caller pass along a best-effort issuer name derived from the sender id. */
    suspend fun process(sender: String, rawBody: String, receivedAtUtcMillis: Long, issuerHint: String) {
        try {
            if (!SmsNormalizer.isAllowlistedSender(sender)) return

            val normalizedBody = SmsNormalizer.normalizeBody(rawBody)
            val hash = SmsNormalizer.contentHash(sender, normalizedBody, receivedAtUtcMillis)

            if (repository.isHashProcessed(hash)) return

            val parsed = parser.parse(normalizedBody)

            // Parse before claiming the hash. Claiming first meant a message the rules could
            // not yet handle was recorded as "processed" forever, so improving the rules could
            // never recover it on a rescan.
            if (parsed.messageType == MessageType.IGNORE || parsed.messageType == MessageType.UNRECOGNIZED) {
                return
            }

            val direction = when (parsed.messageType) {
                MessageType.DEBIT -> TransactionDirection.DEBIT
                MessageType.CREDIT -> TransactionDirection.CREDIT
                else -> return
            }

            val amount = parsed.amountMinor ?: return

            // Claimed only now that this is known to be a transaction, so the claim and the
            // inserted row always agree and a concurrent live/backfill race still inserts once.
            if (!repository.tryClaimHash(hash, System.currentTimeMillis())) return

            val categorization = categorizer.categorize(parsed)

            val transaction = TransactionEntity(
                contentHash = hash,
                amountMinor = amount,
                direction = direction,
                merchantRaw = parsed.merchantRaw ?: "Unknown",
                merchantKey = categorization.merchantKey,
                category = if (categorization.merchantKey.isBlank()) UNKNOWN_CATEGORY_ID else categorization.category,
                accountIssuer = issuerHint,
                accountTail = parsed.accountTail,
                refNo = parsed.refNo,
                balanceMinor = parsed.balanceMinor,
                occurredAtUtcMillis = receivedAtUtcMillis,
                state = categorization.state.takeIf { categorization.merchantKey.isNotBlank() } ?: TransactionState.NEEDS_REVIEW,
                isUserEdited = false,
                createdAtUtcMillis = System.currentTimeMillis(),
            )

            repository.insertTransaction(transaction)

            if (!parsed.accountTail.isNullOrBlank()) {
                repository.upsertAccount(
                    AccountEntity(
                        issuer = issuerHint,
                        accountTail = parsed.accountTail,
                        lastSeenBalanceMinor = parsed.balanceMinor,
                        lastSeenAtUtcMillis = receivedAtUtcMillis,
                    ),
                )
            }
        } catch (e: Exception) {
            // A single malformed SMS must never crash ingestion for the rest of the inbox.
            // No message content is logged here, per the privacy invariant.
        }
    }
}

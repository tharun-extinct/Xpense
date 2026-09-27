package dev.xpensetracker.app.data

import dev.xpensetracker.app.data.dao.CategorySpend
import dev.xpensetracker.app.data.dao.MerchantSpend
import dev.xpensetracker.app.data.entity.AccountEntity
import dev.xpensetracker.app.data.entity.BudgetEntity
import dev.xpensetracker.app.data.entity.BuiltInCategories
import dev.xpensetracker.app.data.entity.CategoryEntity
import dev.xpensetracker.app.data.entity.MerchantRuleEntity
import dev.xpensetracker.app.data.entity.RuleSource
import dev.xpensetracker.app.data.entity.SmsHashEntity
import dev.xpensetracker.app.data.entity.TransactionDirection
import dev.xpensetracker.app.data.entity.TransactionEntity
import dev.xpensetracker.app.data.entity.TransactionState
import dev.xpensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Thin façade over the Room DAOs so callers (ingestion, UI viewmodels) depend on one
 * object instead of five DAOs. Contains no business logic beyond simple delegation
 * and the dedupe-then-insert sequencing described in architecture.md's ingestion flow.
 */
class ExpenseRepository(private val db: AppDatabase) {

    /** True when this SMS already produced a transaction on an earlier pass. */
    suspend fun isHashProcessed(contentHash: String): Boolean = db.smsHashDao().exists(contentHash)

    /**
     * Records this SMS as processed, returning true if the claim was won. Callers must only
     * claim once they have decided the message is a transaction, so a message the parser
     * could not handle stays eligible for a later rescan with improved rules.
     */
    suspend fun tryClaimHash(contentHash: String, nowUtcMillis: Long): Boolean {
        val inserted = db.smsHashDao().insert(SmsHashEntity(contentHash, nowUtcMillis))
        return inserted != -1L
    }

    /**
     * Drops dedupe rows that never produced a transaction. Installs that ran a scan under the
     * old claim-before-parse ordering carry hashes for messages that were never imported;
     * without this a rescan would keep skipping them.
     */
    suspend fun clearOrphanHashes() = db.smsHashDao().deleteOrphans()

    suspend fun insertTransaction(transaction: TransactionEntity) = db.transactionDao().insert(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) = db.transactionDao().update(transaction)

    suspend fun getTransaction(id: Long) = db.transactionDao().getById(id)

    fun observeTransaction(id: Long): Flow<TransactionEntity?> = db.transactionDao().observeById(id)

    fun observeRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>> =
        db.transactionDao().observeRecent(limit)

    fun observeEarliestTransactionMillis(): Flow<Long?> =
        db.transactionDao().observeEarliestTransactionMillis()

    fun observeAccountSpendForRange(
        issuer: String,
        accountTail: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<Long> = db.transactionDao().observeAccountSpendForRange(issuer, accountTail, startMillis, endMillis)

    fun observeTransactionsForRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
        db.transactionDao().observeForRange(startMillis, endMillis)

    fun observeNeedsReview(): Flow<List<TransactionEntity>> = db.transactionDao().observeNeedsReview()

    fun observeNeedsReviewCount(): Flow<Int> = db.transactionDao().observeNeedsReviewCount()

    fun observeTotalForRange(direction: TransactionDirection, startMillis: Long, endMillis: Long): Flow<Long> =
        db.transactionDao().observeTotalForRange(direction, startMillis, endMillis)

    fun observeCategoryBreakdown(startMillis: Long, endMillis: Long): Flow<List<CategorySpend>> =
        db.transactionDao().observeCategoryBreakdown(startMillis, endMillis)

    fun observeMerchantBreakdown(startMillis: Long, endMillis: Long): Flow<List<MerchantSpend>> =
        db.transactionDao().observeMerchantBreakdown(startMillis, endMillis)

    suspend fun upsertAccount(account: AccountEntity) = db.accountDao().upsert(account)

    fun observeAccounts(): Flow<List<AccountEntity>> = db.accountDao().observeAll()

    suspend fun findMerchantRule(merchantKey: String): MerchantRuleEntity? =
        db.merchantRuleDao().findByKey(merchantKey)

    suspend fun learnMerchantRule(merchantKey: String, category: String) {
        db.merchantRuleDao().upsert(MerchantRuleEntity(merchantKey, category, RuleSource.USER))
    }

    /**
     * Applies a user's category correction to one transaction.
     *
     * Only this row changes, per the forward-only rule in architecture.md #identity-and-ownership;
     * [alsoTeachMerchant] additionally records a USER merchant rule so *future* transactions from
     * the same merchant pick the category up, which is why teaching is opt-in rather than implied.
     */
    suspend fun recategorizeTransaction(
        transactionId: Long,
        categoryId: String,
        alsoTeachMerchant: Boolean,
    ) {
        val transaction = db.transactionDao().getById(transactionId) ?: return
        db.transactionDao().update(
            transaction.copy(
                category = categoryId,
                isUserEdited = true,
                state = if (transaction.state == TransactionState.NEEDS_REVIEW) {
                    TransactionState.CONFIRMED
                } else {
                    transaction.state
                },
            ),
        )
        if (alsoTeachMerchant && transaction.merchantKey.isNotBlank()) {
            learnMerchantRule(transaction.merchantKey, categoryId)
        }
    }

    /**
     * Reclassifies one transaction as money in or money out.
     *
     * The parser infers direction from SMS wording and gets it wrong often enough to matter — a
     * refund or a salary credit read as a debit inflates spends — and direction is what every
     * aggregate keys off, so the user needs a way to correct it. Like a category correction this
     * is row-scoped and forward-only: nothing is learned for the merchant, because "this one was
     * income" says nothing about the next message from the same sender.
     */
    suspend fun setTransactionDirection(transactionId: Long, direction: TransactionDirection) {
        val transaction = db.transactionDao().getById(transactionId) ?: return
        if (transaction.direction == direction) return
        db.transactionDao().update(transaction.copy(direction = direction, isUserEdited = true))
    }

    /**
     * Attaches or clears the user's own note on a transaction. Blank is stored as null so "cleared"
     * and "never written" are the same state and no screen has to distinguish them.
     *
     * The note is never read by the parser or the categorizer: it is for the user's memory, and
     * treating it as an input would make an arbitrary sentence able to change how money is counted.
     */
    suspend fun setTransactionNote(transactionId: Long, note: String?) {
        val transaction = db.transactionDao().getById(transactionId) ?: return
        val cleaned = note?.trim()?.ifBlank { null }
        if (transaction.note == cleaned) return
        db.transactionDao().update(transaction.copy(note = cleaned, isUserEdited = true))
    }

    /**
     * Attaches or clears a location tag. Passing either coordinate as null clears both, because
     * half a coordinate is not a place and storing one would let a screen render a point on the
     * equator that the user never visited.
     */
    suspend fun setTransactionLocation(transactionId: Long, latitude: Double?, longitude: Double?) {
        val transaction = db.transactionDao().getById(transactionId) ?: return
        val lat = latitude.takeIf { longitude != null }
        val lng = longitude.takeIf { latitude != null }
        if (transaction.locationLat == lat && transaction.locationLng == lng) return
        db.transactionDao().update(
            transaction.copy(locationLat = lat, locationLng = lng, isUserEdited = true),
        )
    }

    suspend fun setTransactionState(transactionId: Long, state: TransactionState) {
        val transaction = db.transactionDao().getById(transactionId) ?: return
        db.transactionDao().update(transaction.copy(state = state, isUserEdited = true))
    }

    suspend fun seedMerchantRulesIfEmpty(rules: List<MerchantRuleEntity>) {
        if (db.merchantRuleDao().count() == 0) {
            db.merchantRuleDao().insertSeedRules(rules)
        }
    }

    fun observeBudgetsForMonth(month: String): Flow<List<BudgetEntity>> = db.budgetDao().observeForMonth(month)

    suspend fun setBudget(budget: BudgetEntity) = db.budgetDao().upsert(budget)

    suspend fun clearBudget(month: String, category: String) = db.budgetDao().delete(month, category)

    suspend fun findBudget(month: String, category: String): BudgetEntity? =
        db.budgetDao().findForMonthAndCategory(month, category)

    // --- Categories -------------------------------------------------------------------------
    // The category set is data, not an enum (architecture.md #data-representation). There is no
    // SQL foreign key on the category columns, so the guards below are the integrity boundary.

    /** Everything, archived included, so a historical row can always resolve its label. */
    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()

    /** What pickers offer. */
    fun observeSelectableCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeSelectable()

    suspend fun findCategory(id: String): CategoryEntity? = db.categoryDao().findById(id)

    /**
     * Idempotent, and it runs on every launch rather than only on first run: a database created
     * fresh at version 2 never executes [dev.xpensetracker.app.data.migration.MIGRATION_1_2], so
     * this is what guarantees a new install has the built-ins.
     */
    suspend fun seedCategoriesIfMissing() {
        db.categoryDao().insertSeed(BuiltInCategories.all)
    }

    suspend fun createCustomCategory(name: String, colorArgb: Long, iconKey: String): CategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CategoryResult.InvalidName
        if (db.categoryDao().nameExists(trimmed, excludingId = "")) return CategoryResult.DuplicateName
        db.categoryDao().insert(
            CategoryEntity(
                id = "custom_${UUID.randomUUID()}",
                name = trimmed,
                colorArgb = colorArgb,
                iconKey = iconKey,
                isBuiltIn = false,
                sortOrder = db.categoryDao().maxSortOrder() + 1,
                isArchived = false,
            ),
        )
        return CategoryResult.Success
    }

    suspend fun updateCategory(category: CategoryEntity, newName: String, colorArgb: Long, iconKey: String): CategoryResult {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return CategoryResult.InvalidName
        if (db.categoryDao().nameExists(trimmed, excludingId = category.id)) return CategoryResult.DuplicateName
        db.categoryDao().update(category.copy(name = trimmed, colorArgb = colorArgb, iconKey = iconKey))
        return CategoryResult.Success
    }

    /** Archiving hides a category from pickers without breaking any row that already points at it. */
    suspend fun setCategoryArchived(id: String, archived: Boolean): CategoryResult {
        if (id == UNKNOWN_CATEGORY_ID && archived) return CategoryResult.NotAllowed
        db.categoryDao().setArchived(id, archived)
        return CategoryResult.Success
    }

    /**
     * Hard delete, permitted only when nothing references the category. Anything else would either
     * orphan a row or force a history rewrite, both of which the architecture forbids; the caller
     * is expected to offer archiving instead.
     */
    suspend fun deleteCategory(id: String): CategoryResult {
        if (BuiltInCategories.isBuiltIn(id)) return CategoryResult.NotAllowed
        val references = db.categoryDao().transactionReferenceCount(id) +
            db.categoryDao().budgetReferenceCount(id) +
            db.categoryDao().merchantRuleReferenceCount(id)
        if (references > 0) return CategoryResult.InUse(references)
        return if (db.categoryDao().deleteCustom(id) > 0) CategoryResult.Success else CategoryResult.NotAllowed
    }
}

sealed interface CategoryResult {
    data object Success : CategoryResult
    data object InvalidName : CategoryResult
    data object DuplicateName : CategoryResult
    data object NotAllowed : CategoryResult
    data class InUse(val referenceCount: Int) : CategoryResult
}

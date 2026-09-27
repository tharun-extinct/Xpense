package dev.xpensetracker.app.parsing

/** Output of [TransactionParser]. Pure data, no Android/Room dependency (architecture.md #verification-boundaries). */
enum class MessageType { DEBIT, CREDIT, IGNORE, UNRECOGNIZED }

enum class ConfidenceTier {
    /** Not enough fields extracted to show the user anything meaningful. */
    NONE,

    /** Amount + direction present; must land in NEEDS_REVIEW. */
    LOW,

    /** Amount + direction + merchant + (accountTail or refNo); eligible for auto-CONFIRMED
     *  pending a merchant-categorization match. */
    HIGH,
}

data class ParsedTransaction(
    val messageType: MessageType,
    val amountMinor: Long?,
    val merchantRaw: String?,
    val accountTail: String?,
    val refNo: String?,
    val balanceMinor: Long?,
    val confidence: ConfidenceTier,
    val matchedRuleId: String?,
)

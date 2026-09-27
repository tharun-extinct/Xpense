package dev.xpensetracker.app.ingestion

import java.security.MessageDigest
import java.util.Locale

/**
 * Sender allowlist check, body normalization, and contentHash computation.
 * See blueprints/sms-ingestion.md and architecture.md #identity-and-ownership.
 * No Android imports: sender/body/timestamp are plain values passed in by the caller
 * (SmsReceiver / SmsBackfillWorker), keeping this class JVM-unit-testable.
 */
object SmsNormalizer {

    /**
     * Bank/UPI sender IDs are alphanumeric shortcodes, not phone numbers. Under the Indian DLT
     * regime they are commonly "XX-ISSUER" and increasingly "XX-ISSUER-S", where the trailing
     * segment marks the message category (S/T/P/G). Matching only the two-segment form silently
     * dropped most real traffic, so up to three segments are allowed.
     */
    private val senderPattern = Regex("(?i)^[a-z0-9]{2,15}(-[a-z0-9]{1,15}){0,2}$")

    /** A pure-digit sender is a person or a non-DLT shortcode, never a DLT bank header. */
    private val digitsOnlyPattern = Regex("^\\+?[0-9]+$")

    private val knownIssuerTokens = listOf(
        // Banks
        "hdfc", "sbi", "icici", "axis", "kotak", "pnb", "bob", "barb", "idbi", "yes",
        "indus", "canara", "union", "boi", "cbin", "central", "federal", "rbl", "idfc",
        "aubank", "aubnk", "bandhan", "dbs", "citi", "hsbc", "scb", "standard", "uco",
        "iob", "karur", "kvb", "tmb", "csb", "dcb", "esaf", "equitas", "jupiter", "fi",
        "utkarsh", "ujjivan", "suryoday", "airtel", "jio", "paytm", "fino", "nsdl",
        // UPI apps, wallets, and card issuers
        "phonepe", "gpay", "googlepay", "upi", "amazonpay", "amzn", "mobikwik", "freecharge",
        "cred", "slice", "jupiterm", "onecard", "bajaj", "hdfcbk", "sbicrd", "sbicard",
        "onepay", "razorpay", "juspay", "billdesk", "bhim", "npci",
        // Generic issuer suffixes that appear in headers not covered above, e.g. "AD-XYZBNK".
        "bank", "bnk", "bk", "card", "pay",
    )

    /**
     * The allowlist stays deliberately narrow. Accepting any shortcode would pull in merchant
     * receipts (food delivery, cabs) that restate a spend the bank already reported, so the
     * same purchase would be counted twice. An issuer missing from [knownIssuerTokens] is a
     * one-line addition here.
     */
    fun isAllowlistedSender(sender: String): Boolean {
        val trimmed = sender.trim()
        if (trimmed.isEmpty()) return false
        if (digitsOnlyPattern.matches(trimmed)) return false
        if (!senderPattern.matches(trimmed)) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        return knownIssuerTokens.any { lower.contains(it) }
    }

    /**
     * Best-effort issuer name from a DLT header: "AD-HDFCBK-S" and "VM-HDFCBK" both yield
     * "HDFCBK". The middle segment carries the issuer; the outer ones are the telco prefix
     * and the message-category suffix.
     */
    fun issuerFromSender(sender: String): String {
        val segments = sender.trim().split('-').filter { it.isNotBlank() }
        val issuer = when (segments.size) {
            0 -> sender.trim()
            1 -> segments[0]
            else -> segments[1]
        }
        return issuer.uppercase(Locale.ROOT)
    }

    /** Collapses whitespace and trims; the parser handles case-insensitivity itself. */
    fun normalizeBody(body: String): String = body.replace(Regex("\\s+"), " ").trim()

    /**
     * SHA-256 of sender + normalized body + timestamp truncated to the minute.
     * Two occurrences of the "same" SMS (live + backfill) must hash identically.
     */
    fun contentHash(sender: String, normalizedBody: String, receivedAtUtcMillis: Long): String {
        val truncatedMinute = receivedAtUtcMillis / 60_000L
        val input = "$sender|$normalizedBody|$truncatedMinute"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

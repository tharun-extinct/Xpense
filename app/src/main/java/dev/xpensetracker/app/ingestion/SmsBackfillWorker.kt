package dev.xpensetracker.app.ingestion

import android.content.Context
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.xpensetracker.app.ExpenseTrackerApp
import dev.xpensetracker.app.categorization.MerchantCategorizer

/**
 * On-demand historical inbox scan, triggered only by an explicit user action in onboarding
 * or Settings (never automatically), per blueprints/sms-ingestion.md and
 * blueprints/permissions-and-onboarding.md.
 */
class SmsBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ExpenseTrackerApp
        val parser = RuleAssets.buildParser(applicationContext)
        val categorizer = MerchantCategorizer(app.repository)
        val pipeline = SmsPipeline(app.repository, parser, categorizer)

        // Lets a rescan pick up messages that earlier passes recorded as seen but never
        // imported, so improving the parser rules is enough to recover them.
        app.repository.clearOrphanHashes()

        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = applicationContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        ) ?: return Result.success()

        cursor.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val sender = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val timestamp = it.getLong(dateIdx)
                pipeline.process(sender, body, timestamp, issuerHint = SmsNormalizer.issuerFromSender(sender))
            }
        }

        return Result.success()
    }
}

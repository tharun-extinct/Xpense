package dev.xpensetracker.app.ingestion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.xpensetracker.app.ExpenseTrackerApp
import dev.xpensetracker.app.categorization.MerchantCategorizer

/** Off-broadcast-thread worker that runs the shared [SmsPipeline] for one live SMS. */
class ProcessSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_TIMESTAMP = "timestamp"
    }

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        val app = applicationContext as ExpenseTrackerApp
        val parser = RuleAssets.buildParser(applicationContext)
        val categorizer = MerchantCategorizer(app.repository)
        val pipeline = SmsPipeline(app.repository, parser, categorizer)

        pipeline.process(sender, body, timestamp, issuerHint = SmsNormalizer.issuerFromSender(sender))
        return Result.success()
    }
}

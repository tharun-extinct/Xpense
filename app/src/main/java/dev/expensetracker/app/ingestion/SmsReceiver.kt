package dev.expensetracker.app.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Manifest-declared receiver for the live SMS path. Per blueprints/sms-ingestion.md, this
 * must complete quickly and never block: it hands each message off to a one-off WorkManager
 * job (ProcessSmsWorker) instead of parsing/writing to Room on the broadcast thread.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val messages = try {
            android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            return
        }

        val grouped = groupBySender(messages)
        for ((sender, body, timestamp) in grouped) {
            enqueueProcessing(context, sender, body, timestamp)
        }
    }

    private data class SmsTriple(val sender: String, val body: String, val timestampMillis: Long)

    private fun groupBySender(messages: Array<SmsMessage>?): List<SmsTriple> {
        if (messages.isNullOrEmpty()) return emptyList()
        val sender = messages[0].originatingAddress ?: return emptyList()
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis
        return listOf(SmsTriple(sender, body, timestamp))
    }

    private fun enqueueProcessing(context: Context, sender: String, body: String, timestampMillis: Long) {
        val data = Data.Builder()
            .putString(ProcessSmsWorker.KEY_SENDER, sender)
            .putString(ProcessSmsWorker.KEY_BODY, body)
            .putLong(ProcessSmsWorker.KEY_TIMESTAMP, timestampMillis)
            .build()

        val request = OneTimeWorkRequestBuilder<ProcessSmsWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}

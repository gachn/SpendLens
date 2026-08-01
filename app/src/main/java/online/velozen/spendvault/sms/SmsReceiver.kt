package com.spendlens.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.spendlens.app.work.SmsSyncWorker

/**
 * Receives new SMS and does the minimum: reconstruct the (possibly multipart) message
 * and enqueue a WorkManager job to parse it off the main thread. docs/DESIGN.md §6.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val sender = parts.first().displayOriginatingAddress
            ?: parts.first().originatingAddress.orEmpty()
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val timestamp = parts.first().timestampMillis.takeIf { it > 0 }
            ?: System.currentTimeMillis()

        SmsSyncWorker.enqueueSingle(context, sender, body, timestamp)
    }
}

package com.spendlens.app.sms

import android.content.Context
import android.provider.Telephony
import com.spendlens.app.parser.model.SmsMessage

/**
 * Reads the existing SMS inbox via ContentResolver and feeds each message through
 * [SmsProcessor]. Idempotent (SmsProcessor dedupes on content hash). docs/DESIGN.md §6.
 */
class SmsImporter(
    private val context: Context,
    private val processor: SmsProcessor,
) {
    suspend fun importAll(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val total = cursor.count
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var done = 0
            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIdx).orEmpty()
                val body = cursor.getString(bodyIdx).orEmpty()
                val date = cursor.getLong(dateIdx)
                runCatching { processor.process(SmsMessage(sender, body, date)) }
                onProgress(++done, total)
            }
        }
    }
}

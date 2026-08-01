package com.spendlens.app.sms

import android.content.Context
import android.provider.Telephony
import com.spendlens.app.parser.model.SmsMessage

/**
 * Reads the SMS inbox via ContentResolver and feeds each message through [SmsProcessor].
 * Idempotent — SmsProcessor dedupes on content hash. docs/DESIGN.md §6.
 *
 * Pass [since] (epoch millis, inclusive lower-bound excluded) to limit the scan to SMS
 * received **after** that timestamp. On the very first import [since] should be 0 so the
 * entire inbox is ingested; on subsequent cold starts pass the latest [raw_sms.receivedAt]
 * already in the database so only genuinely new inbox messages are processed.
 */
class SmsImporter(
    private val context: Context,
    private val processor: SmsProcessor,
) {
    suspend fun importAll(
        since: Long = 0L,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

        val selection = if (since > 0L) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs = if (since > 0L) arrayOf(since.toString()) else null

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
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

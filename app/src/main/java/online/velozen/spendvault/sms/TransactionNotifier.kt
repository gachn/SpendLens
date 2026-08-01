package com.spendlens.app.sms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.spendlens.app.MainActivity
import com.spendlens.app.R
import com.spendlens.app.SpendLensApp
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.util.Money

object TransactionNotifier {
    private val COLOR_CREDIT = Color.parseColor("#4CAF50")
    private val COLOR_DEBIT  = Color.parseColor("#F44336")

    fun notify(context: Context, txn: TransactionEntity) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val isDebit = txn.direction == "DEBIT"
        val amount = Money.format(txn.amountMinor, txn.currency)
        val amountTag = if (isDebit) "-$amount" else "+$amount"
        val title = if (isDebit)
            "$amountTag at ${txn.counterparty}"
        else
            "$amountTag from ${txn.counterparty}"
        val text = buildString {
            append(txn.channel)
            if (txn.accountKey.isNotEmpty()) append(" · ${txn.accountKey}")
        }
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TXN_ID, txn.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            txn.id.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, SpendLensApp.CHANNEL_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(if (isDebit) COLOR_DEBIT else COLOR_CREDIT)
            .setColorized(true)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(txn.id.toInt(), notification)
    }
}

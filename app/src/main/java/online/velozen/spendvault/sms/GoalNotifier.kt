package online.velozen.spendvault.sms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import online.velozen.spendvault.MainActivity
import online.velozen.spendvault.R
import online.velozen.spendvault.SpendVaultApp
import online.velozen.spendvault.data.db.SavingsGoalEntity
import online.velozen.spendvault.ui.util.Money

/** Fires the one-shot "savings goal reached" notification (issue #12). */
object GoalNotifier {
    fun notify(context: Context, goal: SavingsGoalEntity) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val title = "🎉 Goal reached: ${goal.name}"
        val text = "You've hit your ${Money.format(goal.targetAmountMinor, "INR")} target."
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("goal" + goal.id).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, SpendVaultApp.CHANNEL_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(("goal" + goal.id).hashCode(), notification)
    }
}

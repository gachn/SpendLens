package com.spendlens.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.spendlens.app.MainActivity
import com.spendlens.app.R
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.repository.BudgetRepository
import com.spendlens.app.data.repository.CategoryRepository
import com.spendlens.app.data.repository.TransactionRepository
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.data.db.CategoryTotal
import com.spendlens.app.ui.util.Money

/**
 * Builds and pushes RemoteViews for both home-screen widgets. Called by [WidgetRefreshWorker]
 * every 30 minutes and by [SmsSyncWorker] after each new transaction is parsed.
 */
object WidgetUpdater {

    suspend fun update(
        context: Context,
        txnRepo: TransactionRepository,
        budgetRepo: BudgetRepository,
        categoryRepo: CategoryRepository,
    ) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        val (from, to) = Dates.currentMonth()
        val monthLabel = Dates.monthLabel()

        val categoryTotals = txnRepo.categoryTotalsBetween(from, to)
        val totalSpentMinor = categoryTotals.sumOf { it.total }

        val budgets = budgetRepo.all()
        val totalBudgetMinor = budgets.sumOf { it.monthlyLimitMinor }

        val categoriesById: Map<Long?, CategoryEntity> = categoryRepo.all().associateBy { it.id }

        updateSpendWidgets(awm, context, totalSpentMinor, totalBudgetMinor, monthLabel)
        updateCategoryWidgets(awm, context, categoryTotals
            .sortedByDescending { it.total }
            .take(3), categoriesById, monthLabel)
    }

    private fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun updateSpendWidgets(
        awm: AppWidgetManager,
        context: Context,
        spentMinor: Long,
        budgetMinor: Long,
        monthLabel: String,
    ) {
        val ids = awm.getAppWidgetIds(ComponentName(context, SpendWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_spend)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        views.setTextViewText(R.id.tv_month, monthLabel)

        if (budgetMinor > 0) {
            val pct = (spentMinor * 100 / budgetMinor).toInt().coerceIn(0, 100)
            views.setTextViewText(
                R.id.tv_amounts,
                "${Money.compact(spentMinor, "INR")} / ${Money.compact(budgetMinor, "INR")}",
            )
            views.setProgressBar(R.id.pb_spend, 100, pct, false)
            views.setViewVisibility(R.id.pb_spend, View.VISIBLE)
            views.setTextViewText(R.id.tv_percent, "$pct% of budget used")
        } else {
            views.setTextViewText(R.id.tv_amounts, Money.compact(spentMinor, "INR"))
            views.setViewVisibility(R.id.pb_spend, View.GONE)
            views.setTextViewText(R.id.tv_percent, "No budget set")
        }

        awm.updateAppWidget(ids, views)
    }

    private fun updateCategoryWidgets(
        awm: AppWidgetManager,
        context: Context,
        topCats: List<CategoryTotal>,
        categoriesById: Map<Long?, CategoryEntity>,
        monthLabel: String,
    ) {
        val ids = awm.getAppWidgetIds(ComponentName(context, CategoryWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_category)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        views.setTextViewText(R.id.tv_month, monthLabel)

        val rowIds = listOf(
            Triple(R.id.row1, R.id.tv_cat1_icon, R.id.tv_cat1_name) to R.id.tv_cat1_amount,
            Triple(R.id.row2, R.id.tv_cat2_icon, R.id.tv_cat2_name) to R.id.tv_cat2_amount,
            Triple(R.id.row3, R.id.tv_cat3_icon, R.id.tv_cat3_name) to R.id.tv_cat3_amount,
        )

        rowIds.forEachIndexed { i, (ids3, amtId) ->
            val (rowId, iconId, nameId) = ids3
            val entry = topCats.getOrNull(i)
            if (entry == null) {
                views.setViewVisibility(rowId, View.INVISIBLE)
            } else {
                views.setViewVisibility(rowId, View.VISIBLE)
                val cat = categoriesById[entry.categoryId]
                views.setTextViewText(iconId, cat?.icon ?: "💳")
                views.setTextViewText(nameId, cat?.name ?: "Other")
                views.setTextViewText(amtId, Money.compact(entry.total, "INR"))
            }
        }

        awm.updateAppWidget(ids, views)
    }
}

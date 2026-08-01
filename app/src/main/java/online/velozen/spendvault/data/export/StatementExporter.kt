package com.spendlens.app.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds user-facing exports of transactions and shares them through the Android share-sheet.
 * Everything is written to app-private external storage (already mapped by the FileProvider) and
 * shared as a content:// URI — nothing is uploaded. See issue #5.
 */
object StatementExporter {

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun exportDir(context: Context): File =
        context.getExternalFilesDir(null) ?: error("External storage unavailable")

    private fun csvEscape(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n'))
            "\"${v.replace("\"", "\"\"")}\"" else v

    /** Write all transaction fields to a CSV file and return it. */
    fun writeCsv(
        context: Context,
        txns: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
    ): File {
        val file = File(exportDir(context), "spendlens_export_${timestamp()}.csv")
        file.bufferedWriter().use { w ->
            w.write("date,merchant,amount,currency,amount_inr,direction,category,account,tags,note\n")
            txns.forEach { t ->
                val cat = t.categoryId?.let { categories[it]?.name }.orEmpty()
                val row = listOf(
                    Dates.date(t.occurredAt),
                    csvEscape(t.counterparty),
                    String.format(Locale.US, "%.2f", t.amountMinor / 100.0),
                    t.currency,
                    String.format(Locale.US, "%.2f", t.amountBaseMinor / 100.0),
                    t.direction,
                    csvEscape(cat),
                    csvEscape(t.accountKey),
                    csvEscape(t.tags.orEmpty()),
                    csvEscape(t.note.orEmpty()),
                )
                w.write(row.joinToString(",") + "\n")
            }
        }
        return file
    }

    // ── PDF statement ────────────────────────────────────────────────────────

    private const val PAGE_W = 595   // A4 @ 72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE = 18f

    /**
     * Render a formatted statement: header, totals summary, per-category spend, then the
     * transaction list. Spend/income totals use base-currency (INR) and ignore non-expense rows.
     */
    fun writePdf(
        context: Context,
        txns: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
    ): File {
        val doc = PdfDocument()
        val title = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
        val h = Paint().apply { color = Color.DKGRAY; textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint().apply { color = Color.DKGRAY; textSize = 11f }
        val light = Paint().apply { color = Color.GRAY; textSize = 10f }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        val spendable = txns.filter { it.direction == "DEBIT" && !it.excludedFromExpense }
        val incomeable = txns.filter { it.direction == "CREDIT" && !it.excludedFromExpense }
        val totalSpend = spendable.sumOf { it.amountBaseMinor }
        val totalIncome = incomeable.sumOf { it.amountBaseMinor }
        val byCategory = spendable
            .groupBy { it.categoryId }
            .map { (catId, list) -> (catId?.let { categories[it]?.name } ?: "Uncategorized") to list.sumOf { t -> t.amountBaseMinor } }
            .sortedByDescending { it.second }

        val rangeLabel = if (txns.isEmpty()) "No transactions" else {
            val min = txns.minOf { it.occurredAt }
            val max = txns.maxOf { it.occurredAt }
            "${Dates.date(min)} – ${Dates.date(max)}"
        }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas
        var y = MARGIN
        var pageNo = 1

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensure(space: Float) { if (y + space > PAGE_H - MARGIN) newPage() }

        // Header
        canvas.drawText("SpendLens Statement", MARGIN, y, title); y += 26f
        canvas.drawText(rangeLabel, MARGIN, y, light); y += LINE
        canvas.drawText("Generated ${Dates.date(System.currentTimeMillis())}", MARGIN, y, light); y += LINE + 6f

        // Summary
        canvas.drawText("Total spent:  ${Money.format(totalSpend, "INR")}", MARGIN, y, body); y += LINE
        canvas.drawText("Total income: ${Money.format(totalIncome, "INR")}", MARGIN, y, body); y += LINE
        canvas.drawText("Net:          ${Money.format(totalIncome - totalSpend, "INR")}", MARGIN, y, body); y += LINE
        canvas.drawText("Transactions: ${txns.size}", MARGIN, y, body); y += LINE + 10f

        // Category totals
        canvas.drawText("Spend by category", MARGIN, y, h); y += LINE
        canvas.drawLine(MARGIN, y - 12f, PAGE_W - MARGIN, y - 12f, rule)
        byCategory.forEach { (name, total) ->
            ensure(LINE)
            canvas.drawText(name, MARGIN, y, body)
            canvas.drawText(Money.format(total, "INR"), PAGE_W - MARGIN - 110f, y, body)
            y += LINE
        }
        y += 14f

        // Transactions
        ensure(LINE * 2)
        canvas.drawText("Transactions", MARGIN, y, h); y += LINE
        canvas.drawLine(MARGIN, y - 12f, PAGE_W - MARGIN, y - 12f, rule)
        val dateX = MARGIN
        val merchantX = MARGIN + 90f
        val catX = MARGIN + 270f
        val amountX = PAGE_W - MARGIN - 110f
        canvas.drawText("Date", dateX, y, light)
        canvas.drawText("Merchant", merchantX, y, light)
        canvas.drawText("Category", catX, y, light)
        canvas.drawText("Amount", amountX, y, light)
        y += LINE

        txns.forEach { t ->
            ensure(LINE)
            val cat = t.categoryId?.let { categories[it]?.name }.orEmpty()
            val sign = if (t.direction == "DEBIT") "-" else "+"
            canvas.drawText(Dates.day(t.occurredAt), dateX, y, body)
            canvas.drawText(ellipsize(t.counterparty, 24), merchantX, y, body)
            canvas.drawText(ellipsize(cat, 16), catX, y, body)
            canvas.drawText(sign + Money.format(t.amountMinor, t.currency), amountX, y, body)
            y += LINE
        }

        doc.finishPage(page)
        val file = File(exportDir(context), "spendlens_statement_${timestamp()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun ellipsize(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"

    /** Share [file] via the Android chooser as [mime]. */
    fun share(context: Context, file: File, mime: String, label: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, label).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}

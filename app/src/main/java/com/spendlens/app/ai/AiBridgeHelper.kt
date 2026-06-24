package com.spendlens.app.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

object AiBridgeHelper {
    fun copyAndLaunch(context: Context, promptText: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SpendLens AI Prompt", promptText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, promptText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Open LLM app (paste your prompt there)")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open AI app", Toast.LENGTH_SHORT).show()
        }
    }
}

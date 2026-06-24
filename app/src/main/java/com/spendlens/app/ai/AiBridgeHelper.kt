package com.spendlens.app.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object AiBridgeHelper {
    /**
     * The prompt text most recently placed on the clipboard by [copyAndLaunch].
     * The clipboard watcher in SpendLensRoot skips this so the prompt's own JSON
     * schema example is never misread as an AI response.
     */
    @Volatile
    var lastCopiedPrompt: String? = null
        private set

    fun copyAndLaunch(context: Context, promptText: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SpendLens AI Prompt", promptText)
            clipboard.setPrimaryClip(clip)
            lastCopiedPrompt = promptText
            Toast.makeText(
                context,
                "Prompt copied! Paste it into your AI app, then copy the reply back here.",
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not copy prompt", Toast.LENGTH_SHORT).show()
        }
    }
}

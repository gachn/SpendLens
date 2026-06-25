package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(permanentlyDenied: Boolean, onGrant: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔎💸", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Text("Welcome to SpendLens", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "SpendLens reads your bank, UPI and card transaction SMS to build a clear picture " +
                    "of where your money goes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Bullet("🔒", "Everything stays on your phone, in an encrypted database.")
            Bullet("📵", "No internet permission — nothing is uploaded anywhere.")
            Bullet("🧠", "Learns new SMS formats automatically as they arrive.")
            Spacer(Modifier.height(28.dp))
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                Text(if (permanentlyDenied) "Open settings to allow SMS" else "Allow SMS access")
            }
            if (permanentlyDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "SMS permission was denied. Enable it in App info → Permissions.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Bullet(emoji: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(emoji)
        Text("  $text", style = MaterialTheme.typography.bodyMedium)
    }
}

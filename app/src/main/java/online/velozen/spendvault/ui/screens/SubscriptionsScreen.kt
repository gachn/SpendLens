package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.ai.RecurringPattern
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.SubscriptionsViewModel

@Composable
fun SubscriptionsScreen(vm: SubscriptionsViewModel) {
    val state by vm.state.collectAsState()

    if (!state.isPremium) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                EmptyHint(
                    "Subscriptions is a Premium insight. Switch to Premium in Settings → Plan to see " +
                        "every recurring merchant SpendLens has detected and how much you're spending " +
                        "on them each month.",
                )
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        if (state.patterns.isEmpty()) {
            item {
                EmptyHint(
                    "No recurring subscriptions detected yet. Once a merchant charges you at least " +
                        "3 times with a regular gap, it'll show up here.",
                )
            }
        } else {
            item {
                ElevatedSurfaceCard {
                    Column {
                        Text(
                            "TOTAL RECURRING SPEND / MONTH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Money.format(state.totalMonthlyMinor, state.currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "across ${state.patterns.size} recurring merchant${if (state.patterns.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionHeader("Detected subscriptions") }
            items(state.patterns) { pattern ->
                SubscriptionCard(pattern, state.currency)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubscriptionCard(pattern: RecurringPattern, currency: String) {
    ElevatedSurfaceCard {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pattern.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "~${Money.format(pattern.estimatedMonthlyMinor, currency)}/mo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        pattern.frequency.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${Money.format(pattern.averageAmountMinor, currency)} × ${pattern.count} charges · " +
                        "${(pattern.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package com.spendlens.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.viewmodel.MerchantRow
import com.spendlens.app.ui.viewmodel.MerchantsViewModel

/**
 * Settings → Merchants: search and edit every known merchant. Editing renames the merchant across
 * past transactions, sets its remembered category and expense flag, and lists the raw SMS tokens
 * ("patterns") that resolve to it — each removable.
 */
@Composable
fun MerchantsScreen(vm: MerchantsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val displayQuery by vm.displayQuery.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "Merchants (${state.items.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = displayQuery,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    label = { Text("Search merchants") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.query.isBlank()) "No merchants yet." else "No merchants match \"${state.query}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.items, key = { it.displayName }) { row ->
                MerchantCard(
                    row = row,
                    categories = state.categories,
                    onSave = { name, catId, excluded, emoji, tags ->
                        vm.save(row, name, catId, excluded, emoji, tags)
                    },
                    onDeleteToken = { vm.deleteToken(it) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantCard(
    row: MerchantRow,
    categories: List<com.spendlens.app.data.db.CategoryEntity>,
    onSave: (name: String, categoryId: Long?, excluded: Boolean, emoji: String?, tags: String?) -> Unit,
    onDeleteToken: (String) -> Unit,
) {
    var expanded by remember(row.displayName) { mutableStateOf(false) }
    var name by remember(row.displayName) { mutableStateOf(row.displayName) }
    var categoryId by remember(row.displayName) { mutableStateOf(row.categoryId) }
    var excluded by remember(row.displayName) { mutableStateOf(row.excluded) }
    var emoji by remember(row.displayName) { mutableStateOf(row.emoji.orEmpty()) }
    var tags by remember(row.displayName) { mutableStateOf(row.tags.orEmpty()) }

    val categoryName = categories.firstOrNull { it.id == row.categoryId }?.let { "${it.icon} ${it.name}" }

    ElevatedSurfaceCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.emoji ?: "🏷️", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(row.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        buildString {
                            append(categoryName ?: "Uncategorized")
                            append(" · ${row.tokens.size} token${if (row.tokens.size == 1) "" else "s"}")
                            if (row.excluded) append(" · excluded")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Merchant name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(2) },
                        singleLine = true,
                        label = { Text("Emoji") },
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Count as expense", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Off keeps it out of all totals.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = !excluded, onCheckedChange = { excluded = !it })
                }

                Spacer(Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = categoryId == cat.id,
                            onClick = { categoryId = if (categoryId == cat.id) null else cat.id },
                            label = { Text("${cat.icon} ${cat.name}") },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    singleLine = true,
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Matched tokens",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Raw SMS merchant strings that resolve to this merchant. Remove one to stop it matching.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (row.tokens.isEmpty()) {
                    Text("None", style = MaterialTheme.typography.labelMedium)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.tokens.forEach { token ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        token,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    IconButton(onClick = { onDeleteToken(token) }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove token",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onSave(name, categoryId, excluded, emoji.ifBlank { null }, tags.ifBlank { null })
                        expanded = false
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save merchant") }
            }
        }
    }
}

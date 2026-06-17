package com.freebox.app.ui.filters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freebox.app.data.SearchFilters
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.SlateBorderFaint
import kotlin.math.roundToInt

@Composable
fun SearchFiltersScreen(
    initialFilters: SearchFilters,
    onApply: (SearchFilters) -> Unit,
    onClose: () -> Unit
) {
    val stringSetSaver = listSaver<MutableState<Set<String>>, String>(
        save = { it.value.toList() },
        restore = { mutableStateOf(it.toSet()) }
    )
    var quickSelects by rememberSaveable(initialFilters, saver = stringSetSaver) { mutableStateOf(initialFilters.quickSelects) }
    var radiusMiles by rememberSaveable(initialFilters) { mutableStateOf(initialFilters.radiusMiles.toFloat()) }
    var minProfit by rememberSaveable(initialFilters) { mutableStateOf(initialFilters.minProfit.toFloat()) }
    var sources by rememberSaveable(initialFilters, saver = stringSetSaver) { mutableStateOf(initialFilters.sources) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Filters",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        val defaults = SearchFilters()
                        quickSelects = defaults.quickSelects
                        radiusMiles = defaults.radiusMiles.toFloat()
                        minProfit = defaults.minProfit.toFloat()
                        sources = defaults.sources
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Quick Select
                Column {
                    Text(
                        text = "Quick select",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickSelectChip(
                            label = "Fast Flip",
                            icon = Icons.Default.Bolt,
                            isSelected = "Fast Flip" in quickSelects,
                            onClick = { quickSelects = quickSelects.toggle("Fast Flip") }
                        )
                        QuickSelectChip(
                            label = "High Margin",
                            icon = Icons.Default.Payments,
                            isSelected = "High Margin" in quickSelects,
                            onClick = { quickSelects = quickSelects.toggle("High Margin") }
                        )
                        QuickSelectChip(
                            label = "Rare",
                            icon = Icons.Default.Diamond,
                            isSelected = "Rare" in quickSelects,
                            onClick = { quickSelects = quickSelects.toggle("Rare") }
                        )
                    }
                }

                // Loot Radius
                FilterCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loot radius",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ValueChip(text = "${radiusMiles.roundToInt()} miles")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = radiusMiles,
                        onValueChange = { radiusMiles = it },
                        valueRange = 1f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1 mi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "50 mi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Profit Potential
                FilterCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Profit potential",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ValueChip(text = "Min $${minProfit.roundToInt()}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = minProfit,
                        onValueChange = { minProfit = it },
                        valueRange = 0f..500f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$500+",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sources
                FilterCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sources",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf("Facebook Marketplace", "Craigslist", "OfferUp").forEach { source ->
                        SourceRow(
                            label = source,
                            isChecked = source in sources,
                            onToggle = { sources = sources.toggle(source) }
                        )
                    }
                }
            }

            // Sticky bottom CTA
            HorizontalDivider(color = SlateBorderFaint)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        onApply(
                            SearchFilters(
                                quickSelects = quickSelects,
                                radiusMiles = radiusMiles.roundToInt(),
                                minProfit = minProfit.roundToInt(),
                                sources = sources
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "Apply Filters", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun FilterCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun QuickSelectChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        checked = isSelected,
        onCheckedChange = { onClick() },
        modifier = Modifier.heightIn(min = 44.dp),
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ValueChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SourceRow(
    label: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SearchFiltersScreenPreview() {
    FreeboxTheme {
        SearchFiltersScreen(
            initialFilters = SearchFilters(),
            onApply = {},
            onClose = {}
        )
    }
}

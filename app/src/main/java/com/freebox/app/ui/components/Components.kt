package com.freebox.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freebox.app.ui.theme.SlateBorderFaint
import com.freebox.app.ui.theme.SlateOutline
import com.freebox.app.ui.theme.SoftSlate

// Listing photos aren't loaded yet (no image pipeline) — show a calm
// tonal placeholder with the category icon instead of a bare gray box.
@Composable
fun PlaceholderImage(
    category: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(SoftSlate, SlateBorderFaint)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon ?: categoryIcon(category),
            // Decorative — the surrounding card already announces title/category.
            contentDescription = null,
            tint = SlateOutline,
            modifier = Modifier.size(40.dp)
        )
    }
}

fun categoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "furniture" -> Icons.Default.Chair
    "electronics", "tech" -> Icons.Default.Devices
    "photography" -> Icons.Default.PhotoCamera
    "computing" -> Icons.Default.Computer
    "plants" -> Icons.Default.LocalFlorist
    "clothing", "apparel" -> Icons.Default.Checkroom
    "food" -> Icons.Default.Restaurant
    "toys" -> Icons.Default.SmartToy
    "books" -> Icons.AutoMirrored.Filled.MenuBook
    "art", "collectibles" -> Icons.Default.Palette
    else -> Icons.Default.Inventory2
}

@Composable
fun ProfitChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LootCard(
    title: String,
    description: String,
    category: String,
    location: String,
    timeAgo: String,
    estProfit: String,
    imageUrl: String? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = 0.dp
    ) {
        Column {
            // Image Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                PlaceholderImage(
                    category = category,
                    modifier = Modifier.fillMaxSize()
                )

                ProfitChip(
                    text = "$estProfit Est.",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            // Content Area
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = SlateBorderFaint)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // labelLarge (14sp SemiBold) keeps white-on-green within the
                        // WCAG large-text exception; labelMedium is too small for it.
                        Text(text = "View Details", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

package com.freebox.app.ui.trends

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebox.app.ui.components.ProfitChip
import com.freebox.app.ui.components.categoryIcon
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.SlateBorderFaint

private data class TopFlip(
    val title: String,
    val category: String,
    val soldCaption: String,
    val profit: String
)

private val topFlips = listOf(
    TopFlip("Vintage Camera", "Photography", "Free → Sold $550 · 3d ago", "+$550"),
    TopFlip("Eames-style Chair", "Furniture", "Free → Sold $240 · 1w ago", "+$240"),
    TopFlip("Retro Game Console", "Electronics", "Free → Sold $180 · 2w ago", "+$180")
)

@Composable
fun TrendsScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Hero: Total Profit
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TOTAL PROFIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$14,250.00",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProfitChip(text = "+12.4% this month")
            }
        }

        // Profit Trends Chart
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, SlateBorderFaint)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = "Profit Trends", style = MaterialTheme.typography.headlineSmall)

                    Spacer(modifier = Modifier.height(24.dp))

                    ProfitTrendsChart(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("May", "Jun", "Jul", "Aug", "Sep", "Oct").forEach { month ->
                            Text(
                                text = month,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // By Category Breakdown
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "By Category", style = MaterialTheme.typography.headlineSmall)
                CategoryRow("Electronics", 0.65f, "65%")
                CategoryRow("Apparel", 0.20f, "20%")
                CategoryRow("Furniture", 0.15f, "15%")
            }
        }

        // Top Flips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Top Flips", style = MaterialTheme.typography.headlineSmall)
                topFlips.forEach { flip ->
                    TopFlipRow(flip)
                }
            }
        }
    }
}

// Line draws itself in once via a clip-reveal so the trend reads
// left-to-right, the way the data accrued.
@Composable
private fun ProfitTrendsChart(modifier: Modifier = Modifier) {
    var revealTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { revealTarget = 1f }
    val reveal by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(durationMillis = 600),
        label = "chart_reveal"
    )

    val lineColor = MaterialTheme.colorScheme.primary
    val fillTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val gridColor = SlateBorderFaint

    Canvas(modifier = modifier) {
        // Subtle horizontal gridlines
        val gridLines = 4
        repeat(gridLines) { i ->
            val y = size.height * (i + 1) / (gridLines + 1)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val line = Path().apply {
            moveTo(0f, size.height * 0.7f)
            quadraticTo(size.width * 0.2f, size.height * 0.6f, size.width * 0.4f, size.height * 0.4f)
            quadraticTo(size.width * 0.6f, size.height * 0.5f, size.width * 0.8f, size.height * 0.2f)
            lineTo(size.width, size.height * 0.1f)
        }

        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        clipRect(right = size.width * reveal) {
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(fillTop, fillTop.copy(alpha = 0f))
                )
            )
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
fun CategoryRow(label: String, fraction: Float, percentage: String) {
    var barTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { barTarget = fraction }
    val barFraction by animateFloatAsState(
        targetValue = barTarget,
        animationSpec = tween(durationMillis = 400),
        label = "category_bar"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = categoryIcon(label),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = percentage,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(SlateBorderFaint)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun TopFlipRow(flip: TopFlip) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, SlateBorderFaint)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon(flip.category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = flip.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = flip.soldCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = flip.profit,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TrendsScreenPreview() {
    FreeboxTheme {
        TrendsScreen()
    }
}

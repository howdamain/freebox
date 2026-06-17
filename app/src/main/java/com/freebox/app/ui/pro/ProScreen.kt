package com.freebox.app.ui.pro

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.GoldAccent
import com.freebox.app.ui.theme.ProfitGreenDeep
import com.freebox.app.ui.theme.SlateBorderFaint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onClose: () -> Unit,
    onStartTrial: () -> Unit = {},
    working: Boolean = false
) {
    var selectedPlan by rememberSaveable { mutableStateOf("Yearly") }
    var unlocked by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Header — close anchor only; the paywall canvas does the talking.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Pro paywall",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Premium badge
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    ProfitGreenDeep
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Headline — gold-to-green gradient on the money words.
                Text(
                    text = buildAnnotatedString {
                        append("Unlock ")
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        GoldAccent,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            )
                        ) {
                            append("Maximum Profit")
                        }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Go Pro and out-scout every flipper in town with tools built for serious treasure hunters.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Benefits
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BenefitRow(
                        icon = Icons.Default.Bolt,
                        title = "Instant Alerts",
                        description = "Be first on the scene when profitable treasure drops."
                    )
                    BenefitRow(
                        icon = Icons.Default.Radar,
                        title = "Unlimited Radius",
                        description = "Scout flips citywide, statewide, nationwide — no caps."
                    )
                    BenefitRow(
                        icon = Icons.Default.Block,
                        title = "Ad-Free Experience",
                        description = "Pure sourcing, zero distractions."
                    )
                    BenefitRow(
                        icon = Icons.Default.Diamond,
                        title = "VIP Support",
                        description = "Priority help from the Freebox crew, 24/7."
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Plan selector
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlanCard(
                        title = "Yearly",
                        subtitle = "Best Value",
                        price = "$29.99",
                        period = "/yr",
                        badge = "SAVE 50%",
                        isSelected = selectedPlan == "Yearly",
                        onClick = { selectedPlan = "Yearly" }
                    )
                    PlanCard(
                        title = "Monthly",
                        price = "$4.99",
                        period = "/mo",
                        isSelected = selectedPlan == "Monthly",
                        onClick = { selectedPlan = "Monthly" }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom CTA — flips inline to a confirmation once "purchased".
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Crossfade(
                    targetState = unlocked,
                    animationSpec = tween(durationMillis = 250),
                    label = "proCtaState"
                ) { isUnlocked ->
                    if (isUnlocked) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "You're in — Pro preview unlocked",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Billing isn't connected in this prototype, so Pro is free for now.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onClose,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = CircleShape
                            ) {
                                Text(text = "Done", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = onStartTrial,
                                enabled = !working,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = CircleShape
                            ) {
                                if (working) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Start 7-Day Free Trial",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cancel anytime. No treasure left behind.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    badge: String? = null
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else SlateBorderFaint,
        animationSpec = tween(durationMillis = 200),
        label = "planBorderColor"
    )

    Surface(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Box {
            if (badge != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(topEnd = 22.dp, bottomStart = 16.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (badge != null) 28.dp else 20.dp,
                        bottom = 20.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = period,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProScreenPreview() {
    FreeboxTheme {
        ProScreen(onClose = {})
    }
}

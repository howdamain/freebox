package com.freebox.app.ui.details

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebox.app.data.LootItem
import com.freebox.app.data.VaultRepository
import kotlinx.coroutines.launch
import com.freebox.app.ui.components.PlaceholderImage
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.SlateBorderFaint

@Composable
fun TreasureDetailsScreen(
    item: LootItem,
    onBack: () -> Unit,
    onClaim: () -> Unit = {}
) {
    var claimed by rememberSaveable { mutableStateOf(false) }
    var favorited by rememberSaveable { mutableStateOf(false) }

    // Reflect and persist the watchlist membership for this listing.
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) {
        favorited = runCatching { VaultRepository.isSaved(item.id) }.getOrDefault(false)
    }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Image Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                PlaceholderImage(
                    category = item.category,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    GlassIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share listing",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Free find on Freebox: ${item.title} — ${item.location}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share this find"))
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassIconButton(
                        icon = if (favorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (favorited) "Remove from saved finds" else "Save this find",
                        tint = if (favorited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            favorited = !favorited
                            scope.launch {
                                runCatching {
                                    if (favorited) VaultRepository.add(item.id)
                                    else VaultRepository.remove(item.id)
                                }
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Available Now",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = Color.White, shape = CircleShape) {
                        Text(
                            text = item.timeAgo,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Curbside • ${item.location} (${item.distanceAway})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bento Stat Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Default.Payments,
                        iconTint = MaterialTheme.colorScheme.primary,
                        badge = "Estimated",
                        label = "RESALE VALUE",
                        value = item.resaleValue,
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    StatCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        icon = Icons.Default.Verified,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        badge = null,
                        label = "CONDITION",
                        value = item.condition,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Scavenger Notes
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Scavenger Notes",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quote formatting: 3dp primary rule marks the finder's words.
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "\"${item.finderNote}\"",
                                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Spotted by",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.finderName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Location",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Map Preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderFaint),
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "Map",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        )

                        // Claiming swaps the locked pill for the revealed address (state change -> motion).
                        Crossfade(
                            targetState = claimed,
                            animationSpec = tween(250),
                            modifier = Modifier.align(Alignment.Center),
                            label = "mapReveal"
                        ) { isClaimed ->
                            if (isClaimed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Address Revealed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(color = Color.White, shape = CircleShape) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.location,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Quiet status row — informational, not a button.
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Reveal Exact Location",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (claimed) "You claimed this listing" else "Exact address revealed upon claiming",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(150.dp))
            }
        }

        // Sticky Bottom CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .navigationBarsPadding()
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(
                    onClick = {
                        if (!claimed) {
                            claimed = true
                            onClaim()
                        }
                    },
                    enabled = !claimed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (claimed) Icons.Default.CheckCircle else Icons.Default.DirectionsRun,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (claimed) "Claimed — Address Revealed" else "Claim Listing",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                AnimatedVisibility(
                    visible = claimed,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Text(
                        text = "You've got dibs. Head over before it's gone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// Overlay icon button on the hero image: near-opaque white scrim keeps the
// icon legible regardless of what sits underneath.
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    badge: String?,
    label: String,
    value: String,
    valueColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TreasureDetailsScreenPreview() {
    FreeboxTheme {
        TreasureDetailsScreen(
            item = LootItem(
                id = "eames-lounge-chair",
                title = "Eames Style Lounge Chair & Ottoman",
                description = "Curbside find in great shape. Genuine leather cushions with minor wear on the ottoman, walnut veneer shell intact.",
                category = "Furniture",
                location = "Oakland, CA",
                timeAgo = "12 mins ago",
                estProfit = "+$350",
                resaleValue = "$350",
                condition = "Good",
                distanceAway = "0.8 mi away",
                finderName = "Alex T. (Top Contributor)",
                finderNote = "Spotted on the curb this morning. Owner confirmed it's free to whoever picks it up first. Bring a truck — it's heavier than it looks.",
                sourceName = "Facebook Marketplace",
                distanceMiles = 0.8,
                profitValue = 350
            ),
            onBack = {}
        )
    }
}

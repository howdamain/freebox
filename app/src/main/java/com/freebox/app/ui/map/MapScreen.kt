package com.freebox.app.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freebox.app.data.FiltersStore
import com.freebox.app.data.LootItem
import com.freebox.app.data.matchesFilters
import com.freebox.app.ui.components.PlaceholderImage
import com.freebox.app.ui.components.categoryIcon
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.SlateBorderFaint
import kotlinx.coroutines.launch

// Deterministic marker spots (fraction of viewport width/height), kept away
// from the edges and the top overlays. Indexed per item in SampleData.
private val markerFractions = listOf(
    0.30f to 0.34f,
    0.72f to 0.42f,
    0.42f to 0.58f,
    0.66f to 0.72f
)

@Composable
fun MapScreen(onItemClick: (String) -> Unit = {}) {
    val vm: MapViewModel = viewModel()
    val ui by vm.state.collectAsState()

    val allItems = ui.items
    // Reading FiltersStore.filters here causes recomposition whenever filters change.
    val filters = FiltersStore.filters
    // Apply FiltersStore predicate; category chip filter is applied per-marker below.
    val items = allItems.filter { matchesFilters(it, filters) }
    val categories = remember(allItems) { listOf("All") + allItems.map { it.category }.distinct() }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val bookmarkSaver = listSaver<MutableState<Set<String>>, String>(
        save = { it.value.toList() },
        restore = { mutableStateOf(it.toSet()) }
    )
    var bookmarkedIds by rememberSaveable(saver = bookmarkSaver) { mutableStateOf(setOf<String>()) }

    val selectedItem = selectedId?.let { id -> allItems.find { it.id == id } }
    // Keep the last shown item so the sheet has content during its exit slide.
    var sheetItem by remember { mutableStateOf<LootItem?>(null) }
    LaunchedEffect(selectedItem) { selectedItem?.let { sheetItem = it } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mock map: tapping empty map dismisses the preview sheet.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { selectedId = null })
                }
        ) {
            StreetGridCanvas(modifier = Modifier.fillMaxSize())

            // Loading overlay while items are being fetched.
            if (ui.loading && allItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val mapWidth = maxWidth
                val mapHeight = maxHeight
                items.forEachIndexed { index, item ->
                    val (fx, fy) = markerFractions[index % markerFractions.size]
                    AnimatedVisibility(
                        visible = selectedCategory == "All" || item.category == selectedCategory,
                        modifier = Modifier.offset(
                            x = mapWidth * fx - 22.dp,
                            y = mapHeight * fy - 22.dp
                        ),
                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f)
                    ) {
                        LootMarker(
                            item = item,
                            selected = selectedId == item.id,
                            onClick = {
                                selectedId = if (selectedId == item.id) null else item.id
                            }
                        )
                    }
                }
            }
        }

        // Top overlays: search bar + filter chips + map controls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            MapSearchBar()

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(categories) { category ->
                        MapFilterChip(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                                if (category != "All" && selectedItem?.category != category) {
                                    selectedId = null
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MyLocationFab()
                    LayersFab()
                }
            }
        }

        // Item preview sheet — only visible while a marker is selected.
        AnimatedVisibility(
            visible = selectedItem != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { fullHeight -> fullHeight } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { fullHeight -> fullHeight } + fadeOut(tween(200))
        ) {
            sheetItem?.let { currentSheetItem ->
                LootPreviewSheet(
                    item = currentSheetItem,
                    bookmarked = currentSheetItem.id in bookmarkedIds,
                    onToggleBookmark = {
                        bookmarkedIds = if (currentSheetItem.id in bookmarkedIds) {
                            bookmarkedIds - currentSheetItem.id
                        } else {
                            bookmarkedIds + currentSheetItem.id
                        }
                    },
                    onClose = { selectedId = null },
                    onViewDetails = { onItemClick(currentSheetItem.id) }
                )
            }
        }
    }
}

// Calm mock street grid: soft slate base, faint cross streets, a few park blobs.
@Composable
private fun StreetGridCanvas(modifier: Modifier = Modifier) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val streetColor = SlateBorderFaint
    val parkColor = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = modifier) {
        drawRect(color = baseColor)

        val w = size.width
        val h = size.height

        // Cross streets — one wide avenue each way, the rest hairlines.
        listOf(0.18f to 1.dp, 0.36f to 3.dp, 0.55f to 1.dp, 0.74f to 2.dp, 0.90f to 1.dp)
            .forEach { (fy, stroke) ->
                drawLine(
                    color = streetColor,
                    start = Offset(0f, h * fy),
                    end = Offset(w, h * fy),
                    strokeWidth = stroke.toPx()
                )
            }
        listOf(0.16f to 2.dp, 0.34f to 1.dp, 0.52f to 3.dp, 0.72f to 1.dp, 0.88f to 2.dp)
            .forEach { (fx, stroke) ->
                drawLine(
                    color = streetColor,
                    start = Offset(w * fx, 0f),
                    end = Offset(w * fx, h),
                    strokeWidth = stroke.toPx()
                )
            }

        // Park blobs.
        drawRoundRect(
            color = parkColor.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.06f, h * 0.42f),
            size = Size(w * 0.22f, h * 0.13f),
            cornerRadius = CornerRadius(28.dp.toPx())
        )
        drawRoundRect(
            color = parkColor.copy(alpha = 0.30f),
            topLeft = Offset(w * 0.62f, h * 0.55f),
            size = Size(w * 0.30f, h * 0.12f),
            cornerRadius = CornerRadius(32.dp.toPx())
        )
        drawRoundRect(
            color = parkColor.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.40f, h * 0.18f),
            size = Size(w * 0.18f, h * 0.09f),
            cornerRadius = CornerRadius(24.dp.toPx())
        )
    }
}

// Search isn't wired on the map yet, so this is intentionally non-interactive
// and visually muted (no mic, dimmed placeholder) so it doesn't read as a
// live input. Real search lives on the Discover tab.
@Composable
private fun MapSearchBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Search is on the Discover tab"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Search for free items...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MapFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = if (selected) 0.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = if (selected) null else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun LootMarker(
    item: LootItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "markerScale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = if (selected) 8.dp else 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = categoryIcon(item.category),
                contentDescription = item.title,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// My-location FAB: tapping fires a brief radar pulse as press feedback while
// scouting. Real GPS isn't wired up yet, so no recenter actually happens.
@Composable
private fun MyLocationFab() {
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pulseColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (pulse.isRunning) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1f + pulse.value
                        scaleY = 1f + pulse.value
                        alpha = 1f - pulse.value
                    }
                    .border(2.dp, pulseColor, CircleShape)
            )
        }
        Surface(
            onClick = {
                scope.launch {
                    pulse.snapTo(0f)
                    pulse.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
                }
            },
            modifier = Modifier.matchParentSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SlateBorderFaint),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter map (coming soon)",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LayersFab() {
    Surface(
        onClick = { /* Map layers aren't available yet */ },
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Map layers (coming soon)",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LootPreviewSheet(
    item: LootItem,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onClose: () -> Unit,
    onViewDetails: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, SlateBorderFaint)
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            // Drag handle + close.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Available Now",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.distanceAway,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Item card.
            Row(modifier = Modifier.fillMaxWidth()) {
                PlaceholderImage(
                    category = item.category,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Free",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${item.estProfit} flip est.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = CircleShape
                ) {
                    Text(
                        text = "View Details",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedIconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(56.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = if (bookmarked) {
                            Icons.Default.Bookmark
                        } else {
                            Icons.Default.BookmarkBorder
                        },
                        contentDescription = if (bookmarked) {
                            "Remove from your stash"
                        } else {
                            "Stash this find"
                        },
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MapScreenPreview() {
    FreeboxTheme {
        MapScreen()
    }
}

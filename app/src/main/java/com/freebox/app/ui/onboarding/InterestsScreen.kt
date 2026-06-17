package com.freebox.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebox.app.R
import com.freebox.app.ui.theme.FreeboxTheme

data class Interest(
    val id: String,
    val name: String,
    val icon: ImageVector
)

@Composable
fun InterestsScreen(
    onContinueClick: (Set<String>) -> Unit = {},
    onSkipClick: () -> Unit = {}
) {
    val interests = remember {
        listOf(
            Interest("furniture", "Furniture", Icons.Default.Chair),
            Interest("electronics", "Electronics", Icons.Default.Devices),
            Interest("plants", "Plants", Icons.Default.LocalFlorist),
            Interest("clothing", "Clothing", Icons.Default.Checkroom),
            Interest("food", "Food", Icons.Default.Restaurant),
            Interest("toys", "Toys", Icons.Default.SmartToy),
            Interest("books", "Books", Icons.Default.MenuBook),
            Interest("art", "Art", Icons.Default.Palette)
        )
    }
    
    val selectedInterests = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Bar
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f) // Step 2 of 4
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step 2 of 4",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSkipClick) {
                    Text(
                        text = stringResource(R.string.action_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Header
            Text(
                text = stringResource(R.string.niche_title),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp), // Reduced for responsiveness
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.niche_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Interests Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = false) // Allow it to wrap or take less space
            ) {
                items(interests) { interest ->
                    val isSelected = selectedInterests.contains(interest.id)
                    InterestCard(
                        interest = interest,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) selectedInterests.remove(interest.id)
                            else selectedInterests.add(interest.id)
                        }
                    )
                }
            }

            // Footer
            val selectedCount = selectedInterests.size
            Button(
                onClick = { onContinueClick(selectedInterests.toSet()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .height(56.dp),
                shape = CircleShape
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectedCount == 0) stringResource(R.string.action_continue)
                        else "Continue with $selectedCount picked",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InterestCard(
    interest: Interest,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLowest
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        checked = isSelected,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        color = backgroundColor,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = interest.icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = interest.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Selection badge — scale+fade communicates the toggle state change.
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)),
                exit = scaleOut(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InterestsScreenPreview() {
    FreeboxTheme {
        InterestsScreen()
    }
}

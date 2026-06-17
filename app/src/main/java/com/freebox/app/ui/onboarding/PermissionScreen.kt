package com.freebox.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebox.app.R
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.SlateBorderFaint

@Composable
fun PermissionScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primaryActionText: String,
    secondaryActionText: String,
    isNotification: Boolean = false,
    permissionsToRequest: Array<String> = emptyArray(),
    onPrimaryClick: () -> Unit = {},
    onSecondaryClick: () -> Unit = {}
) {
    // Proceed regardless of grant result — the choice is the OS dialog's to remember.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPrimaryClick() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Visual Area
            Box(
                modifier = Modifier.size(160.dp), // Reduced from 200
                contentAlignment = Alignment.Center
            ) {
                // Pulse Animation
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale"
                )
                val opacity by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "opacity"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = opacity),
                            CircleShape
                        )
                )

                // Main Icon
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderFaint),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isNotification) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        if (isNotification) {
                            // Bolt Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Text
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp), // Reduced
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Actions
            Button(
                onClick = {
                    if (permissionsToRequest.isEmpty()) onPrimaryClick()
                    else permissionLauncher.launch(permissionsToRequest)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape
            ) {
                Text(text = primaryActionText, style = MaterialTheme.typography.labelLarge)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onSecondaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = secondaryActionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationPermissionPreview() {
    FreeboxTheme {
        PermissionScreen(
            title = stringResource(R.string.location_title),
            subtitle = stringResource(R.string.location_subtitle),
            icon = Icons.Default.LocationOn,
            primaryActionText = stringResource(R.string.action_enable_radar),
            secondaryActionText = stringResource(R.string.action_maybe_later)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationPermissionPreview() {
    FreeboxTheme {
        PermissionScreen(
            title = stringResource(R.string.notification_title),
            subtitle = stringResource(R.string.notification_subtitle),
            icon = Icons.Default.Notifications,
            isNotification = true,
            primaryActionText = stringResource(R.string.action_enable_alerts),
            secondaryActionText = stringResource(R.string.action_skip)
        )
    }
}

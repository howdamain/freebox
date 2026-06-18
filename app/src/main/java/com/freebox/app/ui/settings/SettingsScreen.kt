package com.freebox.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebox.app.R
import com.freebox.app.data.AccountRepository
import com.freebox.app.data.supabase
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.SlateBorderFaint
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onLogOut: () -> Unit = {}) {
    var pushNotifications by rememberSaveable { mutableStateOf(true) }
    var emailSummaries by rememberSaveable { mutableStateOf(false) }
    var facebookMarketplace by rememberSaveable { mutableStateOf(true) }
    var craigslist by rememberSaveable { mutableStateOf(true) }
    var nextdoor by rememberSaveable { mutableStateOf(false) }
    var showLogOutDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isDeletingAccount by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val accountEmail = remember { supabase.auth.currentUserOrNull()?.email ?: "—" }
    val context = LocalContext.current
    val privacyUrl = stringResource(R.string.privacy_policy_url)

    if (showLogOutDialog) {
        AlertDialog(
            onDismissRequest = { showLogOutDialog = false },
            title = { Text(text = "Log out of Freebox?") },
            text = { Text(text = "Your saved finds and alerts stay on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogOutDialog = false
                        onLogOut()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = "Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogOutDialog = false }) {
                    Text(text = "Stay")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteDialog = false },
            title = { Text(text = "Delete your account?") },
            text = {
                Text(
                    text = "This permanently removes your account, saved finds, and alerts. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isDeletingAccount = true
                            runCatching { AccountRepository.deleteAccount() }
                            isDeletingAccount = false
                            showDeleteDialog = false
                            onLogOut()
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(text = "Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Manage your account, alerts, and preferences.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeader("ACCOUNT DETAILS")
            SettingsCard {
                AccountRow(
                    icon = Icons.Default.MailOutline,
                    label = "Email Address",
                    value = accountEmail,
                    actionText = "Edit",
                    onActionClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Account editing is coming soon.")
                        }
                    }
                )
                HorizontalDivider(color = SlateBorderFaint)
                AccountRow(
                    icon = Icons.Default.Password,
                    label = "Password",
                    value = "••••••••",
                    actionText = "Update",
                    onActionClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Account editing is coming soon.")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeader("PROFIT ALERTS")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Push Notifications",
                    subtitle = "Instant alerts for high-value items.",
                    checked = pushNotifications,
                    onCheckedChange = { pushNotifications = it }
                )
                HorizontalDivider(color = SlateBorderFaint)
                ToggleRow(
                    icon = Icons.Default.ForwardToInbox,
                    title = "Email Summaries",
                    subtitle = "Daily digest of top freebies.",
                    checked = emailSummaries,
                    onCheckedChange = { emailSummaries = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeader("LOOT SOURCES")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Default.Public,
                    title = "Facebook Marketplace",
                    checked = facebookMarketplace,
                    onCheckedChange = { facebookMarketplace = it },
                    iconTint = Color(0xFF1877F2),
                    iconBackground = Color(0xFF1877F2).copy(alpha = 0.1f)
                )
                HorizontalDivider(color = SlateBorderFaint)
                ToggleRow(
                    icon = Icons.Default.ShoppingCart,
                    title = "Craigslist",
                    checked = craigslist,
                    onCheckedChange = { craigslist = it },
                    iconTint = Color(0xFF52297A),
                    iconBackground = Color(0xFF52297A).copy(alpha = 0.1f)
                )
                HorizontalDivider(color = SlateBorderFaint)
                ToggleRow(
                    icon = Icons.Default.Eco,
                    title = "Nextdoor",
                    checked = nextdoor,
                    onCheckedChange = { nextdoor = it },
                    iconTint = Color(0xFF00A651),
                    iconBackground = Color(0xFF00A651).copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeader("PRIVACY & SECURITY")
            SettingsCard {
                ChevronRow(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedButton(
                onClick = { showLogOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Log Out", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = SlateBorderFaint)
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("DANGER ZONE")

            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Delete Account", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderFaint),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), content = content)
    }
}

@Composable
private fun RowIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    label: String,
    value: String,
    actionText: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onActionClick,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon, tint = iconTint, background = iconBackground)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ChevronRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    FreeboxTheme {
        SettingsScreen()
    }
}

package com.freebox.app.ui.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freebox.app.ui.theme.FreeboxTheme
import com.freebox.app.ui.theme.InkSlate
import com.freebox.app.ui.theme.ProfitGreenBright
import com.freebox.app.ui.theme.SlateBorder
import com.freebox.app.ui.theme.SurfaceWhite
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class ScanStage { Scanning, Result }

// Staged AR-scanner mock. There is no real camera yet (CameraX lands later) —
// the viewfinder is simulated and the copy says so honestly.
@Composable
fun ScannerScreen(onClose: () -> Unit) {
    var stage by remember { mutableStateOf(ScanStage.Scanning) }
    var saved by remember { mutableStateOf(false) }

    // Stage machine: a scan "completes" after 2.5s and reveals the result.
    LaunchedEffect(stage) {
        if (stage == ScanStage.Scanning) {
            saved = false
            delay(2500)
            stage = ScanStage.Result
        }
    }

    // After saving, hold the success state briefly, then leave the scanner.
    LaunchedEffect(saved) {
        if (saved) {
            delay(800)
            onClose()
        }
    }

    val resultVisible = stage == ScanStage.Result
    val ringProgress by animateFloatAsState(
        targetValue = if (resultVisible) 0.98f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "confidence"
    )

    // Deep slate viewfinder — never pure black, per the design system.
    val viewfinderBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                lerp(InkSlate, SurfaceWhite, 0.10f),
                InkSlate,
                lerp(InkSlate, Color.Black, 0.45f)
            )
        )
    }
    val centerGlow = remember {
        Brush.radialGradient(
            colors = listOf(SurfaceWhite.copy(alpha = 0.06f), Color.Transparent)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(viewfinderBrush)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(centerGlow)
        )

        // Reticle bounding box with corner brackets + sweeping scan line.
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-52).dp)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.4f)
        ) {
            // Brackets brighten to full green when the object locks on.
            val reticleColor by animateColorAsState(
                targetValue = if (resultVisible) {
                    ProfitGreenBright
                } else {
                    ProfitGreenBright.copy(alpha = 0.55f)
                },
                animationSpec = tween(durationMillis = 300),
                label = "reticleColor"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracket = 26.dp.toPx()
                val radius = 14.dp.toPx()
                val strokeStyle = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                val cornerPath = Path().apply {
                    moveTo(0f, bracket)
                    lineTo(0f, radius)
                    quadraticTo(0f, 0f, radius, 0f)
                    lineTo(bracket, 0f)
                }
                drawPath(cornerPath, reticleColor, style = strokeStyle)
                scale(scaleX = -1f, scaleY = 1f) {
                    drawPath(cornerPath, reticleColor, style = strokeStyle)
                }
                scale(scaleX = 1f, scaleY = -1f) {
                    drawPath(cornerPath, reticleColor, style = strokeStyle)
                }
                scale(scaleX = -1f, scaleY = -1f) {
                    drawPath(cornerPath, reticleColor, style = strokeStyle)
                }
            }

            if (stage == ScanStage.Scanning) {
                val sweep = rememberInfiniteTransition(label = "scanSweep")
                val sweepProgress by sweep.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "sweepProgress"
                )
                val boxHeight = maxHeight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .graphicsLayer {
                            translationY = sweepProgress * (boxHeight.toPx() - 3.dp.toPx())
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, ProfitGreenBright, Color.Transparent)
                            )
                        )
                )
            }
        }

        // Top controls: close button + pulsing scan status pill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Surface(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart),
                shape = CircleShape,
                color = SurfaceWhite.copy(alpha = 0.9f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close scanner",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(
                visible = stage == ScanStage.Scanning,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                ScanningStatusPill()
            }
        }

        // Bottom stack: result cards, CTA, and the honest simulation caption.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StaggerReveal(visible = resultVisible, delayMillis = 0) {
                IdentifiedCard(
                    ringProgress = ringProgress,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            StaggerReveal(visible = resultVisible, delayMillis = 60) {
                StatsCard(modifier = Modifier.padding(bottom = 16.dp))
            }
            StaggerReveal(visible = resultVisible, delayMillis = 120) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { if (!saved) saved = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = CircleShape
                    ) {
                        Crossfade(
                            targetState = saved,
                            animationSpec = tween(durationMillis = 200),
                            label = "saveState"
                        ) { isSaved ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSaved) {
                                        Icons.Default.Check
                                    } else {
                                        Icons.Default.BookmarkAdd
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSaved) "In your Vault" else "Save to Vault",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !saved,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        TextButton(
                            onClick = { if (!saved) stage = ScanStage.Scanning },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Scan Again",
                                style = MaterialTheme.typography.labelLarge,
                                color = ProfitGreenBright
                            )
                        }
                    }
                }
            }
            Text(
                text = "Camera preview arrives with the next update — this is a simulated scan.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = SlateBorder.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// Result cards slide up + fade in, staggered by delayMillis.
@Composable
private fun StaggerReveal(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(250, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(250, delayMillis = delayMillis),
                initialOffsetY = { it / 3 }
            ),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        content()
    }
}

@Composable
private fun ScanningStatusPill(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "scanPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = SurfaceWhite.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Scanning environment…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceWhite.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, SurfaceWhite.copy(alpha = 0.4f))
    ) {
        content()
    }
}

@Composable
private fun IdentifiedCard(ringProgress: Float, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConfidenceRing(progress = ringProgress)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "IDENTIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Mid-Century Chair",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ConfidenceRing(progress: Float, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun StatsCard(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = "Est. Resale") {
                Text(
                    text = "$450",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            StatItem(label = "Demand") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "High",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            StatItem(label = "Condition") {
                Text(
                    text = "Excellent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatItem(label: String, value: @Composable () -> Unit) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        value()
    }
}

@Preview(showBackground = true)
@Composable
fun ScannerScreenPreview() {
    FreeboxTheme {
        ScannerScreen(onClose = {})
    }
}

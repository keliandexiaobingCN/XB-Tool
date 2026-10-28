package com.xb.tool.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import com.xb.tool.core.AppState
import com.xb.tool.core.LogEntry
import com.xb.tool.core.LogLevel
import com.xb.tool.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer

fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}


/** 毛玻璃开关：由 XBAppRoot 提供，卡片据此使用半透明表面色。 */
val LocalGlassEffect = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 毛玻璃透明度：0.15 更透明，0.95 更实。 */
val LocalGlassAlpha = androidx.compose.runtime.staticCompositionLocalOf { 0.42f }

/** UI 动画开关：由入口处提供，控制按压/展开等过渡。 */
val LocalUiAnimation = androidx.compose.runtime.staticCompositionLocalOf { true }

/** 统一卡片表面色：毛玻璃开 → 半透明；关 → 不透明。 */
@Composable
fun glassSurfaceColor(alpha: Float = LocalGlassAlpha.current): Color {
    val glass = LocalGlassEffect.current
    // 透明度设下限，太透会让卡片压到亮/暗背景上都读不清
    val safeAlpha = alpha.coerceAtLeast(0.62f)
    return if (glass) MaterialTheme.colorScheme.surface.copy(alpha = safeAlpha)
    else MaterialTheme.colorScheme.surface
}


@Composable
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.HIGH -> MaterialTheme.colorScheme.error
    Severity.MEDIUM -> MaterialTheme.colorScheme.tertiary
    Severity.LOW -> MaterialTheme.colorScheme.secondary
}

@Composable
fun severityContainer(severity: Severity): Color = when (severity) {
    Severity.HIGH -> MaterialTheme.colorScheme.errorContainer
    Severity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
    Severity.LOW -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun LevelDot(level: LogLevel) {
    val color = when (level) {
        LogLevel.INFO -> Color(0xFF7FB8B0)
        LogLevel.OK -> Color(0xFF76E0B8)
        LogLevel.WARN -> Color(0xFFFFC46B)
        LogLevel.ERROR -> Color(0xFFFF8C8C)
    }
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun StatusPill(text: String, active: Boolean, icon: ImageVector? = null) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(12.dp), tint = content)
        Text(text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun XBListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null && trailing == null
) {
    val animEnabled = LocalUiAnimation.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animEnabled && pressed) 0.975f else 1f,
        animationSpec = androidx.compose.animation.core.tween(90),
        label = "row-press"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) Modifier.clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple()
                ) { onClick() }
                else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (LocalGlassEffect.current)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showChevron) {
                Spacer(Modifier.width(4.dp))
                Text("›", color = MaterialTheme.colorScheme.outline, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun TerminalPanel(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    title: String = "实时日志",
    height: Dp = 190.dp,
    running: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF06110F),
        border = BorderStroke(1.dp, Color(0xFF1E3A35)),
        contentColor = Color(0xFFB9F2E7)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val alpha = if (running) {
                    val transition = rememberInfiniteTransition(label = "terminal-dot")
                    val value by transition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "alpha"
                    )
                    value
                } else 1f
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF57E6B4).copy(alpha = alpha))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFDFFFF6),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (onClear != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "清空日志",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF7FB8B0)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(height - 46.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "等待事件 · 扫描与拦截日志会显示在这里",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF6F938D)
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(logs.size) {
                    if (logs.isNotEmpty()) {
                        listState.scrollToItem(logs.lastIndex)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.height(height - 46.dp)
                ) {
                    items(logs, key = { it.id }) { entry ->
                        val color = when (entry.level) {
                            LogLevel.INFO -> Color(0xFFA8CFC8)
                            LogLevel.OK -> Color(0xFF7DF0C4)
                            LogLevel.WARN -> Color(0xFFFFCE85)
                            LogLevel.ERROR -> Color(0xFFFF9A9A)
                        }
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Text(
                                entry.time,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF5A7D77)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (entry.level) {
                                    LogLevel.INFO -> "I"
                                    LogLevel.OK -> "✓"
                                    LogLevel.WARN -> "!"
                                    LogLevel.ERROR -> "×"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = color
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                entry.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = color,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    deep: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = if (deep) "需 Root" else null,
    badgeActive: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            Modifier.background(
                // 两张卡片必须用同一套底色，只靠图标颜色和「需 Root」标签区分。
                // 之前强力杀毒用了整片 primary 渐变，换背景后和普通杀毒明显不是一块料。
                Brush.linearGradient(
                    listOf(
                        scheme.surface.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                )
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (deep) scheme.primary.copy(alpha = 0.20f) else scheme.onSurface.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (deep) scheme.primary else scheme.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (badge != null) StatusPill(badge, active = badgeActive)
                }
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ScanningOrb(running: Boolean, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.Shield) {
    val progress = if (running) {
        val transition = rememberInfiniteTransition(label = "scan-orb")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "angle"
        )
        angle
    } else 0f
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = scheme.primary.copy(alpha = 0.10f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = scheme.primary.copy(alpha = 0.30f),
                radius = radius * 0.78f,
                center = center,
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = scheme.outlineVariant.copy(alpha = 0.35f),
                radius = radius * 0.62f,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawArc(
                color = scheme.primary,
                startAngle = progress,
                sweepAngle = if (running) 70f else 0f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f),
                size = androidx.compose.ui.geometry.Size(radius * 1.8f, radius * 1.8f),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
            if (running) {
                drawArc(
                    color = scheme.tertiary.copy(alpha = 0.55f),
                    startAngle = progress + 160f,
                    sweepAngle = 28f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.56f, radius * 1.56f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(scheme.primaryContainer, scheme.surface)))
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = scheme.primary)
        }
    }
}

@Composable
fun PermissionReminderDialog(
    permissionName: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var offValue by remember { mutableFloatStateOf(0f) }
    var onValue by remember { mutableFloatStateOf(0f) }
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(scheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = scheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("权限提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            permissionName,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "请选择：关闭授权保持现状，或拖动滑块开启授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("关闭", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (offValue >= 0.98f) "已选择关闭" else "向右滑动关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = offValue,
                    onValueChange = { value ->
                        offValue = value
                        if (value >= 0.98f) onDismiss()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.onSurfaceVariant,
                        activeTrackColor = scheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = scheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("开启", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (onValue >= 0.98f) "正在授权…" else "向右滑动开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary
                    )
                }
                Slider(
                    value = onValue,
                    onValueChange = { value ->
                        onValue = value
                        if (value >= 0.98f) onConfirm()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "授权仅用于深度扫描、应用冻结与系统目录读取，不会上传你的数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}

@Composable
fun XBConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

@Composable
fun XBInfoDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

@Composable
fun BusyOverlay(text: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = glassSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(14.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MonoBlock(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp = 44.dp, fallbackLabel: String = "?") {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallbackLabel.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun applySystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return
    LaunchedEffect(dark, view) {
        val window = activity.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
fun BackgroundImage(path: String?, alpha: Float) {
    if (path.isNullOrBlank()) return
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
                } else null
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = alpha
        )
    }
}

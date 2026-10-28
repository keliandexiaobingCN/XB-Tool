package com.xb.tool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xb.tool.R
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.util.Intents
import com.xb.tool.ui.screens.AboutScreen
import com.xb.tool.ui.screens.AdBlockScreen
import com.xb.tool.ui.screens.AdbPairScreen
import com.xb.tool.ui.screens.PrivilegeAppsScreen
import com.xb.tool.ui.screens.PrivilegeServiceScreen
import com.xb.tool.ui.screens.AnnouncementsScreen
import com.xb.tool.ui.screens.AppFreezeScreen
import com.xb.tool.ui.screens.AudioConverterScreen
import com.xb.tool.ui.screens.AuthorScreen
import com.xb.tool.ui.screens.BrowserScreen
import com.xb.tool.ui.screens.SystemMonitorScreen
import com.xb.tool.ui.screens.CacheScreen
import com.xb.tool.ui.screens.CloudDownloadScreen
import com.xb.tool.ui.screens.EmergencyScreen
import com.xb.tool.ui.screens.ExtremeKeepAliveScreen
import com.xb.tool.ui.screens.HomeScreen
import com.xb.tool.ui.screens.InterceptScreen
import com.xb.tool.ui.screens.KernelScreen
import com.xb.tool.ui.screens.LicenseScreen
import com.xb.tool.ui.screens.MemoryCleanScreen
import com.xb.tool.ui.screens.NetworkScreen
import com.xb.tool.ui.screens.NotificationSettingsScreen
import com.xb.tool.ui.screens.RealtimeProtectScreen
import com.xb.tool.ui.screens.PdfConverterScreen
import com.xb.tool.ui.screens.QrCodeScreen
import com.xb.tool.ui.screens.QuarantineScreen
import com.xb.tool.ui.screens.RamCleanScreen
import com.xb.tool.ui.screens.RootScreen
import com.xb.tool.ui.screens.ScanScreen
import com.xb.tool.ui.screens.SearchScreen
import com.xb.tool.ui.screens.SettingsScreen
import com.xb.tool.ui.screens.ThemeSettingsScreen
import com.xb.tool.ui.screens.ToolsScreen
import com.xb.tool.ui.screens.UpdateScreen
import com.xb.tool.ui.screens.WhitelistScreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XBAppRoot(state: AppState) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.theme.darkMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    applySystemBarAppearance(dark)

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            state.consumeMessage()
        }
    }

    BackHandler(enabled = !state.screen.isTabRoot) {
        state.goBack()
    }

    if (!state.agreementAccepted) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text("用户协议与法律条款") },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.height(340.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "欢迎使用 XB工具。请在使用前阅读并同意以下条款：\n\n" +
                            "1. 本应用为本地安全检测与系统工具，按“现状”提供，不承诺查杀率或绝对安全。\n\n" +
                            "2. 你应确保对设备拥有合法控制权；在他人设备上使用须获得明确授权。\n\n" +
                            "3. Root / Shizuku / 应用冻结 / 文件删除等操作具有风险，可能导致数据丢失或系统异常，请自行备份。\n\n" +
                            "4. 病毒库与公开哈希来自第三方，可能存在误报或漏报。\n\n" +
                            "5. 严禁将本应用用于任何违反法律法规的用途，包括未经授权访问他人设备、制作传播恶意软件、侵犯隐私等。\n\n" +
                            "6. 因滥用本应用造成的一切后果由使用者承担，作者不承担直接或间接责任。\n\n" +
                            "7. 你可以在「关于 → 用户协议与法律条款」中随时查看完整内容。\n\n" +
                            "点击「同意并继续」表示你已阅读、理解并同意上述条款；点击「不同意」将退出应用。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { state.acceptAgreement() }) {
                    Text("同意并继续")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    (state.context as? android.app.Activity)?.finishAffinity()
                }) { Text("不同意") }
            }
        )
    }

    if (state.showVirusDbPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { state.dismissVirusDbPrompt() },
            title = { Text("提示：请更新病毒库") },
            text = {
                Text(
                    "当前使用的是一些内置基础特征，病毒库可能不是最新。\n\n" +
                        "由于没有稳定的公共更新服务，建议你自行配置更新源，或使用内置公开哈希库。\n" +
                        "可在「更新检测」页手动更新，也可以在设置中开启自动检查（默认 60 分钟一次）。"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.dismissVirusDbPrompt()
                    state.navigate(Screen.Update)
                }) { Text("去更新") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { state.dismissVirusDbPrompt() }) { Text("稍后") }
            }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DynamicBackground(state, dark)
        // 纯色背景优先
        val solid = state.theme.backgroundColor
        if (solid != null && state.theme.backgroundPath == null && state.theme.backgroundVideoPath == null) {
            val color = runCatching { Color(android.graphics.Color.parseColor(solid)) }.getOrNull()
            if (color != null) {
                Box(Modifier.fillMaxSize().background(color))
            }
        }
        // 无背景图时铺一层「实底 + 极淡光晕」：
        // 先把背景底色铺满，再叠很淡的径向光，避免亮斑把半透明卡片和文字冲掉
        if (solid == null && state.theme.backgroundPath == null && state.theme.backgroundVideoPath == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.10f else 0.05f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(180f, 260f),
                            radius = 900f
                        )
                    )
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = state.screen.isTabRoot,
            scrimColor = Color.Black.copy(alpha = 0.62f),
            drawerContent = {
                XBDrawer(
                    state = state,
                    onNavigate = { target ->
                        scope.launch { drawerState.close() }
                        state.navigate(target)
                    }
                )
            }
        ) {
            // 抽屉打开时把页面整体模糊，实现“透明抽屉 + 磨砂”且不与页面文字重叠
            
            val openProgress = if (drawerState.isOpen) 1f else 0f
            val blurAmount by androidx.compose.animation.core.animateFloatAsState(
                targetValue = openProgress * 12f,
                animationSpec = androidx.compose.animation.core.tween(280),
                label = "drawer-blur"
            )
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.then(
                    if (blurAmount > 0.5f && android.os.Build.VERSION.SDK_INT >= 31)
                        Modifier.blur(blurAmount.dp)
                    else Modifier
                ),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    if (state.screen.isTabRoot) "XB工具" else state.screen.title,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                )
                                if (state.screen.isTabRoot) {
                                    Text(
                                        when (state.screen) {
                                            Screen.Tools -> "转换 · 清理 · Root 工具"
                                            Screen.Settings -> "防护与个性化"
                                            else -> "本机安全与系统工具"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (state.screen.isTabRoot) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "菜单")
                                }
                            } else {
                                IconButton(onClick = { state.goBack() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            }
                        },
                        actions = {
                            if (state.screen.isTabRoot) {
                                IconButton(onClick = { state.navigate(Screen.Search) }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            // 顶栏给一层跟随主题的半透明底衬，防止文字压在亮背景上看不清
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    if (state.screen.isTabRoot) {
                        XBBottomBar(
                            current = state.screen,
                            onSelect = { target -> state.switchTab(target) }
                        )
                    }
                }
            ) { padding ->
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        if (!state.theme.uiAnimation) {
                            androidx.compose.animation.EnterTransition.None togetherWith
                                androidx.compose.animation.ExitTransition.None
                        } else {
                            val forward = targetState !is Screen.Home
                            when (state.theme.navStyle) {
                                "fade" -> androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(320)
                                ) togetherWith androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(240)
                                )
                                "zoom" -> androidx.compose.animation.scaleIn(
                                    initialScale = 0.90f,
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ) + androidx.compose.animation.fadeIn() togetherWith
                                    androidx.compose.animation.scaleOut(
                                        targetScale = 1.06f,
                                        animationSpec = androidx.compose.animation.core.tween(240)
                                    ) + androidx.compose.animation.fadeOut()
                                else -> {
                                    val dir = if (forward) 1 else -1
                                    (androidx.compose.animation.slideInHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(340)
                                    ) { dir * it / 3 } + androidx.compose.animation.fadeIn(
                                        animationSpec = androidx.compose.animation.core.tween(260)
                                    )) togetherWith androidx.compose.animation.slideOutHorizontally(
                                        animationSpec = androidx.compose.animation.core.tween(300)
                                    ) { -dir * it / 4 } + androidx.compose.animation.fadeOut(
                                        animationSpec = androidx.compose.animation.core.tween(220)
                                    )
                                }
                            }
                        }
                    },
                    label = "xb-screen"
                ) { screen ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (screen) {
                            Screen.Home -> HomeScreen(state)
                            Screen.Tools -> ToolsScreen(state)
                            Screen.Settings -> SettingsScreen(state)
                            Screen.Root -> RootScreen(state)
                            Screen.Kernel -> KernelScreen(state)
                            Screen.Update -> UpdateScreen(state)
                            Screen.Announcements -> AnnouncementsScreen(state)
                            Screen.Whitelist -> WhitelistScreen(state)
                            Screen.About -> AboutScreen(state)
                            Screen.License -> LicenseScreen(state)
                            Screen.Author -> AuthorScreen(state)
                            Screen.Search -> SearchScreen(state)
                            Screen.Intercepts -> InterceptScreen(state)
                            Screen.Quarantine -> QuarantineScreen(state)
                            Screen.Audio -> AudioConverterScreen(state)
                            Screen.Cloud -> CloudDownloadScreen(state)
                            Screen.Pdf -> PdfConverterScreen(state)
                            Screen.Memory -> MemoryCleanScreen(state)
                            Screen.Ram -> RamCleanScreen(state)
                            Screen.Freeze -> AppFreezeScreen(state)
                            Screen.Theme -> ThemeSettingsScreen(state)
                            Screen.Cache -> CacheScreen(state)
                            Screen.Notification -> NotificationSettingsScreen(state)
                            Screen.Extreme -> ExtremeKeepAliveScreen(state)
                            Screen.Emergency -> EmergencyScreen(state)
                            Screen.AdBlock -> AdBlockScreen(state)
                            Screen.QrCode -> QrCodeScreen(state)
                            Screen.Browser -> BrowserScreen(state)
                            Screen.SystemMonitor -> SystemMonitorScreen(state)
                            Screen.PrivilegeService -> PrivilegeServiceScreen(state)
                            Screen.PrivilegeApps -> PrivilegeAppsScreen(state)
                            Screen.Network -> NetworkScreen(state)
                            Screen.Realtime -> RealtimeProtectScreen(state)
                            Screen.AdbPair -> AdbPairScreen(state)
                            is Screen.Scan -> ScanScreen(state, screen.mode)
                        }
                    }
                }
            }
        }
    }

    state.busy?.let { BusyOverlay(it) }
}

@Composable
private fun XBBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    data class Tab(val label: String, val icon: ImageVector, val screen: Screen)
    val tabs = remember {
        listOf(
            Tab("首页", Icons.Default.Home, Screen.Home),
            Tab("其他功能", Icons.Default.Apps, Screen.Tools),
            Tab("设置", Icons.Default.Settings, Screen.Settings)
        )
    }
    val selectedIndex = tabs.indexOfFirst { it.screen == current }.coerceAtLeast(0)
    val glass = LocalGlassEffect.current
    val anim = LocalUiAnimation.current

    // 滑动指示器：选中位置 + 当前按压的 tab
    val indicatorX = androidx.compose.animation.core.animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (anim) androidx.compose.animation.core.spring(
            dampingRatio = 0.72f,
            stiffness = 520f
        ) else androidx.compose.animation.core.tween(0),
        label = "tab-indicator"
    )
    val indicatorWidth = androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        label = "tab-width"
    )

    Surface(
        color = if (glass) MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassAlpha.current)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (glass) 0.28f else 0.45f)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(62.dp)) {
            val tabWidth = maxWidth / tabs.size

            // 滑动高亮块（毛玻璃胶囊）
            Box(
                Modifier
                    .offset(x = tabWidth * indicatorX.value + tabWidth * 0.18f)
                    .align(Alignment.CenterStart)
                    .width(tabWidth * 0.64f * indicatorWidth.value)
                    .height(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = if (glass) 0.72f else 1f
                        )
                    )
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(tab.screen) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val iconScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (selected && anim) 1.12f else 1f,
                            animationSpec = if (anim) androidx.compose.animation.core.spring(
                                dampingRatio = 0.55f,
                                stiffness = 640f
                            ) else androidx.compose.animation.core.tween(0),
                            label = "tab-icon"
                        )
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XBDrawer(state: AppState, onNavigate: (Screen) -> Unit) {
    val glass = LocalGlassEffect.current
    val alpha = LocalGlassAlpha.current
    // 纯色背景也应用到侧边栏；未设置时用主题 surface
    val solid = state.theme.backgroundColor
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    ModalDrawerSheet(
        // 有纯色背景时抽屉用该色，否则用半透明毛玻璃
        drawerContainerColor = solid ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
        modifier = Modifier
            .width(310.dp)
            .graphicsLayer {
                // 抽屉滑入时的轻微缩放，配合系统滑动动画
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    if (solid != null) {
                        // 纯色背景：只叠一层很淡的深色遮罩，保证文字可读
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.04f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                            )
                        )
                    }
                )
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            )
                        )
                    )
                    .padding(start = 22.dp, end = 22.dp, top = 42.dp, bottom = 22.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_preview),
                                contentDescription = "XB工具图标",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "XB工具",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "v1.3.7 · 本地安全与系统工具",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            state.describePrivilege(),
                            active = state.rootInfo.rootGranted || state.shizukuGranted
                        )
                        StatusPill(
                            if (state.realtime) "实时防护开" else "实时防护关",
                            active = state.realtime
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "更多",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 6.dp)
            )
            DrawerItem(Icons.Default.Campaign, "公告", if (state.hasUnreadAnnouncements()) "有新公告 · 点击查看" else "版本动态与作者说明") {
                onNavigate(Screen.Announcements)
            }
            DrawerItem(Icons.Default.GppMaybe, "拦截记录", "查看实时检测拦截") {
                onNavigate(Screen.Intercepts)
            }
            DrawerItem(Icons.Default.ListAlt, "白名单", "已添加 ${state.whitelist.size} 个应用") {
                onNavigate(Screen.Whitelist)
            }
            DrawerItem(Icons.Default.SystemUpdate, "更新检测", "当前版本 v1.3.7") {
                onNavigate(Screen.Update)
            }
            DrawerItem(Icons.Default.Info, "关于", "版本信息与开源许可") {
                onNavigate(Screen.About)
            }
            Surface(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "病毒库 v${state.database.version}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "本地特征 ${state.database.signatureCount} 条 · 在线更新已接入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // 抽屉条目也走毛玻璃，避免整片死板的不透明块
        color = if (LocalGlassEffect.current)
            MaterialTheme.colorScheme.surface.copy(alpha = (LocalGlassAlpha.current + 0.18f).coerceAtMost(0.92f))
        else MaterialTheme.colorScheme.surface,
        // 半透明色会让 Surface 自动推导出黑色内容色，这里显式指定
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

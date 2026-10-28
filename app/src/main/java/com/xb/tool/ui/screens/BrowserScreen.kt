package com.xb.tool.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xb.tool.browser.Bookmark
import com.xb.tool.browser.BrowserPrefs
import com.xb.tool.core.AppState
import com.xb.tool.ui.StatusPill
import com.xb.tool.util.Intents

object BrowserGuard {
    fun isRisky(url: String): String? {
        val lower = url.lowercase()
        if (lower.startsWith("http://")) return "该网页使用不加密的 HTTP 连接，内容可能被篡改。"
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return null
        if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return "该网页使用 IP 地址直接访问，请确认来源可信。"
        if (host.contains("xn--")) return "该网页使用国际化域名编码，可能伪装成相似域名。"
        if (host.count { it == '.' } >= 4) return "该网页域名层级异常，可能存在钓鱼风险。"
        val riskyWords = listOf("login", "verify", "account", "update", "secure", "支付", "登录")
        if (riskyWords.any { host.contains(it) } && lower.contains("http://")) return "登录/支付类页面建议使用 HTTPS。"
        return null
    }
}

private const val MOBILE_UA_FALLBACK =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

/**
 * 用 WebView 自己的 UA（去掉 wv 标记）。
 * 之前写死 Chrome/132，但模拟器/旧机上的 WebView 内核可能是 Chromium 9x，
 * 站点按 UA 里的版本号下发特性，结果就是「页面能开但内容渲染不出来」
 * （Bing 首页只剩一张背景图就是这么来的）。用自己的 UA 版本永远对得上。
 */
private fun mobileUserAgent(webView: android.webkit.WebView): String {
    val default = runCatching { android.webkit.WebSettings.getDefaultUserAgent(webView.context) }
        .getOrDefault(MOBILE_UA_FALLBACK)
    // 去掉 "; wv" 与 "; wv)" 标记：部分站点看到 wv 会降级到极简版
    return default.replace("; wv", "").replace(" Version/4.0", "")
}

/**
 * 把 WebView 的错误码翻译成用户能看懂的原因。
 * WebView 默认在加载失败时只留一片空白，用户完全不知道是断网、被墙还是域名打错。
 */
private fun describeWebError(code: Int, url: String): String {
    val why = when (code) {
        android.webkit.WebViewClient.ERROR_HOST_LOOKUP ->
            "域名解析失败：可能没有网络，或这个域名在当前网络下无法解析。"
        android.webkit.WebViewClient.ERROR_CONNECT ->
            "连接被拒绝：站点没开、端口不对，或者被网络环境拦截。"
        android.webkit.WebViewClient.ERROR_TIMEOUT ->
            "连接超时：站点响应太慢，或当前网络无法访问它。"
        android.webkit.WebViewClient.ERROR_IO ->
            "连接中断：数据没传完就断了，可以重试一次。"
        android.webkit.WebViewClient.ERROR_UNSUPPORTED_SCHEME ->
            "这个链接用的协议浏览器打不开。"
        android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
        android.webkit.WebViewClient.ERROR_BAD_URL ->
            "安全连接失败：证书或地址有问题，为安全起见不继续加载。"
        android.webkit.WebViewClient.ERROR_UNKNOWN -> "未知错误，可以重试一次。"
        else -> "加载失败（错误码 $code）。"
    }
    return why + "\n\n地址：" + url.ifBlank { "（空）" }
}

@Composable
fun BrowserScreen(state: AppState) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf(BrowserPrefs.engine(context)) }
    var incognito by remember { mutableStateOf(BrowserPrefs.incognito(context)) }
    var darkMode by remember { mutableStateOf(BrowserPrefs.darkMode(context)) }
    var adblock by remember { mutableStateOf(BrowserPrefs.adblock(context)) }
    var desktop by remember { mutableStateOf(BrowserPrefs.desktop(context)) }
    var warningEnabled by remember { mutableStateOf(BrowserPrefs.prefs(context).getBoolean("warning", true)) }
    // 用 TextFieldValue 而不是 String：需要在点进地址栏时自动全选，
    // 否则新输入的网址会接在旧网址后面（表现为 https://cn.bing.com/example.com）。
    var urlValue by remember { mutableStateOf(TextFieldValue("")) }
    val urlText = urlValue.text
    // 用户正在地址栏里打字时，页面跳转的 onPageStarted / onPageFinished 不能把
    // 输入框里的内容覆盖掉（之前就是这个原因，输入到一半就被 URL 冲掉）。
    var editing by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("新标签页") }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var warningUrl by remember { mutableStateOf<String?>(null) }
    var warningText by remember { mutableStateOf("") }
    var bookmarks by remember { mutableStateOf(BrowserPrefs.bookmarks(context)) }
    // Edge 风格起始页：没有打开任何网页时显示，内嵌在 WebView 上面
    var showHome by remember { mutableStateOf(true) }
    // 加载失败时给一张可读的错误页，而不是空白/只剩背景色
    var pageError by remember { mutableStateOf<String?>(null) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // 移动端排版：只开 wideViewport，让站点按视口宽度回流。
            // overview 会把整页缩放成「一屏看全」，和 wideViewport 叠加后正文反而被放大，
            // 表现为「页面比屏幕宽、要左右拖」，所以这里必须关掉。
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            // 不用 TEXT_AUTOSIZING：它会把正文按「可读性」放大，在宽屏上反而溢出屏幕
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 100
            settings.defaultTextEncodingName = "UTF-8"
            settings.blockNetworkImage = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            isFocusableInTouchMode = true
            // 保证 WebView 自身能接收触摸滚动
            setOnTouchListener { _, _ -> false }
            // 手机端 UA，去掉 wv 标记，部分站点会因此返回电脑版
            settings.userAgentString = mobileUserAgent(this)
            setBackgroundColor(android.graphics.Color.rgb(15, 21, 19))
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    fun loadHome() {
        webView.loadUrl(BrowserPrefs.engineHome(engine))
    }

    fun goHome() {
        showHome = true
        currentUrl = ""
        pageTitle = ""
        urlValue = TextFieldValue("")
        canBack = false
        canForward = false
    }

    fun openText(raw0: String) {
        val raw = raw0.trim()
        if (raw.isBlank()) return
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw
        else if (raw.contains('.') && !raw.contains(' ')) "https://$raw"
        else BrowserPrefs.searchUrl(engine, raw)
        editing = false
        showHome = false
        webView.loadUrl(url)
    }

    fun loadInput() = openText(urlText)

    fun applyPlugins() {
        // 有些页面（尤其是老站点和部分搜索结果页）不写 viewport meta，
        // WebView 就按默认的 980px 排版，看上去像「平板版」——字小、要左右拖。
        // 这里在页面里补一条 viewport，只有在页面自己没有时才生效。
        val viewportJs =
            "(function(){try{" +
                "var h=document.head||document.getElementsByTagName('head')[0];" +
                "if(!h)return;" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';h.appendChild(m);}" +
                "if(!/width\\s*=\\s*device-width/i.test(m.content||'')){" +
                "m.setAttribute('content','width=device-width, initial-scale=1.0');}" +
                "}catch(e){}})();"
        webView.evaluateJavascript(viewportJs, null)

        val js = buildString {
            if (adblock) {
                // 注意：绝不能用 [id*=ad] / [class*=ad-] 这种子串选择器。
                // "ad" 是 head、download、breadcrumb、loading、shadow、read 的子串，
                // 会把整页布局容器一起隐藏掉——Bing 首页「只剩一张背景图」就是这么来的。
                // 这里只保留业界通行的精确广告容器选择器。
                append(
                    "(function(){var s=document.createElement('style');s.textContent=" +
                        "'ins.adsbygoogle," +
                        "iframe[src*=\"doubleclick.net\"]," +
                        "iframe[src*=\"googlesyndication.com\"]," +
                        "iframe[id^=\"google_ads_iframe\"]," +
                        "div[id^=\"div-gpt-ad\"]," +
                        "div[data-ad-slot]," +
                        "div[data-ad-client]," +
                        ".adsbygoogle," +
                        ".advertisement,.advertisements,.ad-container,.ad-wrapper," +
                        "div[class=\"ads\"],div[id=\"ads\"]" +
                        "{display:none!important}'" +
                        ";document.documentElement.appendChild(s);})();"
                )
            }
            if (darkMode == "dark") {
                append(
                    "(function(){var s=document.createElement('style');s.textContent=" +
                        "'html{filter:invert(0.92) hue-rotate(180deg);background:#111!important}'" +
                        "img,video{filter:invert(1) hue-rotate(180deg)}';" +
                        "document.documentElement.appendChild(s);})();"
                )
            }
        }
        if (js.isNotBlank()) webView.evaluateJavascript(js, null)
    }

    DisposableEffect(Unit) {
        webView.settings.userAgentString = if (desktop) {
            DESKTOP_UA
        } else {
            mobileUserAgent(webView)
        }
        webView.settings.useWideViewPort = true
        // 桌面 UA 的页面按 ~980px 排版，手机上必须开 overview 才能整页缩进屏幕；
        // 手机 UA 则要关掉，否则正文会被整体缩小、字太小。
        webView.settings.loadWithOverviewMode = desktop
        webView.settings.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loading = true
                pageError = null
                showHome = false
                currentUrl = url.orEmpty()
                if (!editing) urlValue = TextFieldValue(url.orEmpty())
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // 只有主文档失败才算「打不开」；子资源（图片/统计脚本）失败不能顶掉整页
                if (request?.isForMainFrame != true) return
                pageError = describeWebError(error?.errorCode ?: -1, view?.url.orEmpty())
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame != true) return
                val code = errorResponse?.statusCode ?: 0
                if (code >= 400) {
                    pageError = "服务器返回 HTTP $code\n\n这个地址可能已经失效，或者站点拒绝了当前访问。"
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loading = false
                currentUrl = url.orEmpty()
                if (!editing) urlValue = TextFieldValue(url.orEmpty())
                pageTitle = view?.title?.ifBlank { url.orEmpty() } ?: url.orEmpty()
                canBack = view?.canGoBack() == true
                canForward = view?.canGoForward() == true
                applyPlugins()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                if (target.startsWith("intent:") || target.startsWith("market:") ||
                    target.startsWith("magnet:") || target.startsWith("tel:")
                ) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return true
                }
                if (warningEnabled) {
                    BrowserGuard.isRisky(target)?.let {
                        warningUrl = target
                        warningText = it
                        return true
                    }
                }
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
            }
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            runCatching {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                state.showMessage("已开始下载")
            }
        }
        goHome()
        onDispose {
            if (incognito) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                webView.clearCache(true)
                webView.clearHistory()
            }
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = true) {
        when {
            webView.canGoBack() -> webView.goBack()
            !showHome -> goHome()
            else -> state.goBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!showHome) Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    // 单行紧凑工具栏：后退/前进 + 地址栏 + 打开 + 更多
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (webView.canGoBack()) webView.goBack() },
                            enabled = canBack,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "后退", modifier = Modifier.size(18.dp))
                        }
                        // 不用 OutlinedTextField：Material3 的它自带 56dp 最小高度和
                        // 上下 16dp 内容内边距，硬压到 40dp 会把文字裁掉一半（表现为
                        // 地址栏里的字母只剩上半截）。这里用 BasicTextField 自己画边框，
                        // 高度和内边距完全可控。
                        BasicTextField(
                            value = urlValue,
                            onValueChange = { urlValue = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = { loadInput() }),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .onFocusChanged { focus ->
                                    editing = focus.isFocused
                                    // 点进地址栏时全选，方便直接覆盖旧网址
                                    if (focus.isFocused && urlValue.text.isNotEmpty()) {
                                        urlValue = urlValue.copy(
                                            selection = TextRange(0, urlValue.text.length)
                                        )
                                    }
                                },
                            decorationBox = { inner ->
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (urlText.isEmpty()) {
                                        Text(
                                            "搜索或输入网址",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        IconButton(onClick = { loadInput() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "打开", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showSettings = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "浏览器设置", modifier = Modifier.size(18.dp))
                        }
                    }
                    // 细状态行：只保留必要信息，节省高度
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canForward) {
                            IconButton(onClick = { webView.goForward() }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "前进", modifier = Modifier.size(15.dp))
                            }
                        }
                        if (incognito) {
                            Text("无痕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            pageTitle.ifBlank { BrowserPrefs.engineName(engine) }.take(28),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { webView.reload() }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { showBookmarks = true }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Bookmark, contentDescription = "收藏夹", modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = {
                            BrowserPrefs.addBookmark(context, pageTitle, currentUrl)
                            bookmarks = BrowserPrefs.bookmarks(context)
                            state.showMessage("已加入收藏")
                        }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "收藏当前页", modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            webView.clearCache(true)
                            webView.clearHistory()
                            state.showMessage("已清除 Cookie、缓存与历史")
                        }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = "清除痕迹", modifier = Modifier.size(15.dp))
                        }
                    }
                    if (loading) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
                if (showHome) {
                    EdgeStartPage(
                        engine = engine,
                        bookmarks = bookmarks,
                        onSearch = { openText(it) },
                        onOpen = { openText(it) },
                        onOpenBookmarks = { showBookmarks = true },
                        onSettings = { showSettings = true },
                        onBack = { if (webView.canGoBack()) { webView.goBack(); showHome = false } },
                        onForward = { if (webView.canGoForward()) { webView.goForward(); showHome = false } }
                    )
                }
                pageError?.let { message ->
                    // 加载失败时盖一层可读的错误页：说明原因 + 重试 + 回起始页
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "页面打不开",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = {
                                    pageError = null
                                    webView.reload()
                                }) { Text("重试") }
                                Button(onClick = {
                                    pageError = null
                                    goHome()
                                }) { Text("回到起始页") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("收藏夹") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("暂无收藏。打开网页后点击书签图标添加。")
                } else {
                    LazyColumn(Modifier.height(360.dp)) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f).padding(end = 4.dp)
                                ) {
                                    Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        bookmark.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    webView.loadUrl(bookmark.url)
                                    showBookmarks = false
                                }) { Text("打开") }
                                TextButton(onClick = {
                                    BrowserPrefs.removeBookmark(context, bookmark.url)
                                    bookmarks = BrowserPrefs.bookmarks(context)
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        BrowserPrefs.clearBookmarks(context)
                        bookmarks = emptyList()
                    }) { Text("清空") }
                    TextButton(onClick = { showBookmarks = false }) { Text("关闭") }
                }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("浏览器设置") },
            text = {
                LazyColumn(Modifier.height(470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("搜索引擎", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("bing" to "Bing", "google" to "Google", "baidu" to "百度", "duckduckgo" to "DuckDuckGo").forEach { (id, label) ->
                                FilterChip(
                                    selected = engine == id,
                                    onClick = {
                                        engine = id
                                        BrowserPrefs.setEngine(context, id)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    item {
                        Text("主题", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色").forEach { (id, label) ->
                                FilterChip(
                                    selected = darkMode == id,
                                    onClick = {
                                        darkMode = id
                                        BrowserPrefs.setDarkMode(context, id)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    item {
                        SettingSwitch("无痕浏览（退出清除 Cookie/缓存/历史）", incognito) {
                            incognito = it
                            BrowserPrefs.setIncognito(context, it)
                            webView.settings.cacheMode = if (it) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                        }
                    }
                    item {
                        SettingSwitch("广告过滤插件（隐藏常见广告元素）", adblock) {
                            adblock = it
                            BrowserPrefs.setAdblock(context, it)
                            applyPlugins()
                        }
                    }
                    item {
                        SettingSwitch("桌面版网页", desktop) {
                            desktop = it
                            BrowserPrefs.setDesktop(context, it)
                            webView.settings.userAgentString = if (it) {
                                DESKTOP_UA
                            } else {
                                mobileUserAgent(webView)
                            }
                            webView.settings.useWideViewPort = true
                            webView.settings.loadWithOverviewMode = it
                            webView.reload()
                        }
                    }
                    item {
                        SettingSwitch("网页风险警告（HTTP/钓鱼特征）", warningEnabled) {
                            warningEnabled = it
                            BrowserPrefs.prefs(context).edit().putBoolean("warning", it).apply()
                        }
                    }
                    item {
                        Text(
                            "浏览器默认无痕；收藏与搜索引擎保存在本机。主题背景使用应用的动态背景设置。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } }
        )
    }

    warningUrl?.let { target ->
        AlertDialog(
            onDismissRequest = { warningUrl = null },
            title = { Text("网页风险警告") },
            text = { Text("$warningText\n\n$target") },
            confirmButton = {
                TextButton(onClick = {
                    warningUrl = null
                    webView.loadUrl(target)
                }) { Text("仍然访问") }
            },
            dismissButton = {
                TextButton(onClick = { warningUrl = null }) { Text("返回") }
            }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Edge 手机版风格的起始页：深色渐变 + 大 Logo + 底部悬浮搜索框 + 快捷入口 + 底部工具条。
 *
 * 这里刻意用 Compose 画而不是加载网页——加载一个远程首页既慢又会被 DNS 广告劫持，
 * 而且用户的搜索词根本不需要先经过一个页面。
 */
@Composable
private fun EdgeStartPage(
    engine: String,
    bookmarks: List<Bookmark>,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF1B1B20), Color(0xFF0E0E12))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            // 顶部：头像 + 名称 + 引擎 + 设置
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF2E2E38),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("XB", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD8D8E0))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF24242C)) {
                    Text(
                        BrowserPrefs.engineName(engine),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCFCFDA),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color(0xFFD8D8E0), modifier = Modifier.size(19.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // 中间：大 Logo
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "XB",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEDEDF3),
                    letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "浏览器",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9A9AA8)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "无痕 · 广告过滤 · 网页风险警告",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6E6E7C),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            // 悬浮搜索框
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF24242C),
                border = BorderStroke(
                    1.dp,
                    if (focus) Color(0xFF4C8DFF) else Color(0xFF34343E)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF9A9AA8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFEDEDF3)),
                        cursorBrush = SolidColor(Color(0xFF4C8DFF)),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .onFocusChanged { focus = it.isFocused },
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        "搜索或输入网址",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF7A7A88)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "清空", tint = Color(0xFF9A9AA8), modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = { onSearch(query) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Send, contentDescription = "搜索", tint = Color(0xFF4C8DFF), modifier = Modifier.size(17.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 快捷入口：收藏夹 + 最近收藏（没有收藏时给三个常用站点，避免只剩两个图标）
            val shortcuts: List<Pair<String, String>> = if (bookmarks.isEmpty()) {
                listOf(
                    "必应" to "https://cn.bing.com/",
                    "百度" to "https://www.baidu.com/",
                    "谷歌" to "https://www.google.com/"
                )
            } else {
                bookmarks.take(3).map { (it.title.ifBlank { "网页" }).take(4) to it.url }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickEntry("收藏夹", Icons.Default.Bookmark) { onOpenBookmarks() }
                shortcuts.forEach { (label, url) ->
                    QuickEntry(label, Icons.Default.Language) { onOpen(url) }
                }
                QuickEntry("设置", Icons.Default.Settings) { onSettings() }
            }

            Spacer(Modifier.height(22.dp))

            // 底部工具条
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "后退", tint = Color(0xFF9A9AA8), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onForward, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "前进", tint = Color(0xFF9A9AA8), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenBookmarks, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "收藏夹", tint = Color(0xFF9A9AA8), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = "菜单", tint = Color(0xFF9A9AA8), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickEntry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF24242C),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFFCFCFDA), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9A9AA8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

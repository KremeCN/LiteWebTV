package com.yukon.litewebtv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // UI 组件
    private lateinit var containerChannel: FrameLayout
    private lateinit var containerProgram: FrameLayout
    private lateinit var rvChannels: RecyclerView
    private lateinit var rvPrograms: RecyclerView
    private lateinit var tvCurrentTitle: TextView

    // 幕布相关
    private lateinit var flSplashCover: FrameLayout
    private lateinit var tvSplashStatus: TextView
    private lateinit var viewBreathingLight: View
    private lateinit var breathingAnimator: ObjectAnimator

    // 加载动画圆点
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private val loadingAnimators = mutableListOf<ObjectAnimator>()

    // 适配器
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var programAdapter: ProgramAdapter

    private var allChannels: List<ChannelItem> = ArrayList()
    private var isMenuVisible = false
    private var currentChannelIndex = 0
    private var currentProgramIndex = 0

    private var lastSwitchTime: Long = 0
    private val SWITCH_DELAY = 3000L
    private var lastBackPressTime = 0L

    // 记录换台开始时间，用于过滤旧视频的播放信号
    private var switchStartTime = 0L

    private val hideTitleRunnable = Runnable {
        tvCurrentTitle.visibility = View.GONE
    }

    private val TARGET_URL = "https://www.yangshipin.cn/tv/home"
    private val PC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 返回空响应用于拦截无用请求（每次新建 InputStream 避免复用问题） */
    private fun EMPTY_RESPONSE() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        initViews()
        initWebView()
        hideSystemUI()

        // 启动圆点动画
        startLoadingDotsAnimation()
        // 启动时显示幕布
        showSplashScreen("正在连接云端服务器...")
    }

    private fun initViews() {
        containerChannel = findViewById(R.id.container_channel)
        containerProgram = findViewById(R.id.container_program)
        rvChannels = findViewById(R.id.rv_channels)
        rvPrograms = findViewById(R.id.rv_programs)
        tvCurrentTitle = findViewById(R.id.tv_current_title)

        flSplashCover = findViewById(R.id.fl_splash_cover)
        tvSplashStatus = findViewById(R.id.tv_splash_status)
        viewBreathingLight = findViewById(R.id.view_breathing_light)

        // 初始化加载点
        dot1 = findViewById(R.id.view_loading_dot_1)
        dot2 = findViewById(R.id.view_loading_dot_2)
        dot3 = findViewById(R.id.view_loading_dot_3)

        // 背景呼吸灯动画
        breathingAnimator = ObjectAnimator.ofFloat(viewBreathingLight, "alpha", 0.4f, 1.0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        rvChannels.layoutManager = LinearLayoutManager(this)
        channelAdapter = ChannelAdapter { item ->
            Log.d("LiteWebTV", "点击频道: ${item.name}")
            hideKeyboard()
            // 【修正】直接传递 ChannelItem 对象，而非仅仅传递 index
            switchChannel(item)
        }
        rvChannels.adapter = channelAdapter

        rvPrograms.layoutManager = LinearLayoutManager(this)
        programAdapter = ProgramAdapter()
        rvPrograms.adapter = programAdapter
    }

    /**
     * 启动三点依次发亮的动画
     */
    private fun startLoadingDotsAnimation() {
        // 先停止之前的动画，防止重复
        stopLoadingDotsAnimation()

        val dots = listOf(dot1, dot2, dot3)
        dots.forEachIndexed { index, view ->
            // 从透明度 0.3 到 1.0
            val animator = ObjectAnimator.ofFloat(view, "alpha", 0.3f, 1.0f).apply {
                duration = 600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                startDelay = (index * 200).toLong() // 错峰启动，形成波浪感
            }
            animator.start()
            loadingAnimators.add(animator)
        }
    }

    /**
     * 停止并销毁加载动画
     */
    private fun stopLoadingDotsAnimation() {
        loadingAnimators.forEach { it.cancel() }
        loadingAnimators.clear()
        // 重置状态
        dot1.alpha = 0.3f
        dot2.alpha = 0.3f
        dot3.alpha = 0.3f
    }

    // =========================================================================
    // 智能幕布逻辑
    // =========================================================================

    private fun showSplashScreen(statusText: String) {
        // 【关键】先取消正在进行的升起动画，清除 listener 防止 onAnimationEnd 设 GONE
        flSplashCover.animate().cancel()
        flSplashCover.animate().setListener(null)

        flSplashCover.translationY = 0f
        flSplashCover.visibility = View.VISIBLE
        tvSplashStatus.text = statusText

        // 启动背景呼吸灯
        if (!breathingAnimator.isStarted) {
            breathingAnimator.start()
        }
        // 启动圆点动画
        startLoadingDotsAnimation()

        flSplashCover.removeCallbacks(curtainRiseRunnable)
        // 10秒兜底策略
        flSplashCover.postDelayed(curtainRiseRunnable, 10000)
    }

    fun onVideoPlaySuccess() {
        if (flSplashCover.visibility != View.VISIBLE) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - switchStartTime < 1500) {
            return
        }

        Log.d("LiteWebTV_Splash", "检测到新视频播放，执行幕布上升动画！")
        flSplashCover.post(curtainRiseRunnable)
    }

    private val curtainRiseRunnable = Runnable {
        animateCurtainRise()
    }

    private fun animateCurtainRise() {
        if (flSplashCover.visibility != View.VISIBLE) return

        // 停止背景动画
        breathingAnimator.cancel()
        // 停止圆点加载动画，节省资源
        stopLoadingDotsAnimation()

        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        flSplashCover.animate()
            .translationY(-screenHeight)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    // 仅当动画正常完成（非被 cancel）时才隐藏
                    if (flSplashCover.translationY != 0f) {
                        flSplashCover.visibility = View.GONE
                    }
                }
            })
            .start()
    }

    fun showTitleTip(title: String) {
        if (title.isBlank()) return
        tvCurrentTitle.text = title
        tvCurrentTitle.visibility = View.VISIBLE
        tvCurrentTitle.removeCallbacks(hideTitleRunnable)
        tvCurrentTitle.postDelayed(hideTitleRunnable, 5000)
    }

    // =========================================================================
    // 按键与换台
    // =========================================================================
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode

            if (isMenuVisible) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        closeSidebars()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (containerChannel.visibility == View.VISIBLE) {
                            closeSidebars()
                            return true
                        }
                        if (containerProgram.visibility == View.VISIBLE) {
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (containerProgram.visibility == View.VISIBLE) {
                            closeSidebars()
                            return true
                        }
                        if (containerChannel.visibility == View.VISIBLE) {
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }

            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime > 2000) {
                        showToast("再按一次退出 LiteWebTV")
                        lastBackPressTime = currentTime
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSwitchTime < SWITCH_DELAY) {
                        showToast("高频次换台会导致播放卡顿\n等待3s方可继续换台~")
                        return true
                    }
                    lastSwitchTime = currentTime
                    quickSwitchChannel(false)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSwitchTime < SWITCH_DELAY) {
                        showToast("高频次换台会导致播放卡顿\n等待3s方可继续换台~")
                        return true
                    }
                    lastSwitchTime = currentTime
                    quickSwitchChannel(true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    showChannelSidebar()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    showProgramSidebar()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    Log.d("LiteWebTV_Key", "执行操作: 暂停/播放")
                    webView.evaluateJavascript("""
                        document.querySelector('.container')?.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
                        document.querySelector('.tv-player-video video')?.click();
                    """.trimIndent(), null)
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    injectScript()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showChannelSidebar() {
        if (containerChannel.visibility == View.VISIBLE) return
        hideKeyboard()
        containerChannel.visibility = View.VISIBLE
        containerProgram.visibility = View.GONE
        isMenuVisible = true

        rvChannels.post {
            val layoutManager = rvChannels.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(currentChannelIndex, 0)
            rvChannels.postDelayed({
                val viewToFocus = layoutManager.findViewByPosition(currentChannelIndex)
                if (viewToFocus != null) {
                    viewToFocus.requestFocus()
                } else {
                    rvChannels.requestFocus()
                }
            }, 100)
        }
    }

    private fun showProgramSidebar() {
        if (containerProgram.visibility == View.VISIBLE) return
        hideKeyboard()
        containerProgram.visibility = View.VISIBLE
        containerChannel.visibility = View.GONE
        isMenuVisible = true

        rvPrograms.post {
            val layoutManager = rvPrograms.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(currentProgramIndex, 0)
            rvPrograms.postDelayed({
                val viewToFocus = layoutManager.findViewByPosition(currentProgramIndex)
                if (viewToFocus != null) {
                    viewToFocus.requestFocus()
                } else {
                    rvPrograms.requestFocus()
                }
            }, 100)
        }
    }

    private fun closeSidebars() {
        containerChannel.visibility = View.GONE
        containerProgram.visibility = View.GONE
        isMenuVisible = false
        currentFocus?.clearFocus()
        webView.requestFocus()
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.let {
                imm.hideSoftInputFromWindow(it.windowToken, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 【修正】方向键换台逻辑
     * 核心：完全基于 Android 端清洗后的列表进行索引计算，然后再根据 item.index 去点击 DOM
     */
    private fun quickSwitchChannel(isNext: Boolean) {
        if (allChannels.isEmpty()) return

        // 1. 在 Android 端（清洗后的列表）计算目标索引
        var targetListIndex = if (isNext) currentChannelIndex + 1 else currentChannelIndex - 1

        // 处理循环边界
        if (targetListIndex >= allChannels.size) targetListIndex = 0
        if (targetListIndex < 0) targetListIndex = allChannels.size - 1

        // 2. 获取目标数据模型
        val targetItem = allChannels[targetListIndex]

        // 更新当前索引记录
        currentChannelIndex = targetListIndex

        // 3. 准备显示和点击的数据
        val targetName = targetItem.name
        val targetDomIndex = targetItem.index // 关键：这是 DOM 索引

        switchStartTime = System.currentTimeMillis()
        showSplashScreen("即将进入：$targetName")

        // 4. 精确打击：直接告诉 JS 点击那个特定的 DOM 索引
        val js = """
            (function() {
                const items = document.querySelectorAll('.tv-main-con-r-list-left .oveerflow-1');
                if(items[$targetDomIndex]) {
                    items[$targetDomIndex].click();
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)

        webView.postDelayed({
            webView.evaluateJavascript("window.extractData()", null)
        }, 1500)
    }

    /**
     * 【修正】侧边栏换台逻辑
     * 核心：直接接收 ChannelItem，使用其内部的 index (DOM索引) 进行点击
     */
    private fun switchChannel(item: ChannelItem) {
        val targetName = item.name

        // 更新当前选中状态的索引 (需要在 allChannels 里找到这个 item 的位置，保持上下键逻辑连贯)
        val listIndex = allChannels.indexOf(item)
        if (listIndex != -1) {
            currentChannelIndex = listIndex
        }

        switchStartTime = System.currentTimeMillis()
        showSplashScreen("即将进入：$targetName")

        val domIndex = item.index
        val js = """
            (function() {
                const items = document.querySelectorAll('.tv-main-con-r-list-left .oveerflow-1');
                if(items[$domIndex]) {
                    items[$domIndex].click();
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
        closeSidebars()

        webView.postDelayed({
            webView.evaluateJavascript("window.extractData()", null)
        }, 1500)
    }

    fun updateChannelList(list: List<ChannelItem>) {
        channelAdapter.updateData(list)
        allChannels = list
        val activeIndex = list.indexOfFirst { it.isActive }
        if (activeIndex != -1) {
            currentChannelIndex = activeIndex
        }
    }

    fun updateProgramList(list: List<ProgramItem>) {
        programAdapter.updateData(list)
        val activeIndex = list.indexOfFirst { it.isCurrent }
        if (activeIndex != -1) {
            currentProgramIndex = activeIndex
        } else {
            currentProgramIndex = 0
        }
    }

    // =========================================================================
    // WebView 配置
    // =========================================================================
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = findViewById(R.id.main_webview)
        val webSettings = webView.settings
        webSettings.apply {
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = PC_USER_AGENT
        }

        webView.isFocusableInTouchMode = false
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean { return false }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectScript()
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase() ?: return null

                // ---- 1. 字体文件：TV 端无需 Web 字体 ----
                if (url.endsWith(".woff") || url.endsWith(".woff2") ||
                    url.endsWith(".ttf") || url.endsWith(".otf") ||
                    url.endsWith(".eot") || url.contains("/fonts/")) {
                    return EMPTY_RESPONSE()
                }

                // ---- 2. 图片资源：纯视频播放无需任何图片 ----
                // 视频流走 blob URL 不会被匹配；控件交互靠 CSS 类名定位，不依赖图片渲染
                if (url.endsWith(".jpg") || url.endsWith(".jpeg") ||
                    url.endsWith(".png") || url.endsWith(".gif") ||
                    url.endsWith(".webp") || url.endsWith(".svg") ||
                    url.endsWith(".ico") || url.endsWith(".bmp") ||
                    url.endsWith(".avif")) {
                    return EMPTY_RESPONSE()
                }

                // ---- 3. 统计追踪 & 广告 & 埋点 ----
                val blockKeywords = arrayOf(
                    // 百度统计
                    "hm.baidu.com", "tongji.baidu.com",
                    // Google
                    "google-analytics", "googletagmanager",
                    // CNZZ / 友盟
                    "s.cnzz.com", "umeng.com",
                    // 通用追踪/埋点关键字
                    "/beacon", "/trace", "/report", "/collect",
                    "/log.", "/logs/", "/monitor", "/tracking",
                    "/analytics", "/stat.", "/stats/", "/stats?",
                    "/tongji", "/datacenter",
                    // 央视频/CMG 内部埋点 & 非核心 SDK
                    "openapi-trace", "tracing", "sentry",
                    "bugly", "hotfix", "crash",
                    // 广告
                    "ad.doubleclick", "pagead", "adservice",
                    "adsense", "adsbygoogle"
                )
                for (keyword in blockKeywords) {
                    if (url.contains(keyword)) {
                        return EMPTY_RESPONSE()
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return true
            }
        }
        webView.loadUrl(TARGET_URL)
    }

    private fun injectScript() {
        webView.evaluateJavascript(ScriptRepo.AUTOMATION_SCRIPT, null)
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    override fun onDestroy() {
        // 销毁时停止所有动画
        stopLoadingDotsAnimation()
        if (breathingAnimator.isStarted) {
            breathingAnimator.cancel()
        }

        webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        webView.clearHistory()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
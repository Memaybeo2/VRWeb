package com.vrbox.webmirror

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * VR Box Web Mirror
 *
 * Cách hoạt động:
 *  - "Mắt trái" (eyeContainer) chứa WebView thật + thanh nút VR + con trỏ nhìn.
 *  - "Mắt phải" (mirrorView) là ảnh sao chép liên tục (khoảng 30 khung hình/giây)
 *    của vùng mắt trái, lấy bằng PixelCopy. Nhờ vậy hai mắt thấy cùng một hình.
 *  - Trong chế độ VR, con trỏ di chuyển theo cử động đầu (cảm biến xoay).
 *    Nhìn yên 1 điểm vài giây = bấm (dwell click).
 */
class MainActivity : ComponentActivity(), SensorEventListener {

    companion object {
        /** Trang chủ mặc định. Đổi địa chỉ này nếu muốn trang chủ khác. */
        private const val HOME_URL = "https://www.google.com"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Safari/537.36"

        private const val FRAME_INTERVAL_MS = 33L      // ~30 khung hình/giây cho mắt phải
        private const val SMOOTHING = 0.25f            // làm mượt con trỏ (0..1)
        private const val DWELL_RADIUS_DP = 28f        // vùng "nhìn yên" tính là cùng 1 điểm
        private const val HOVER_SCROLL_STEP = 14       // px mỗi lần cảm biến cập nhật
        private const val KEY_SCROLL_STEP = 300        // px mỗi lần bấm phím âm lượng
        private const val AUTO_RECENTER_DELAY_MS = 5000L
    }

    // ===== Các View =====
    private lateinit var root: FrameLayout
    private lateinit var urlBar: View
    private lateinit var urlInput: EditText
    private lateinit var leftHalf: FrameLayout
    private lateinit var rightHalf: FrameLayout
    private lateinit var eyeContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var vrBar: View
    private lateinit var btnScrollUp: View
    private lateinit var btnScrollDown: View
    private lateinit var cursorView: GazeCursorView
    private lateinit var mirrorView: ImageView
    private lateinit var settingsOverlay: View

    // ===== Trạng thái =====
    private var vrMode = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var defaultUserAgent = ""

    private val prefs by lazy { getSharedPreferences("vrbox", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    // Cài đặt (được lưu lại)
    private var marginOuter = 10     // dp - lề ngoài của mỗi mắt
    private var marginInner = 30     // dp - lề trong (gần mũi) của mỗi mắt
    private var marginVertical = 20  // dp - lề trên/dưới
    private var sensitivity = 50     // 0..100
    private var dwellProgress = 16   // 0..50  -> thời gian = 500ms + progress*50ms
    private var dwellEnabled = true
    private var desktopMode = false
    private var textZoom = 100
    private var mouseMode = false    // true = dùng chuột, tắt cảm biến đầu / nhìn để bấm / cuộn bằng nhìn

    // Trạng thái chuột trong chế độ VR
    private var mouseDown = false
    private var mouseDownTime = 0L
    private val capturedListener =
        View.OnCapturedPointerListener { _, e -> handleCapturedPointer(e) }

    // Sao chép hình sang mắt phải
    private var mirrorBitmap: Bitmap? = null
    private var copyPending = false

    // Cảm biến + con trỏ
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private val rotMatrix = FloatArray(9)
    private var refYaw = 0f
    private var refPitch = 0f
    private var needRecenter = true
    private var cursorX = 0f
    private var cursorY = 0f
    private var dwellAnchorX = 0f
    private var dwellAnchorY = 0f
    private var dwellStart = 0L
    private var dwellArmed = true
    private val tmpRect = Rect()
    private val piF = Math.PI.toFloat()

    private val dwellMs: Long
        get() = 500L + dwellProgress * 50L

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!vrMode) return
            copyFrame()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    private val recenterRunnable = Runnable { needRecenter = true }

    // =====================================================================
    //  Vòng đời Activity
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15/16 bắt buộc giao diện tràn viền: tự xử lý vùng an toàn bằng insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 30) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            window.attributes = lp
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        loadPrefs()
        bindViews()
        setupInsets()
        setupWebView()
        setupUi()
        setupSettings()
        setupBackHandling()
        applyMargins()

        webView.loadUrl(prefs.getString("lastUrl", HOME_URL) ?: HOME_URL)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        if (vrMode) startVrServices()
    }

    override fun onPause() {
        if (vrMode) stopVrServices()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    // =====================================================================
    //  Khởi tạo giao diện
    // =====================================================================

    private fun loadPrefs() {
        marginOuter = prefs.getInt("marginOuter", marginOuter)
        marginInner = prefs.getInt("marginInner", marginInner)
        marginVertical = prefs.getInt("marginVertical", marginVertical)
        sensitivity = prefs.getInt("sensitivity", sensitivity)
        dwellProgress = prefs.getInt("dwellProgress", dwellProgress)
        dwellEnabled = prefs.getBoolean("dwellEnabled", dwellEnabled)
        desktopMode = prefs.getBoolean("desktopMode", desktopMode)
        textZoom = prefs.getInt("textZoom", textZoom)
        mouseMode = prefs.getBoolean("mouseMode", mouseMode)
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        urlBar = findViewById(R.id.urlBar)
        urlInput = findViewById(R.id.urlInput)
        leftHalf = findViewById(R.id.leftHalf)
        rightHalf = findViewById(R.id.rightHalf)
        eyeContainer = findViewById(R.id.eyeContainer)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        vrBar = findViewById(R.id.vrBar)
        btnScrollUp = findViewById(R.id.btnScrollUp)
        btnScrollDown = findViewById(R.id.btnScrollDown)
        cursorView = findViewById(R.id.cursorView)
        mirrorView = findViewById(R.id.mirrorView)
        settingsOverlay = findViewById(R.id.settingsOverlay)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            if (vrMode) {
                v.setPadding(0, 0, 0, 0)
            } else {
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        defaultUserAgent = s.userAgentString
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.textZoom = textZoom
        applyDesktopMode()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val scheme = request?.url?.scheme ?: return false
                // Chỉ cho phép http/https. Chặn các liên kết lạ (intent:, market:...)
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && !urlInput.hasFocus()) urlInput.setText(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null) prefs.edit().putString("lastUrl", url).apply()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            // Video toàn màn hình: đặt vào trong khung mắt trái để cũng được sao chép sang mắt phải.
            override fun onShowCustomView(
                view: View?,
                callback: WebChromeClient.CustomViewCallback?
            ) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                eyeContainer.addView(
                    view,
                    1,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }
    }

    private fun hideCustomView() {
        customView?.let { eyeContainer.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    private fun applyDesktopMode() {
        val s = webView.settings
        s.userAgentString = if (desktopMode) DESKTOP_UA else defaultUserAgent
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
    }

    private fun click(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { action() }
    }

    private fun setupUi() {
        // Nhận sự kiện chuột khi đã "khoá chuột" (pointer capture) trong chế độ VR
        eyeContainer.isFocusable = true
        eyeContainer.isFocusableInTouchMode = true
        eyeContainer.setOnCapturedPointerListener(capturedListener)
        webView.setOnCapturedPointerListener(capturedListener)

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                goToUrl(urlInput.text.toString())
                true
            } else {
                false
            }
        }

        // Thanh địa chỉ (chế độ thường)
        click(R.id.goButton) { goToUrl(urlInput.text.toString()) }
        click(R.id.vrButton) { setVrMode(true) }
        click(R.id.settingsButton) { settingsOverlay.visibility = View.VISIBLE }
        click(R.id.closeSettingsButton) { settingsOverlay.visibility = View.GONE }

        // Thanh nút trong chế độ VR
        click(R.id.btnBack) { if (webView.canGoBack()) webView.goBack() }
        click(R.id.btnForward) { if (webView.canGoForward()) webView.goForward() }
        click(R.id.btnReload) { webView.reload() }
        click(R.id.btnHome) { webView.loadUrl(HOME_URL) }
        click(R.id.btnZoomOut) { changeTextZoom(-20) }
        click(R.id.btnZoomIn) { changeTextZoom(20) }
        click(R.id.btnScrollUp) { webView.scrollBy(0, -(webView.height * 2 / 3)) }
        click(R.id.btnScrollDown) { webView.scrollBy(0, webView.height * 2 / 3) }
        click(R.id.btnRecenter) { needRecenter = true }
        click(R.id.btnExitVr) { setVrMode(false) }
    }

    private fun changeTextZoom(delta: Int) {
        textZoom = (textZoom + delta).coerceIn(50, 300)
        webView.settings.textZoom = textZoom
        prefs.edit().putInt("textZoom", textZoom).apply()
    }

    private fun bindSeek(
        seekId: Int,
        labelId: Int,
        initial: Int,
        label: (Int) -> String,
        onChange: (Int) -> Unit
    ) {
        val seek = findViewById<SeekBar>(seekId)
        val text = findViewById<TextView>(labelId)
        seek.progress = initial
        text.text = label(initial)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                text.text = label(progress)
                onChange(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupSettings() {
        bindSeek(R.id.seekOuter, R.id.labelOuter, marginOuter, { "Lề ngoài mỗi mắt: $it dp" }) {
            marginOuter = it
            prefs.edit().putInt("marginOuter", it).apply()
            applyMargins()
        }
        bindSeek(R.id.seekInner, R.id.labelInner, marginInner, { "Lề trong (gần mũi): $it dp" }) {
            marginInner = it
            prefs.edit().putInt("marginInner", it).apply()
            applyMargins()
        }
        bindSeek(R.id.seekVertical, R.id.labelVertical, marginVertical, { "Lề trên/dưới: $it dp" }) {
            marginVertical = it
            prefs.edit().putInt("marginVertical", it).apply()
            applyMargins()
        }
        bindSeek(R.id.seekSens, R.id.labelSens, sensitivity, { "Độ nhạy xoay đầu: $it" }) {
            sensitivity = it
            prefs.edit().putInt("sensitivity", it).apply()
        }
        bindSeek(
            R.id.seekDwell, R.id.labelDwell, dwellProgress,
            { "Thời gian nhìn để bấm: ${(500 + it * 50) / 1000f} giây" }
        ) {
            dwellProgress = it
            prefs.edit().putInt("dwellProgress", it).apply()
        }

        val checkDwell = findViewById<CheckBox>(R.id.checkDwell)
        checkDwell.isChecked = dwellEnabled
        checkDwell.setOnCheckedChangeListener { _, checked ->
            dwellEnabled = checked
            prefs.edit().putBoolean("dwellEnabled", checked).apply()
        }

        val checkMouse = findViewById<CheckBox>(R.id.checkMouse)
        checkMouse.isChecked = mouseMode
        checkMouse.setOnCheckedChangeListener { _, checked ->
            mouseMode = checked
            prefs.edit().putBoolean("mouseMode", checked).apply()
        }

        val checkDesktop = findViewById<CheckBox>(R.id.checkDesktop)
        checkDesktop.isChecked = desktopMode
        checkDesktop.setOnCheckedChangeListener { _, checked ->
            desktopMode = checked
            prefs.edit().putBoolean("desktopMode", checked).apply()
            applyDesktopMode()
            webView.reload()
        }
    }

    private fun setupBackHandling() {
        // Android 16 dùng "back dự đoán" nên phải đăng ký callback thay vì onBackPressed().
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    settingsOverlay.visibility == View.VISIBLE ->
                        settingsOverlay.visibility = View.GONE
                    customView != null -> hideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    vrMode -> setVrMode(false)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // =====================================================================
    //  Điều hướng web
    // =====================================================================

    private fun goToUrl(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            text.contains(" ") || !text.contains(".") ->
                "https://www.google.com/search?q=" + Uri.encode(text)
            else -> "https://$text"
        }
        webView.loadUrl(url)
        urlInput.clearFocus()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    // =====================================================================
    //  Chế độ VR
    // =====================================================================

    private fun setVrMode(enable: Boolean) {
        vrMode = enable
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (enable) {
            settingsOverlay.visibility = View.GONE
            urlBar.visibility = View.GONE
            rightHalf.visibility = View.VISIBLE
            vrBar.visibility = View.VISIBLE
            cursorView.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            hideKeyboard()
            needRecenter = true
            handler.removeCallbacks(recenterRunnable)
            handler.postDelayed(recenterRunnable, AUTO_RECENTER_DELAY_MS)
            if (mouseMode) {
                Toast.makeText(
                    this,
                    "Chế độ chuột: chuột trái = bấm, lăn chuột = cuộn, chuột phải = Lùi.",
                    Toast.LENGTH_LONG
                ).show()
            } else if (rotationSensor == null) {
                Toast.makeText(
                    this,
                    "Máy không có cảm biến xoay. Hãy dùng tay cầm/bàn phím Bluetooth.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Đặt máy vào kính VR. Sau 5 giây con trỏ sẽ tự căn giữa.",
                    Toast.LENGTH_LONG
                ).show()
            }
            startVrServices()
        } else {
            stopVrServices()
            handler.removeCallbacks(recenterRunnable)
            urlBar.visibility = View.VISIBLE
            rightHalf.visibility = View.GONE
            vrBar.visibility = View.GONE
            cursorView.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
            mirrorView.setImageDrawable(null)
            mirrorBitmap = null
            copyPending = false
        }

        applyMargins()
        ViewCompat.requestApplyInsets(root)
    }

    private fun startVrServices() {
        if (mouseMode) {
            // Không dùng cảm biến. Khoá chuột để tự vẽ con trỏ (hiện ở cả 2 mắt).
            mouseDown = false
            eyeContainer.post {
                cursorX = eyeContainer.width / 2f
                cursorY = eyeContainer.height / 2f
                cursorView.progress = 0f
                cursorView.setPosition(cursorX, cursorY)
            }
            eyeContainer.requestFocus()
            eyeContainer.requestPointerCapture()
        } else {
            rotationSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        handler.removeCallbacks(frameRunnable)
        handler.post(frameRunnable)
    }

    private fun stopVrServices() {
        sensorManager.unregisterListener(this)
        eyeContainer.releasePointerCapture()
        mouseDown = false
        handler.removeCallbacks(frameRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Lấy lại quyền khoá chuột nếu bị mất (ví dụ sau khi kéo thanh thông báo)
        if (hasFocus && vrMode && mouseMode) {
            eyeContainer.requestFocus()
            eyeContainer.requestPointerCapture()
        }
    }

    private fun applyMargins() {
        if (!vrMode) {
            leftHalf.setPadding(0, 0, 0, 0)
            rightHalf.setPadding(0, 0, 0, 0)
            return
        }
        val d = resources.displayMetrics.density
        val outer = (marginOuter * d).toInt()
        val inner = (marginInner * d).toInt()
        val v = (marginVertical * d).toInt()
        // Mắt trái: lề ngoài bên trái, lề trong bên phải. Mắt phải: ngược lại.
        leftHalf.setPadding(outer, v, inner, v)
        rightHalf.setPadding(inner, v, outer, v)
    }

    // =====================================================================
    //  Sao chép hình mắt trái -> mắt phải
    // =====================================================================

    private fun copyFrame() {
        if (copyPending || !eyeContainer.isLaidOut) return
        val w = eyeContainer.width
        val h = eyeContainer.height
        if (w <= 0 || h <= 0) return

        val existing = mirrorBitmap
        val bmp = if (existing != null && existing.width == w && existing.height == h) {
            existing
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { mirrorBitmap = it }
        }

        val loc = IntArray(2)
        eyeContainer.getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)

        copyPending = true
        try {
            PixelCopy.request(
                window,
                rect,
                bmp,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    copyPending = false
                    if (result == PixelCopy.SUCCESS) {
                        mirrorView.setImageBitmap(bmp)
                    } else {
                        softwareCopy(bmp)
                    }
                },
                handler
            )
        } catch (e: IllegalArgumentException) {
            copyPending = false
            softwareCopy(bmp)
        }
    }

    /** Phương án dự phòng nếu PixelCopy thất bại. */
    private fun softwareCopy(bmp: Bitmap) {
        try {
            eyeContainer.draw(Canvas(bmp))
            mirrorView.setImageBitmap(bmp)
        } catch (e: Exception) {
            // Bỏ qua khung hình này
        }
    }

    // =====================================================================
    //  Cảm biến xoay đầu -> con trỏ
    // =====================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (!vrMode || mouseMode || event == null) return
        val w = eyeContainer.width.toFloat()
        val h = eyeContainer.height.toFloat()
        if (w <= 0f || h <= 0f) return

        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

        // Hướng nhìn = trục -Z của điện thoại (xuyên qua màn hình ra phía sau),
        // biểu diễn trong hệ toạ độ thế giới. Cách này không phụ thuộc xoay ngang/dọc.
        val vx = -rotMatrix[2]
        val vy = -rotMatrix[5]
        val vz = -rotMatrix[8]
        val yaw = atan2(vx, vy)                    // quay trái/phải
        val pitch = asin(vz.coerceIn(-1f, 1f))     // ngẩng/cúi

        if (needRecenter) {
            refYaw = yaw
            refPitch = pitch
            needRecenter = false
            cursorX = w / 2f
            cursorY = h / 2f
            dwellAnchorX = cursorX
            dwellAnchorY = cursorY
            dwellStart = SystemClock.uptimeMillis()
            dwellArmed = true
        }

        var dYaw = yaw - refYaw
        if (dYaw > piF) dYaw -= 2 * piF else if (dYaw < -piF) dYaw += 2 * piF
        val dPitch = pitch - refPitch

        // Độ nhạy: góc đầu cần xoay để con trỏ đi hết chiều ngang màn hình (100° -> 20°)
        val coverDeg = 100f - sensitivity * 0.8f
        val pxPerRad = w / Math.toRadians(coverDeg.toDouble()).toFloat()

        val targetX = (w / 2f + dYaw * pxPerRad).coerceIn(0f, w)
        val targetY = (h / 2f - dPitch * pxPerRad).coerceIn(0f, h)
        cursorX += (targetX - cursorX) * SMOOTHING
        cursorY += (targetY - cursorY) * SMOOTHING
        cursorView.setPosition(cursorX, cursorY)

        // Nhìn vào nút "Lên"/"Xuống" thì cuộn liên tục
        if (hoverScroll()) {
            resetDwell()
            return
        }
        updateDwell()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun isCursorOver(v: View): Boolean {
        if (!v.isShown) return false
        v.getDrawingRect(tmpRect)
        eyeContainer.offsetDescendantRectToMyCoords(v, tmpRect)
        return tmpRect.contains(cursorX.toInt(), cursorY.toInt())
    }

    private fun hoverScroll(): Boolean {
        if (isCursorOver(btnScrollUp)) {
            webView.scrollBy(0, -HOVER_SCROLL_STEP)
            return true
        }
        if (isCursorOver(btnScrollDown)) {
            webView.scrollBy(0, HOVER_SCROLL_STEP)
            return true
        }
        return false
    }

    private fun resetDwell() {
        dwellAnchorX = cursorX
        dwellAnchorY = cursorY
        dwellStart = SystemClock.uptimeMillis()
        dwellArmed = true
        cursorView.progress = 0f
    }

    private fun updateDwell() {
        if (!dwellEnabled) {
            cursorView.progress = 0f
            return
        }
        val radius = DWELL_RADIUS_DP * resources.displayMetrics.density
        val dist = hypot(cursorX - dwellAnchorX, cursorY - dwellAnchorY)
        if (dist > radius) {
            resetDwell()
            return
        }
        if (!dwellArmed) {
            cursorView.progress = 0f
            return
        }
        val p = (SystemClock.uptimeMillis() - dwellStart).toFloat() / dwellMs
        cursorView.progress = p.coerceIn(0f, 1f)
        if (p >= 1f) {
            dwellArmed = false      // phải di chuyển ra khỏi vùng mới bấm tiếp được
            cursorView.progress = 0f
            clickAtCursor()
        }
    }

    /** Giả lập một cú chạm tại vị trí con trỏ (vào WebView hoặc các nút VR). */
    private fun clickAtCursor() {
        val x = cursorX
        val y = cursorY
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        eyeContainer.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val up = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0
            )
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            eyeContainer.dispatchTouchEvent(up)
            up.recycle()
        }, 60L)
    }

    // =====================================================================
    //  Phím cứng / tay cầm Bluetooth
    // =====================================================================

    // =====================================================================
    //  Chuột (qua hub OTG) trong chế độ VR
    // =====================================================================

    /** Gửi một sự kiện chạm giả tại vị trí con trỏ vào khung mắt trái. */
    private fun sendTouch(action: Int) {
        val ev = MotionEvent.obtain(
            mouseDownTime, SystemClock.uptimeMillis(), action, cursorX, cursorY, 0
        )
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        eyeContainer.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun handleCapturedPointer(e: MotionEvent): Boolean {
        if (!vrMode || !mouseMode) return false
        val w = eyeContainer.width.toFloat()
        val h = eyeContainer.height.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                // Khi khoá chuột, x/y là độ dịch chuyển tương đối
                cursorX = (cursorX + e.x).coerceIn(0f, w)
                cursorY = (cursorY + e.y).coerceIn(0f, h)
                cursorView.setPosition(cursorX, cursorY)
                if (mouseDown) sendTouch(MotionEvent.ACTION_MOVE)
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (e.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    mouseDown = true
                    mouseDownTime = SystemClock.uptimeMillis()
                    sendTouch(MotionEvent.ACTION_DOWN)
                } else if (e.actionButton == MotionEvent.BUTTON_SECONDARY) {
                    if (webView.canGoBack()) webView.goBack()
                }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (e.actionButton == MotionEvent.BUTTON_PRIMARY && mouseDown) {
                    mouseDown = false
                    sendTouch(MotionEvent.ACTION_UP)
                }
            }
            MotionEvent.ACTION_SCROLL -> {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                webView.scrollBy(0, (-v * 120f).toInt())
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (vrMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                    webView.scrollBy(0, -KEY_SCROLL_STEP)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    webView.scrollBy(0, KEY_SCROLL_STEP)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    clickAtCursor()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    needRecenter = true
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

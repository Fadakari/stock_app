package com.example.stockdivarapp

// =================================================================
// === بخش ایمپورت‌ها (نسخه کامل و نهایی) ===
// =================================================================
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.stockdivarapp.ui.theme.StockDivarAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ۱. فعال‌سازی صفحه اسپلش مدرن
        installSplashScreen()

        // ۲. جداسازی نوار وضعیت از محتوای وب
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            
            // ۳. تنظیمات اولیه رنگ نوار وضعیت بر اساس تم سیستم
            SideEffect {
                val defaultColor = if (darkTheme) Color.parseColor("#1C1C1E") else Color.parseColor("#F8F8F8")
                window.statusBarColor = defaultColor
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
            }

            StockDivarAppTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WebPage(url = "https://stockdivar.ir")
                }
            }
        }
        
        // ۴. مدیریت دکمه بازگشت فیزیکی گوشی
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack() // اگر WebView تاریخچه داشت، به عقب برگرد
                } else {
                    finish() // در غیر این صورت، اپلیکیشن را ببند
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    @Composable
    fun WebPage(url: String) {
        var progress by remember { mutableFloatStateOf(0f) }
        var isPageLoading by remember { mutableStateOf(true) }

        // --- بخش مدیریت اجازه موقعیت مکانی ---
        var geolocationCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
        var geolocationOrigin by remember { mutableStateOf<String?>(null) }
        val locationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            geolocationCallback?.invoke(geolocationOrigin, fineLocationGranted || coarseLocationGranted, false)
        }
        // --- پایان بخش موقعیت مکانی ---

        // --- بخش مدیریت انتخابگر فایل مدرن ---
        // var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
        // val fileChooserLauncher = rememberLauncherForActivityResult(
        //     contract = ActivityResultContracts.StartActivityForResult()
        // ) { result ->
        //     val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        //     filePathCallback?.onReceiveValue(uris)
        //     filePathCallback = null
        // }
        // --- پایان بخش انتخابگر فایل ---

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webView = this // انتساب نمونه WebView

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setGeolocationEnabled(true)
                            allowFileAccess = true
                            allowContentAccess = true
                            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                            // پشتیبانی از باز شدن لینک در پنجره جدید (target="_blank")
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            val originalUserAgent = userAgentString
                            userAgentString = "$originalUserAgent StockDivarApp/1.0"
                        }

                        // ========== اصلاح کلیدی: ارسال context به جای cast کردن به Activity ==========
                        addJavascriptInterface(MyWebInterface(context), "Android")

                        webViewClient = object : WebViewClient() {
                            private val TRUSTED_HOST = "stockdivar.ir"

                            // ========== اصلاح کلیدی: مدیریت هوشمند URL ها برای رفع خطای ERR_RESPONSE_CODE_FAILURE ==========
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                val host = url.host
                                
                                Log.d("WebViewDebug", "shouldOverrideUrlLoading: ${url.toString()}")

                                // اگر لینک داخلی بود، به WebView اجازه بارگذاری بده
                                if (host == TRUSTED_HOST) {
                                    return false // به WebView اجازه بده خودش این لینک را بارگذاری کند
                                }

                                // برای سایر لینک‌ها (خارجی)، آن‌ها را در مرورگر پیش‌فرض باز کن
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, url)
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "هیچ اپلیکیشنی برای باز کردن این لینک پیدا نشد.", Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                // منطق امنیتی برای فعال/غیرفعال کردن رابط
                                if (Uri.parse(url)?.host == TRUSTED_HOST) {
                                    addJavascriptInterface(MyWebInterface(context), "Android")
                                } else {
                                    removeJavascriptInterface("Android")
                                }
                            }
                            
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                // برای هر خطایی در URL اصلی، صفحه آفلاین را نشان بده
                                if (request != null && request.isForMainFrame) {
                                    Log.e("WebViewDebug", "Error loading main frame: ${error?.errorCode} ${error?.description}")
                                    view?.loadUrl("file:///android_asset/offline_page.html")
                                }
                            }

                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                Log.e("WebViewDebug", "HTTP Error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase} for URL ${request?.url}")
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.proceed()
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                isPageLoading = newProgress < 100
                                progress = newProgress / 100f
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    Log.d("JSConsole", "[${it.messageLevel()}] ${it.message()} -- ${it.sourceId()}:${it.lineNumber()}")
                                }
                                return true
                            }
                            
                            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                                val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                                if (granted) {
                                    callback.invoke(origin, true, false)
                                } else {
                                    geolocationCallback = callback
                                    geolocationOrigin = origin
                                    locationPermissionLauncher.launch(permissions)
                                }
                            }
                            
                            override fun onShowFileChooser(
                                webView: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams
                            ): Boolean {
                                this@MainActivity.filePathCallback?.onReceiveValue(null) // قبلی رو آزاد کن
                                this@MainActivity.filePathCallback = filePathCallback
                            
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) // اگر فقط یک عکس مجازه
                                }
                            
                                return try {
                                    fileChooserLauncher.launch(Intent.createChooser(intent, "انتخاب عکس"))
                                    true
                                } catch (e: Exception) {
                                    this@MainActivity.filePathCallback = null
                                    Toast.makeText(webView.context, "انتخابگر فایل باز نشد", Toast.LENGTH_SHORT).show()
                                    false
                                }
                            }

                            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                                val newWebView = WebView(context)
                                newWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        this@apply.loadUrl(request.url.toString())
                                        return true
                                    }
                                }
                                val transport = resultMsg.obj as WebView.WebViewTransport
                                transport.webView = newWebView
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isPageLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = androidx.compose.ui.graphics.Color(0xFF9B1B30),
                    trackColor = androidx.compose.ui.graphics.Color.Transparent
                )
            }
        }
    }
}

// رابط بین جاوااسکریپت و کاتلین
class MyWebInterface(private val context: Context) {
    
    @JavascriptInterface
    fun shareContent(text: String, title: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        context.startActivity(shareIntent)
    }

    @JavascriptInterface
    fun performHapticFeedback() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    @JavascriptInterface
    fun updateThemeColor(hexColor: String) {
        val activity = context as? Activity ?: return // تبدیل امن به Activity

        activity.runOnUiThread {
            try {
                val color = Color.parseColor(hexColor)
                activity.window.statusBarColor = color
                
                val isDark = Color.luminance(color) < 0.5
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = !isDark
            } catch (e: IllegalArgumentException) {
                // نادیده گرفتن کد رنگ نامعتبر
            }
        }
    }
}
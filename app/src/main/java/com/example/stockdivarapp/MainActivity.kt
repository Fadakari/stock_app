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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.stockdivarapp.ui.theme.StockDivarAppTheme
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    

    private var cameraImageUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "برای استفاده از دوربین، مجوز لازم است.", Toast.LENGTH_LONG).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                filePathCallback?.onReceiveValue(arrayOf(uri))
                filePathCallback = null
            }
        } else {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        // var geolocationCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
        // var geolocationOrigin by remember { mutableStateOf<String?>(null) }
        // val locationPermissionLauncher = rememberLauncherForActivityResult(
        //     contract = ActivityResultContracts.RequestMultiplePermissions()
        // ) { permissions ->
        //     val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        //     val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        //     geolocationCallback?.invoke(geolocationOrigin, fineLocationGranted || coarseLocationGranted, false)
        // }
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
                            allowFileAccess = true
                            allowContentAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)

                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            val originalUserAgent = userAgentString
                            userAgentString = "$originalUserAgent StockDivarApp/1.0"
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            val request = android.app.DownloadManager.Request(Uri.parse(url))
                            request.setMimeType(mimeType)
                            request.addRequestHeader("User-Agent", userAgent)
                            request.setDescription("در حال دانلود فایل...")
                            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                            request.setTitle(fileName)
                            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            downloadManager.enqueue(request)
                            Toast.makeText(context, "دانلود آغاز شد...", Toast.LENGTH_SHORT).show()
                        }

                        // ========== اصلاح کلیدی: ارسال context به جای cast کردن به Activity ==========
                        addJavascriptInterface(MyWebInterface(context), "Android")

                        webViewClient = object : WebViewClient() {
                            private val TRUSTED_HOST = "stockdivar.ir"
                                                
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                val host = url.host
                                
                                Log.d("WebViewDebug", "shouldOverrideUrlLoading: ${url.toString()}")
                            
                                if (host == TRUSTED_HOST) {
                                    return false 
                                }
                            
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, url)
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "هیچ اپلیکیشنی برای باز کردن این لینک پیدا نشد.", Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }
                        
                            // 🔥 تغییر کلیدی اینجاست: onReceivedError اصلاح شد 🔥
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                // این شرط تضمین می‌کند که صفحه آفلاین فقط برای خطاهای اصلی (مانند عدم اتصال) نمایش داده شود
                                // و نه برای خطاهای جزئی مثل لود نشدن یک عکس.
                                if (request != null && request.isForMainFrame) {
                                    // کد خطای `ERR_INTERNET_DISCONNECTED` مختص زمان قطعی اینترنت است.
                                    val errorCode = error?.errorCode
                                    if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT || errorCode == ERROR_UNKNOWN) {
                                        Log.e("WebViewDebug", "Internet connection error detected. Loading offline page.")
                                        view?.loadUrl("file:///android_asset/offline_page.html")
                                    }
                                }
                            }
                        
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                // همچنین اگر سرور خطایی مثل 404 یا 500 برگرداند، صفحه آفلاین را نمایش می‌دهیم
                                if (request != null && request.isForMainFrame) {
                                    if (errorResponse != null && (errorResponse.statusCode >= 400)) {
                                         Log.e("WebViewDebug", "HTTP Error ${errorResponse.statusCode}. Loading offline page.")
                                         view?.loadUrl("file:///android_asset/offline_page.html")
                                    }
                                }
                            }
                        
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                super.onReceivedSslError(view, handler, error)
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
                            
                            
                            override fun onShowFileChooser(
                                webView: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams
                            ): Boolean {
                                this@MainActivity.filePathCallback?.onReceiveValue(null)
                                this@MainActivity.filePathCallback = filePathCallback

                                showImageSourceDialog()

                                return true
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

    private fun showImageSourceDialog() {
        val items = arrayOf("گرفتن عکس با دوربین", "انتخاب از گالری")
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب منبع عکس")
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch() // آیتم اول: دوربین
                    1 -> launchGallery()                 // آیتم دوم: گالری
                }
                dialog.dismiss()
            }
            .setOnCancelListener {
                // در صورت بستن دیالوگ، انتخاب فایل را لغو کن
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }

    /**
     * مجوز دوربین را بررسی کرده و در صورت وجود، دوربین را اجرا می‌کند.
     */
    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // مجوز از قبل وجود دارد
                launchCamera()
            }
            else -> {
                // مجوز را درخواست کن
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * یک URI امن برای فایل عکسی که دوربین خواهد گرفت، ایجاد می‌کند.
     */
    private fun createImageUri(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        // فایل در حافظه کش خارجی اپلیکیشن ذخیره می‌شود
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        // از FileProvider برای ایجاد URI استفاده می‌شود
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            imageFile
        )
    }

    /**
     * اینتنت دوربین را با URI ساخته شده اجرا می‌کند.
     */
    private fun launchCamera() {
        cameraImageUri = createImageUri()
        cameraImageUri?.let {
            cameraLauncher.launch(it)
        }
    }

    /**
     * اینتنت گالری را اجرا می‌کند.
     */
    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        galleryLauncher.launch(Intent.createChooser(intent, "انتخاب عکس"))
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
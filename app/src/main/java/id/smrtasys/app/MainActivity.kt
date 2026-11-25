package id.smrtasys.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineBanner: LinearLayout
    private lateinit var tvOfflineBanner: TextView
    private lateinit var errorOverlay: ConstraintLayout
    private lateinit var tvErrorTitle: TextView
    private lateinit var tvErrorStatus: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnGoBack: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var activeBaseUrl: String = ServerConfig.PRIMARY_URL
    private var fallbackUsedForCurrentPage: Boolean = false
    private var retryCountForCurrentPage: Int = 0
    private var isPageLoadedOnce: Boolean = false
    private var isErrorShowing: Boolean = false
    private var pendingNavigationUrl: String? = null

    private val loadTimeoutRunnable = Runnable {
        if (!isPageLoadedOnce && !isErrorShowing) {
            handleLoadFailure(
                errorType = FailureType.TIMEOUT,
                failingUrl = webView.url ?: activeBaseUrl
            )
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val clipData = data?.clipData
            val singleData = data?.data
            when {
                clipData != null && clipData.itemCount > 0 -> {
                    val uris = mutableListOf<Uri>()
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                    callback.onReceiveValue(uris.toTypedArray())
                }
                singleData != null -> {
                    callback.onReceiveValue(arrayOf(singleData))
                }
                else -> callback.onReceiveValue(null)
            }
        } else {
            callback.onReceiveValue(null)
        }
    }

    private enum class FailureType {
        OFFLINE,
        NETWORK,
        TIMEOUT,
        SSL,
        POLICY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSwipeRefresh()
        setupErrorActions()
        setupBackNavigation()
        setupWebView()
        registerNetworkMonitor()

        val initialUrl = savedInstanceState?.getString(KEY_ACTIVE_URL)
            ?.takeIf { ServerConfig.isAllowedInternalUrl(it) }
            ?: ServerConfig.PRIMARY_URL

        activeBaseUrl = if (initialUrl.startsWith(ServerConfig.FALLBACK_URL.dropLast(1))) {
            ServerConfig.FALLBACK_URL
        } else {
            ServerConfig.PRIMARY_URL
        }

        updateNetworkIndicators(hasActiveNetwork())
        loadPortalUrl(initialUrl, resetCounters = true)
    }

    private fun bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh)
        webView = findViewById(R.id.portalWebView)
        progressBar = findViewById(R.id.pageProgressBar)
        offlineBanner = findViewById(R.id.offlineBanner)
        tvOfflineBanner = findViewById(R.id.tvOfflineBanner)
        errorOverlay = findViewById(R.id.errorOverlay)
        tvErrorTitle = findViewById(R.id.tvErrorTitle)
        tvErrorStatus = findViewById(R.id.tvErrorStatus)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
        btnGoBack = findViewById(R.id.btnGoBack)
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(
            R.color.accent_emerald,
            R.color.accent_gold,
            R.color.accent_emerald_soft
        )
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.bg_slate_dark)
        swipeRefresh.setOnRefreshListener {
            provideLightHaptic()
            val currentUrl = webView.url
            if (!currentUrl.isNullOrBlank() && ServerConfig.isAllowedInternalUrl(currentUrl)) {
                retryCountForCurrentPage = 0
                fallbackUsedForCurrentPage = false
                loadPortalUrl(currentUrl, resetCounters = false)
            } else {
                loadPortalUrl(activeBaseUrl, resetCounters = true)
            }
        }
    }

    private fun setupErrorActions() {
        btnRetry.setOnClickListener {
            provideLightHaptic()
            val targetUrl = pendingNavigationUrl
                ?: webView.url?.takeIf { ServerConfig.isAllowedInternalUrl(it) }
                ?: activeBaseUrl

            if (!hasActiveNetwork()) {
                showErrorOverlay(FailureType.OFFLINE)
                return@setOnClickListener
            }

            retryCountForCurrentPage = 0
            fallbackUsedForCurrentPage = false
            activeBaseUrl = ServerConfig.PRIMARY_URL
            val resolvedUrl = if (targetUrl.startsWith(ServerConfig.FALLBACK_URL.dropLast(1))) {
                ServerConfig.switchHostPreservingPath(targetUrl, ServerConfig.PRIMARY_URL)
            } else {
                targetUrl
            }
            hideErrorOverlay()
            loadPortalUrl(resolvedUrl, resetCounters = false)
        }

        btnGoBack.setOnClickListener {
            provideLightHaptic()
            hideErrorOverlay()
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                loadPortalUrl(ServerConfig.PRIMARY_URL, resetCounters = true)
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                provideLightHaptic()
                if (isErrorShowing) {
                    if (webView.canGoBack()) {
                        hideErrorOverlay()
                        webView.goBack()
                    } else {
                        showExitConfirmationDialog()
                    }
                    return
                }

                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showExitConfirmationDialog()
                }
            }
        })
    }

    private fun showExitConfirmationDialog() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_dialog_title))
            .setMessage(getString(R.string.exit_dialog_message))
            .setPositiveButton(getString(R.string.exit_dialog_confirm)) { _, _ ->
                flushCookiesSafely()
                finish()
            }
            .setNegativeButton(getString(R.string.exit_dialog_cancel), null)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.WEBVIEW_DEBUGGING)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }

        webView.webViewClient = PortalWebViewClient()
        webView.webChromeClient = PortalWebChromeClient()
        webView.setDownloadListener(PortalDownloadListener())
    }

    private fun loadPortalUrl(url: String, resetCounters: Boolean) {
        if (!ServerConfig.isAllowedInternalUrl(url)) {
            showErrorOverlay(FailureType.POLICY)
            return
        }

        if (resetCounters) {
            retryCountForCurrentPage = 0
            fallbackUsedForCurrentPage = false
        }

        pendingNavigationUrl = url
        isPageLoadedOnce = false
        hideErrorOverlay()

        progressBar.progress = 8
        progressBar.alpha = 1f
        progressBar.visibility = View.VISIBLE

        mainHandler.removeCallbacks(loadTimeoutRunnable)
        mainHandler.postDelayed(loadTimeoutRunnable, ServerConfig.PAGE_LOAD_TIMEOUT_MS)

        webView.loadUrl(url)
    }

    private fun handleLoadFailure(errorType: FailureType, failingUrl: String?) {
        mainHandler.removeCallbacks(loadTimeoutRunnable)
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE

        if (!hasActiveNetwork()) {
            showErrorOverlay(FailureType.OFFLINE)
            return
        }

        val canAttemptFallback = errorType != FailureType.SSL &&
            errorType != FailureType.POLICY &&
            !fallbackUsedForCurrentPage &&
            activeBaseUrl == ServerConfig.PRIMARY_URL

        if (canAttemptFallback) {
            fallbackUsedForCurrentPage = true
            activeBaseUrl = ServerConfig.FALLBACK_URL
            val fallbackTarget = ServerConfig.switchHostPreservingPath(
                currentUrl = failingUrl ?: pendingNavigationUrl,
                destinationBaseUrl = ServerConfig.FALLBACK_URL
            )
            loadPortalUrl(fallbackTarget, resetCounters = false)
            return
        }

        if (errorType != FailureType.SSL &&
            errorType != FailureType.POLICY &&
            retryCountForCurrentPage < ServerConfig.MAX_AUTO_RETRIES
        ) {
            retryCountForCurrentPage += 1
            val retryTarget = failingUrl ?: pendingNavigationUrl ?: activeBaseUrl
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed && hasActiveNetwork()) {
                    loadPortalUrl(retryTarget, resetCounters = false)
                } else {
                    showErrorOverlay(errorType)
                }
            }, 900L * retryCountForCurrentPage)
            return
        }

        showErrorOverlay(errorType)
    }

    private fun showErrorOverlay(type: FailureType) {
        isErrorShowing = true
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE

        when (type) {
            FailureType.OFFLINE -> {
                tvErrorTitle.text = getString(R.string.error_title)
                tvErrorStatus.text = "STATUS: OFFLINE"
                tvErrorMessage.text = getString(R.string.error_desc_offline)
            }
            FailureType.SSL -> {
                tvErrorTitle.text = getString(R.string.error_title)
                tvErrorStatus.text = "STATUS: TLS VERIFICATION FAILED"
                tvErrorMessage.text = getString(R.string.error_desc_ssl)
            }
            FailureType.POLICY -> {
                tvErrorTitle.text = getString(R.string.error_title)
                tvErrorStatus.text = "STATUS: RESTRICTED DESTINATION"
                tvErrorMessage.text = getString(R.string.error_desc_policy)
            }
            FailureType.TIMEOUT, FailureType.NETWORK -> {
                tvErrorTitle.text = getString(R.string.error_title)
                tvErrorStatus.text = "STATUS: CONNECTION INTERRUPTED"
                tvErrorMessage.text = getString(R.string.error_desc_network)
            }
        }

        if (errorOverlay.visibility != View.VISIBLE) {
            errorOverlay.alpha = 0f
            errorOverlay.visibility = View.VISIBLE
            errorOverlay.animate()
                .alpha(1f)
                .setDuration(220L)
                .start()
        }
    }

    private fun hideErrorOverlay() {
        isErrorShowing = false
        if (errorOverlay.visibility == View.VISIBLE) {
            errorOverlay.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    if (!isErrorShowing) {
                        errorOverlay.visibility = View.GONE
                    }
                }
                .start()
        }
    }

    private fun handleExternalIntent(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        return when (scheme) {
            "tel", "mailto", "whatsapp" -> launchSafeViewIntent(uri)
            "https", "http" -> launchSafeViewIntent(uri)
            else -> false
        }
    }

    private fun launchSafeViewIntent(uri: Uri): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun hasActiveNetwork(): Boolean {
        val cm = connectivityManager ?: getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateNetworkIndicators(isOnline: Boolean) {
        if (isOnline) {
            if (offlineBanner.visibility == View.VISIBLE) {
                offlineBanner.animate()
                    .alpha(0f)
                    .setDuration(200L)
                    .withEndAction { offlineBanner.visibility = View.GONE }
                    .start()
            }
            if (isErrorShowing && tvErrorStatus.text == "STATUS: OFFLINE") {
                val target = pendingNavigationUrl
                    ?: webView.url?.takeIf { ServerConfig.isAllowedInternalUrl(it) }
                    ?: activeBaseUrl
                hideErrorOverlay()
                loadPortalUrl(target, resetCounters = true)
            }
        } else {
            if (offlineBanner.visibility != View.VISIBLE) {
                offlineBanner.alpha = 0f
                offlineBanner.visibility = View.VISIBLE
                offlineBanner.animate()
                    .alpha(1f)
                    .setDuration(220L)
                    .start()
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { updateNetworkIndicators(true) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                mainHandler.post { updateNetworkIndicators(validated) }
            }

            override fun onLost(network: Network) {
                mainHandler.post { updateNetworkIndicators(hasActiveNetwork()) }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                mainHandler.post { updateNetworkIndicators(hasActiveNetwork()) }
            }
        }

        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkMonitor() {
        val cm = connectivityManager ?: return
        val callback = networkCallback ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun flushCookiesSafely() {
        runCatching {
            CookieManager.getInstance().flush()
        }
    }

    private fun provideLightHaptic() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                webView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
            } else {
                @Suppress("DEPRECATION")
                webView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentUrl = webView.url
        if (!currentUrl.isNullOrBlank() && ServerConfig.isAllowedInternalUrl(currentUrl)) {
            outState.putString(KEY_ACTIVE_URL, currentUrl)
        }
    }

    override fun onPause() {
        super.onPause()
        flushCookiesSafely()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        updateNetworkIndicators(hasActiveNetwork())
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        unregisterNetworkMonitor()
        flushCookiesSafely()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        swipeRefresh.removeAllViews()
        webView.stopLoading()
        webView.webChromeClient = null
        webView.destroy()
        super.onDestroy()
    }

    private inner class PortalWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return true
            val rawUrl = uri.toString()

            if (ServerConfig.isAllowedInternalUrl(rawUrl)) {
                pendingNavigationUrl = rawUrl
                activeBaseUrl = if (rawUrl.startsWith(ServerConfig.FALLBACK_URL.dropLast(1))) {
                    ServerConfig.FALLBACK_URL
                } else {
                    ServerConfig.PRIMARY_URL
                }
                return false
            }

            if (handleExternalIntent(uri)) {
                return true
            }

            if (uri.scheme?.lowercase(Locale.ROOT) != "https") {
                showErrorOverlay(FailureType.POLICY)
                return true
            }

            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (url != null) {
                pendingNavigationUrl = url
            }
            if (!isErrorShowing) {
                progressBar.visibility = View.VISIBLE
                progressBar.alpha = 1f
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            mainHandler.removeCallbacks(loadTimeoutRunnable)
            isPageLoadedOnce = true
            swipeRefresh.isRefreshing = false
            flushCookiesSafely()

            progressBar.progress = 100
            progressBar.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction {
                    progressBar.visibility = View.GONE
                    progressBar.progress = 0
                }
                .start()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true) {
                val type = if (!hasActiveNetwork()) FailureType.OFFLINE else FailureType.NETWORK
                handleLoadFailure(type, request.url?.toString())
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.cancel()
            mainHandler.removeCallbacks(loadTimeoutRunnable)
            swipeRefresh.isRefreshing = false
            progressBar.visibility = View.GONE
            showErrorOverlay(FailureType.SSL)
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: RenderProcessGoneDetail?
        ): Boolean {
            mainHandler.removeCallbacks(loadTimeoutRunnable)
            swipeRefresh.isRefreshing = false
            progressBar.visibility = View.GONE
            showErrorOverlay(FailureType.NETWORK)
            return true
        }
    }

    private inner class PortalWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (!isErrorShowing) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = newProgress.coerceIn(0, 100)
            }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            this@MainActivity.filePathCallback?.onReceiveValue(null)
            this@MainActivity.filePathCallback = filePathCallback

            val chooserIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, ServerConfig.ALLOWED_MIME_TYPES)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

            val wrappedIntent = Intent.createChooser(
                chooserIntent,
                getString(R.string.upload_chooser_title)
            )

            return runCatching {
                fileChooserLauncher.launch(wrappedIntent)
                true
            }.getOrElse {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = null
                false
            }
        }
    }

    private inner class PortalDownloadListener : DownloadListener {
        override fun onDownloadStart(
            url: String?,
            userAgent: String?,
            contentDisposition: String?,
            mimetype: String?,
            contentLength: Long
        ) {
            if (url.isNullOrBlank()) return
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
            if (uri.scheme?.lowercase(Locale.ROOT) != "https") {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.download_blocked_scheme),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            if (!ServerConfig.isAllowedInternalUrl(url)) {
                launchSafeViewIntent(uri)
                return
            }

            val rawFileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val sanitizedFileName = rawFileName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "smrtasys_download.bin" }

            runCatching {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(uri)
                    .setTitle(sanitizedFileName)
                    .setDescription(getString(R.string.app_name))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitizedFileName)

                if (!mimetype.isNullOrBlank()) {
                    request.setMimeType(mimetype)
                }

                dm.enqueue(request)
                provideLightHaptic()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.download_started, sanitizedFileName),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                launchSafeViewIntent(uri)
            }
        }
    }

    companion object {
        private const val KEY_ACTIVE_URL = "smrta_active_url"
    }
}

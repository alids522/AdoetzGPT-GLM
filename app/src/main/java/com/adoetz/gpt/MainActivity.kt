--- app/src/main/java/com/adoetz/gpt/MainActivity.kt (原始)
package com.adoetz.gpt

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.adoetz.gpt.databinding.ActivityMainBinding
import com.adoetz.gpt.models.BackendConfig
import com.adoetz.gpt.models.VoiceSessionState
import com.adoetz.gpt.service.VoiceSessionService
import com.adoetz.gpt.ui.BackendConfigActivity
import com.adoetz.gpt.utils.BackendConfigManager
import kotlinx.coroutines.launch

/**
 * Main Activity that hosts the OpenWebUI frontend in a WebView
 * Implements persistent WebView to prevent reloads on minimize
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: BackendConfigManager

    private var webView: WebView? = null
    private var currentBackendConfig: BackendConfig? = null
    private var isWebViewInitialized = false

    // Voice session service connection
    private var voiceServiceBinder: VoiceSessionService.LocalBinder? = null
    private var isVoiceServiceBound = false

    private val backendConfigLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val shouldClearWebViewData = result.data?.getBooleanExtra("clear_webview_data", false) ?: false
                if (shouldClearWebViewData) {
                    clearWebViewData()
                }
                // Reload config and refresh WebView
                loadBackendConfig()
                if (currentBackendConfig?.isValid() == true) {
                    loadUrlInWebView(currentBackendConfig!!.getCleanBaseUrl())
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = BackendConfigManager(this)

        setupViews()
        loadBackendConfig()
    }

    private fun setupViews() {
        binding.btnConfigureBackend.setOnClickListener {
            openBackendConfig()
        }

        binding.fabMenu.setOnClickListener {
            showMenu()
        }

        // Restore WebView if saved
        if (savedInstanceState != null) {
            restoreWebView(savedInstanceState)
        }
    }

    private fun loadBackendConfig() {
        lifecycleScope.launch {
            configManager.backendConfigFlow.collect { config ->
                currentBackendConfig = config

                if (config.isValid()) {
                    // Backend configured - initialize WebView
                    binding.noBackendOverlay.isVisible = false
                    if (!isWebViewInitialized) {
                        initializeWebView()
                        loadUrlInWebView(config.getCleanBaseUrl())
                    }
                } else {
                    // No backend configured - show configuration UI
                    binding.noBackendOverlay.isVisible = true
                    binding.webViewContainer.isVisible = false
                }
            }
        }
    }

    /**
     * Initialize WebView with proper settings
     */
    private fun initializeWebView() {
        if (isWebViewInitialized) return

        webView = binding.webView.apply {
            settings.apply {
                // Web settings
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Enable responsive design
                useWideViewPort = true
                loadWithOverviewMode = true

                // Zoom settings
                setSupportZoom(true)
                builtInZoomControls = false
                displayZoomControls = false

                // Text rendering
                textZoom = 100

                // Performance
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                enableSmoothTransition()

                // File access
                allowFileAccess = true
                allowContentAccess = true

                // Third-party cookies
                cookieManager = CookieManager.getInstance().apply {
                    setAcceptThirdPartyCookies(this@apply, true)
                    setAcceptCookie(true)
                }

                // Audio autoplay
                setMediaPlaybackRequiresUserGesture(false)
            }

            // WebViewClient - handles page loading and errors
            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    showLoading(true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    showLoading(false)
                    isWebViewInitialized = true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        showLoading(false)
                        // Show connection error in WebView
                        // The frontend will handle its own error display
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // Add custom headers if API key is configured
                    return if (currentBackendConfig?.apiKey != null) {
                        // Note: This is a simplified example
                        // In production, you'd want to add headers selectively
                        super.shouldInterceptRequest(view, request)
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }
            }

            // WebChromeClient - handles JavaScript dialogs, progress, etc.
            webChromeClient = object : WebChromeClient() {

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    // Can update progress bar here if needed
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Grant permissions for microphone, camera, etc.
                    request?.grant(request.resources)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        android.util.Log.d(
                            "WebViewConsole",
                            "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                        )
                    }
                    return true
                }
            }

            // Add JavaScript Interface for native communication
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidNative")
        }

        isWebViewInitialized = true
    }

    /**
     * Load URL in WebView
     */
    private fun loadUrlInWebView(url: String) {
        webView?.loadUrl(url)
    }

    /**
     * Show/hide loading overlay
     */
    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.isVisible = show
        if (show) {
            binding.tvLoadingText.text = getString(R.string.connecting_to_backend)
        }
    }

    /**
     * Open backend configuration activity
     */
    private fun openBackendConfig() {
        val intent = Intent(this, BackendConfigActivity::class.java)
        backendConfigLauncher.launch(intent)
    }

    /**
     * Show menu options
     */
    private fun showMenu() {
        // Simple menu implementation - can be expanded to show popup menu
        openBackendConfig()
    }

    /**
     * Clear all WebView data
     */
    private fun clearWebViewData() {
        webView?.apply {
            CookieManager.getInstance().removeAllCookies(null)
            clearCache(true)
            clearFormData()
            clearHistory()
        }

        // Also clear WebStorage
        WebStorage.getInstance().deleteAllData()

        isWebViewInitialized = false
    }

    /**
     * Restore WebView from saved instance state
     */
    private fun restoreWebView(savedInstanceState: Bundle) {
        val webViewState = savedInstanceState.getBundle("webViewState")
        if (webViewState != null) {
            initializeWebView()
            webView?.restoreState(webViewState)
        }
    }

    /**
     * Handle voice session state changes from service
     */
    private fun onVoiceSessionStateChanged(state: VoiceSessionState) {
        when (state) {
            VoiceSessionState.IDLE -> {
                binding.voiceSessionIndicator.isVisible = false
            }
            VoiceSessionState.LISTENING,
            VoiceSessionState.PROCESSING,
            VoiceSessionState.SPEAKING -> {
                binding.voiceSessionIndicator.isVisible = true
                startVoiceIndicatorAnimation()
            }
            VoiceSessionState.ERROR -> {
                binding.voiceSessionIndicator.isVisible = true
                stopVoiceIndicatorAnimation()
            }
            else -> {
                binding.voiceSessionIndicator.isVisible = false
            }
        }
    }

    /**
     * Start voice indicator animation
     */
    private fun startVoiceIndicatorAnimation() {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                binding.voiceSessionIndicator.alpha = 0.3f + (0.7f * value)
            }
        }
        animator.start()
    }

    private fun stopVoiceIndicatorAnimation() {
        binding.voiceSessionIndicator.alpha = 1f
    }

    // ==================== Activity Lifecycle ====================

    /**
     * CRITICAL: Don't destroy WebView on pause - just pause it
     * This prevents the WebView from being destroyed when app is minimized
     */
    override fun onPause() {
        super.onPause()

        webView?.onPause()
        webView?.pauseTimers()

        // Pause video/audio if playing
        // Note: We keep the WebView alive for voice sessions
    }

    /**
     * CRITICAL: Resume WebView when app comes back to foreground
     */
    override fun onResume() {
        super.onResume()

        webView?.onResume()
        webView?.resumeTimers()
    }

    /**
     * Save WebView state before potential destruction
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        webView?.let { wv ->
            val webViewState = Bundle()
            wv.saveState(webViewState)
            outState.putBundle("webViewState", webViewState)
        }
    }

    /**
     * Restore state after process death
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreWebView(savedInstanceState)
    }

    /**
     * CRITICAL: Handle configuration changes without destroying activity
     * This is handled by android:configChanges in manifest
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // WebView is automatically preserved - no need to do anything
    }

    /**
     * Don't destroy WebView in onBackPressed - let WebView handle navigation
     */
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Destroy WebView only when activity is truly being destroyed
        // Not called when app is minimized
        webView?.destroy()
        webView = null

        // Unbind from voice service
        if (isVoiceServiceBound) {
            unbindService(serviceConnection)
            isVoiceServiceBound = false
        }
    }

    // ==================== Service Connection ====================

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            voiceServiceBinder = (service as? VoiceSessionService.LocalBinder)
            isVoiceServiceBound = true

            // Observe voice session state
            observeVoiceSessionState()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            voiceServiceBinder = null
            isVoiceServiceBound = false
        }
    }

    private fun observeVoiceSessionState() {
        lifecycleScope.launch {
            voiceServiceBinder?.getService()?.sessionState?.collect { state ->
                onVoiceSessionStateChanged(state.state)
            }
        }
    }

    // ==================== JavaScript Interface ====================

    /**
     * JavaScript Interface for communication between WebView and native code
     */
    inner class WebAppInterface(private val context: Context) {

        @JavascriptInterface
        fun startVoiceSession(sessionId: String?) {
            VoiceSessionService.startSession(context, sessionId)
        }

        @JavascriptInterface
        fun stopVoiceSession() {
            VoiceSessionService.stopSession(context)
        }

        @JavascriptInterface
        fun isVoiceSessionActive(): Boolean {
            return voiceServiceBinder?.getService()?.isSessionActive() ?: false
        }

        @JavascriptInterface
        fun getBackendUrl(): String {
            return currentBackendConfig?.getCleanBaseUrl() ?: ""
        }

        @JavascriptInterface
        fun getApiKey(): String? {
            return currentBackendConfig?.apiKey
        }

        @JavascriptInterface
        fun openSettings() {
            openBackendConfig()
        }

        @JavascriptInterface
        fun showToast(message: String) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

+++ app/src/main/java/com/adoetz/gpt/MainActivity.kt (修改后)
package com.adoetz.gpt

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.adoetz.gpt.databinding.ActivityMainBinding
import com.adoetz.gpt.models.BackendConfig
import com.adoetz.gpt.models.VoiceSessionState
import com.adoetz.gpt.service.VoiceSessionService
import com.adoetz.gpt.ui.BackendConfigActivity
import com.adoetz.gpt.utils.BackendConfigManager
import kotlinx.coroutines.launch

/**
 * Main Activity that hosts the OpenWebUI frontend in a WebView
 * Implements persistent WebView to prevent reloads on minimize
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: BackendConfigManager

    private var webView: WebView? = null
    private var currentBackendConfig: BackendConfig? = null
    private var isWebViewInitialized = false

    // Voice session service connection
    private var voiceServiceBinder: VoiceSessionService.LocalBinder? = null
    private var isVoiceServiceBound = false

    private val backendConfigLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val shouldClearWebViewData = result.data?.getBooleanExtra("clear_webview_data", false) ?: false
                if (shouldClearWebViewData) {
                    clearWebViewData()
                }
                // Reload config and refresh WebView
                loadBackendConfig()
                if (currentBackendConfig?.isValid() == true) {
                    loadUrlInWebView(currentBackendConfig!!.getCleanBaseUrl())
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = BackendConfigManager(this)

        setupViews()
        loadBackendConfig()

        // Bind to voice session service
        bindVoiceService()
    }

    private fun setupViews() {
        binding.btnConfigureBackend.setOnClickListener {
            openBackendConfig()
        }

        binding.fabMenu.setOnClickListener {
            showMenu()
        }

        // Restore WebView if saved
        if (savedInstanceState != null) {
            restoreWebView(savedInstanceState)
        }
    }

    private fun loadBackendConfig() {
        lifecycleScope.launch {
            configManager.backendConfigFlow.collect { config ->
                currentBackendConfig = config

                if (config.isValid()) {
                    // Backend configured - initialize WebView
                    binding.noBackendOverlay.isVisible = false
                    if (!isWebViewInitialized) {
                        initializeWebView()
                        loadUrlInWebView(config.getCleanBaseUrl())
                    }
                } else {
                    // No backend configured - show configuration UI
                    binding.noBackendOverlay.isVisible = true
                    binding.webViewContainer.isVisible = false
                }
            }
        }
    }

    /**
     * Initialize WebView with proper settings
     */
    private fun initializeWebView() {
        if (isWebViewInitialized) return

        webView = binding.webView.apply {
            settings.apply {
                // Web settings
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Enable responsive design
                useWideViewPort = true
                loadWithOverviewMode = true

                // Zoom settings
                setSupportZoom(true)
                builtInZoomControls = false
                displayZoomControls = false

                // Text rendering
                textZoom = 100

                // Performance
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                enableSmoothTransition()

                // File access
                allowFileAccess = true
                allowContentAccess = true

                // Third-party cookies
                cookieManager = CookieManager.getInstance().apply {
                    setAcceptThirdPartyCookies(this@apply, true)
                    setAcceptCookie(true)
                }

                // Audio autoplay
                setMediaPlaybackRequiresUserGesture(false)
            }

            // WebViewClient - handles page loading and errors
            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    showLoading(true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    showLoading(false)
                    isWebViewInitialized = true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        showLoading(false)
                        // Show connection error in WebView
                        // The frontend will handle its own error display
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // Add custom headers if API key is configured
                    return if (currentBackendConfig?.apiKey != null) {
                        // Note: This is a simplified example
                        // In production, you'd want to add headers selectively
                        super.shouldInterceptRequest(view, request)
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }
            }

            // WebChromeClient - handles JavaScript dialogs, progress, etc.
            webChromeClient = object : WebChromeClient() {

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    // Can update progress bar here if needed
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Grant permissions for microphone, camera, etc.
                    request?.grant(request.resources)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        android.util.Log.d(
                            "WebViewConsole",
                            "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                        )
                    }
                    return true
                }
            }

            // Add JavaScript Interface for native communication
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidNative")
        }

        isWebViewInitialized = true
    }

    /**
     * Load URL in WebView
     */
    private fun loadUrlInWebView(url: String) {
        webView?.loadUrl(url)
    }

    /**
     * Show/hide loading overlay
     */
    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.isVisible = show
        if (show) {
            binding.tvLoadingText.text = getString(R.string.connecting_to_backend)
        }
    }

    /**
     * Open backend configuration activity
     */
    private fun openBackendConfig() {
        val intent = Intent(this, BackendConfigActivity::class.java)
        backendConfigLauncher.launch(intent)
    }

    /**
     * Show menu options
     */
    private fun showMenu() {
        // Simple menu implementation - can be expanded to show popup menu
        openBackendConfig()
    }

    /**
     * Clear all WebView data
     */
    private fun clearWebViewData() {
        webView?.apply {
            CookieManager.getInstance().removeAllCookies(null)
            clearCache(true)
            clearFormData()
            clearHistory()
        }

        // Also clear WebStorage
        WebStorage.getInstance().deleteAllData()

        isWebViewInitialized = false
    }

    /**
     * Restore WebView from saved instance state
     */
    private fun restoreWebView(savedInstanceState: Bundle) {
        val webViewState = savedInstanceState.getBundle("webViewState")
        if (webViewState != null) {
            initializeWebView()
            webView?.restoreState(webViewState)
        }
    }

    /**
     * Handle voice session state changes from service
     */
    private fun onVoiceSessionStateChanged(state: VoiceSessionState) {
        when (state) {
            VoiceSessionState.IDLE -> {
                binding.voiceSessionIndicator.isVisible = false
            }
            VoiceSessionState.LISTENING,
            VoiceSessionState.PROCESSING,
            VoiceSessionState.SPEAKING -> {
                binding.voiceSessionIndicator.isVisible = true
                startVoiceIndicatorAnimation()
            }
            VoiceSessionState.ERROR -> {
                binding.voiceSessionIndicator.isVisible = true
                stopVoiceIndicatorAnimation()
            }
            else -> {
                binding.voiceSessionIndicator.isVisible = false
            }
        }
    }

    /**
     * Start voice indicator animation
     */
    private fun startVoiceIndicatorAnimation() {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                binding.voiceSessionIndicator.alpha = 0.3f + (0.7f * value)
            }
        }
        animator.start()
    }

    private fun stopVoiceIndicatorAnimation() {
        binding.voiceSessionIndicator.alpha = 1f
    }

    // ==================== Activity Lifecycle ====================

    /**
     * CRITICAL: Don't destroy WebView on pause - just pause it
     * This prevents the WebView from being destroyed when app is minimized
     */
    override fun onPause() {
        super.onPause()

        webView?.onPause()
        webView?.pauseTimers()

        // Pause video/audio if playing
        // Note: We keep the WebView alive for voice sessions
    }

    /**
     * CRITICAL: Resume WebView when app comes back to foreground
     */
    override fun onResume() {
        super.onResume()

        webView?.onResume()
        webView?.resumeTimers()
    }

    /**
     * Save WebView state before potential destruction
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        webView?.let { wv ->
            val webViewState = Bundle()
            wv.saveState(webViewState)
            outState.putBundle("webViewState", webViewState)
        }
    }

    /**
     * Restore state after process death
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreWebView(savedInstanceState)
    }

    /**
     * CRITICAL: Handle configuration changes without destroying activity
     * This is handled by android:configChanges in manifest
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // WebView is automatically preserved - no need to do anything
    }

    /**
     * Don't destroy WebView in onBackPressed - let WebView handle navigation
     */
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Destroy WebView only when activity is truly being destroyed
        // Not called when app is minimized
        webView?.destroy()
        webView = null

        // Unbind from voice service safely
        if (isVoiceServiceBound && ::serviceConnection.isInitialized) {
            unbindService(serviceConnection)
            isVoiceServiceBound = false
        }
    }

    // ==================== Service Connection ====================

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            voiceServiceBinder = (service as? VoiceSessionService.LocalBinder)
            isVoiceServiceBound = true

            // Set WebView callback for service-to-UI communication
            voiceServiceBinder?.getService()?.setWebViewCallback(object : VoiceSessionService.WebViewCallback {
                override fun onVoiceSessionStateChanged(state: VoiceSessionState) {
                    runOnUiThread {
                        onVoiceSessionStateChanged(state)
                    }
                }

                override fun onMicrophonePermissionResult(granted: Boolean) {
                    runOnUiThread {
                        if (!granted) {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                getString(R.string.microphone_permission_required),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            })

            // Observe voice session state
            observeVoiceSessionState()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            voiceServiceBinder = null
            isVoiceServiceBound = false
            // Clear WebView callback
            voiceServiceBinder?.getService()?.setWebViewCallback(null)
        }
    }

    private fun bindVoiceService() {
        val intent = android.content.Intent(this, VoiceSessionService::class.java)
        bindService(intent, serviceConnection, android.content.Context.BIND_AUTO_CREATE)
    }

    private fun observeVoiceSessionState() {
        lifecycleScope.launch {
            voiceServiceBinder?.getService()?.sessionState?.collect { state ->
                onVoiceSessionStateChanged(state.state)
            }
        }
    }

    // ==================== JavaScript Interface ====================

    /**
     * JavaScript Interface for communication between WebView and native code
     */
    inner class WebAppInterface(private val context: Context) {

        @JavascriptInterface
        fun startVoiceSession(sessionId: String?) {
            VoiceSessionService.startSession(context, sessionId)
        }

        @JavascriptInterface
        fun stopVoiceSession() {
            VoiceSessionService.stopSession(context)
        }

        @JavascriptInterface
        fun isVoiceSessionActive(): Boolean {
            return voiceServiceBinder?.getService()?.isSessionActive() ?: false
        }

        @JavascriptInterface
        fun getBackendUrl(): String {
            return currentBackendConfig?.getCleanBaseUrl() ?: ""
        }

        @JavascriptInterface
        fun getApiKey(): String? {
            return currentBackendConfig?.apiKey
        }

        @JavascriptInterface
        fun openSettings() {
            openBackendConfig()
        }

        @JavascriptInterface
        fun showToast(message: String) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

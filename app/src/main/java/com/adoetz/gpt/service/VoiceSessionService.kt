--- app/src/main/java/com/adoetz/gpt/service/VoiceSessionService.kt (原始)
package com.adoetz.gpt.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.adoetz.gpt.models.VoiceSessionData
import com.adoetz.gpt.models.VoiceSessionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that maintains voice session state
 * Keeps microphone and audio alive even when app is backgrounded
 */
class VoiceSessionService : Service() {

    companion object {
        private const val TAG = "VoiceSessionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_session_channel"
        private const val ACTION_START = "com.adoetz.gpt.action.START_SESSION"
        private const val ACTION_STOP = "com.adoetz.gpt.action.STOP_SESSION"
        private const val ACTION_UPDATE_STATE = "com.adoetz.gpt.action.UPDATE_STATE"
        private const val EXTRA_STATE = "extra_state"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_ERROR_MESSAGE = "extra_error_message"

        fun startSession(context: Context, sessionId: String? = null) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun stopSession(context: Context) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateSessionState(
            context: Context,
            state: VoiceSessionState,
            sessionId: String? = null,
            errorMessage: String? = null
        ) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_STATE, state.name)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val binder = LocalBinder()

    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var audioManager: AudioManager

    private val _sessionState = MutableStateFlow(VoiceSessionData())
    val sessionState: StateFlow<VoiceSessionData> = _sessionState.asStateFlow()

    private var isForeground = false

    // WebView communication
    private var webViewCallback: WebViewCallback? = null

    interface WebViewCallback {
        fun onVoiceSessionStateChanged(state: VoiceSessionState)
        fun onMicrophonePermissionResult(granted: Boolean)
    }

    inner class LocalBinder : Binder() {
        fun getService(): VoiceSessionService = this@VoiceSessionService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeWakeLock()
        audioManager = AudioManager(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                startVoiceSession(sessionId)
            }
            ACTION_STOP -> {
                stopVoiceSession()
            }
            ACTION_UPDATE_STATE -> {
                val stateName = intent.getStringExtra(EXTRA_STATE)
                val state = VoiceSessionState.valueOf(stateName ?: VoiceSessionState.IDLE.name)
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
                updateState(state, sessionId, errorMessage)
            }
        }

        return START_STICKY // Service will be restarted if killed
    }

    /**
     * Start a voice session - acquires wake lock and shows notification
     */
    private fun startVoiceSession(sessionId: String?) {
        updateState(
            VoiceSessionState.STARTING,
            sessionId,
            null
        )

        // Acquire wake lock to keep CPU running
        acquireWakeLock()

        // Start foreground with notification
        if (!isForeground) {
            val notification = createNotification(_sessionState.value)
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        }

        // Update state to listening
        updateState(
            VoiceSessionState.LISTENING,
            sessionId,
            null
        )

        webViewCallback?.onVoiceSessionStateChanged(VoiceSessionState.LISTENING)
    }

    /**
     * Stop the voice session - releases wake lock and removes notification
     */
    private fun stopVoiceSession() {
        updateState(VoiceSessionState.STOPPING, null, null)

        // Release wake lock
        releaseWakeLock()

        // Remove notification and stop foreground
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isForeground = false
        }

        // Reset state
        _sessionState.value = VoiceSessionData()
        webViewCallback?.onVoiceSessionStateChanged(VoiceSessionState.IDLE)
    }

    /**
     * Update the current session state
     */
    private fun updateState(
        state: VoiceSessionState,
        sessionId: String?,
        errorMessage: String?
    ) {
        _sessionState.value = _sessionState.value.copy(
            state = state,
            sessionId = sessionId ?: _sessionState.value.sessionId,
            lastActivityTime = System.currentTimeMillis(),
            errorMessage = errorMessage
        )

        // Update notification if foreground
        if (isForeground) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(_sessionState.value))
        }

        webViewCallback?.onVoiceSessionStateChanged(state)
    }

    /**
     * Set the WebView callback for communication with the frontend
     */
    fun setWebViewCallback(callback: WebViewCallback?) {
        this.webViewCallback = callback
    }

    /**
     * Get the current session data
     */
    fun getCurrentSession(): VoiceSessionData {
        return _sessionState.value
    }

    /**
     * Check if a session is currently active
     */
    fun isSessionActive(): Boolean {
        return _sessionState.value.isActive()
    }

    /**
     * Create the notification channel for API 26+
     */
    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_voice),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_voice_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the foreground notification
     */
    private fun createNotification(sessionData: VoiceSessionData): Notification {
        val stateText = sessionData.getStateDescription()
        val contentText = when (sessionData.state) {
            VoiceSessionState.LISTENING -> getString(R.string.listening)
            VoiceSessionState.PROCESSING -> getString(R.string.processing)
            VoiceSessionState.SPEAKING -> getString(R.string.speaking)
            else -> getString(R.string.tap_to_stop)
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_session_active))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.tap_to_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Initialize partial wake lock to keep CPU running
     */
    private fun initializeWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "adoetzgpt:voice_session_wake_lock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    /**
     * Acquire wake lock
     */
    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(30 * 60 * 1000L) // 30 minutes max
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }
}

+++ app/src/main/java/com/adoetz/gpt/service/VoiceSessionService.kt (修改后)
package com.adoetz.gpt.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.adoetz.gpt.models.VoiceSessionData
import com.adoetz.gpt.models.VoiceSessionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that maintains voice session state
 * Keeps microphone and audio alive even when app is backgrounded
 */
class VoiceSessionService : Service() {

    companion object {
        private const val TAG = "VoiceSessionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_session_channel"
        private const val ACTION_START = "com.adoetz.gpt.action.START_SESSION"
        private const val ACTION_STOP = "com.adoetz.gpt.action.STOP_SESSION"
        private const val ACTION_UPDATE_STATE = "com.adoetz.gpt.action.UPDATE_STATE"
        private const val EXTRA_STATE = "extra_state"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_ERROR_MESSAGE = "extra_error_message"

        fun startSession(context: Context, sessionId: String? = null) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun stopSession(context: Context) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateSessionState(
            context: Context,
            state: VoiceSessionState,
            sessionId: String? = null,
            errorMessage: String? = null
        ) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_STATE, state.name)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val binder = LocalBinder()

    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var audioManager: AudioManager

    private val _sessionState = MutableStateFlow(VoiceSessionData())
    val sessionState: StateFlow<VoiceSessionData> = _sessionState.asStateFlow()

    private var isForeground = false

    // WebView communication
    private var webViewCallback: WebViewCallback? = null

    interface WebViewCallback {
        fun onVoiceSessionStateChanged(state: VoiceSessionState)
        fun onMicrophonePermissionResult(granted: Boolean)
    }

    inner class LocalBinder : Binder() {
        fun getService(): VoiceSessionService = this@VoiceSessionService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeWakeLock()
        audioManager = AudioManager(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                startVoiceSession(sessionId)
            }
            ACTION_STOP -> {
                stopVoiceSession()
            }
            ACTION_UPDATE_STATE -> {
                val stateName = intent.getStringExtra(EXTRA_STATE)
                val state = try {
                    VoiceSessionState.valueOf(stateName ?: VoiceSessionState.IDLE.name)
                } catch (e: IllegalArgumentException) {
                    VoiceSessionState.ERROR
                }
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
                updateState(state, sessionId, errorMessage)
            }
        }

        return START_STICKY // Service will be restarted if killed
    }

    /**
     * Start a voice session - acquires wake lock and shows notification
     */
    private fun startVoiceSession(sessionId: String?) {
        updateState(
            VoiceSessionState.STARTING,
            sessionId,
            null
        )

        // Acquire wake lock to keep CPU running
        acquireWakeLock()

        // Request audio focus for voice session
        audioManager.requestAudioFocus()

        // Start foreground with notification
        if (!isForeground) {
            val notification = createNotification(_sessionState.value)
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        }

        // Update state to listening
        updateState(
            VoiceSessionState.LISTENING,
            sessionId,
            null
        )

        webViewCallback?.onVoiceSessionStateChanged(VoiceSessionState.LISTENING)
    }

    /**
     * Stop the voice session - releases wake lock and removes notification
     */
    private fun stopVoiceSession() {
        updateState(VoiceSessionState.STOPPING, null, null)

        // Release audio focus
        audioManager.abandonAudioFocus()

        // Release wake lock
        releaseWakeLock()

        // Remove notification and stop foreground
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isForeground = false
        }

        // Reset state
        _sessionState.value = VoiceSessionData()
        webViewCallback?.onVoiceSessionStateChanged(VoiceSessionState.IDLE)
    }

    /**
     * Update the current session state
     */
    private fun updateState(
        state: VoiceSessionState,
        sessionId: String?,
        errorMessage: String?
    ) {
        _sessionState.value = _sessionState.value.copy(
            state = state,
            sessionId = sessionId ?: _sessionState.value.sessionId,
            lastActivityTime = System.currentTimeMillis(),
            errorMessage = errorMessage
        )

        // Update notification if foreground
        if (isForeground) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(_sessionState.value))
        }

        webViewCallback?.onVoiceSessionStateChanged(state)
    }

    /**
     * Set the WebView callback for communication with the frontend
     */
    fun setWebViewCallback(callback: WebViewCallback?) {
        this.webViewCallback = callback
    }

    /**
     * Get the current session data
     */
    fun getCurrentSession(): VoiceSessionData {
        return _sessionState.value
    }

    /**
     * Check if a session is currently active
     */
    fun isSessionActive(): Boolean {
        return _sessionState.value.isActive()
    }

    /**
     * Create the notification channel for API 26+
     */
    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_voice),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_voice_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the foreground notification
     */
    private fun createNotification(sessionData: VoiceSessionData): Notification {
        val stateText = sessionData.getStateDescription()
        val contentText = when (sessionData.state) {
            VoiceSessionState.LISTENING -> getString(R.string.listening)
            VoiceSessionState.PROCESSING -> getString(R.string.processing)
            VoiceSessionState.SPEAKING -> getString(R.string.speaking)
            else -> getString(R.string.tap_to_stop)
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_session_active))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.tap_to_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Initialize partial wake lock to keep CPU running
     */
    private fun initializeWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "adoetzgpt:voice_session_wake_lock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    /**
     * Acquire wake lock
     */
    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(30 * 60 * 1000L) // 30 minutes max
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }
}

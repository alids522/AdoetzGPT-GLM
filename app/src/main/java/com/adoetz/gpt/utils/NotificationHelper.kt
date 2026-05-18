package com.adoetz.gpt.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.adoetz.gpt.MainActivity
import com.adoetz.gpt.R
import com.adoetz.gpt.models.VoiceSessionData
import com.adoetz.gpt.models.VoiceSessionState
import com.adoetz.gpt.service.VoiceSessionService

/**
 * Helper class for creating and managing notifications
 */
class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID_VOICE = "voice_session_channel"
        private const val CHANNEL_ID_CONNECTION = "connection_channel"
        private const val NOTIFICATION_ID_VOICE = 1001
        private const val NOTIFICATION_ID_CONNECTION = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for API 26+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Voice session channel
            val voiceChannel = NotificationChannel(
                CHANNEL_ID_VOICE,
                context.getString(R.string.notification_channel_voice),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_voice_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lightColor = Color.parseColor("#6366F1")
            }

            // Connection channel
            val connectionChannel = NotificationChannel(
                CHANNEL_ID_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about backend connection status"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(voiceChannel)
            notificationManager.createNotificationChannel(connectionChannel)
        }
    }

    /**
     * Create voice session notification
     */
    fun createVoiceSessionNotification(sessionData: VoiceSessionData): Notification {
        val (title, contentText) = getVoiceSessionText(sessionData)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, VoiceSessionService::class.java).apply {
            action = VoiceSessionService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_VOICE)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(getIconForState(sessionData.state))
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.tap_to_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Set progress for processing state
        if (sessionData.state == VoiceSessionState.PROCESSING) {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    /**
     * Update voice session notification
     */
    fun updateVoiceSessionNotification(sessionData: VoiceSessionData) {
        val notification = createVoiceSessionNotification(sessionData)
        notificationManager.notify(NOTIFICATION_ID_VOICE, notification)
    }

    /**
     * Cancel voice session notification
     */
    fun cancelVoiceSessionNotification() {
        notificationManager.cancel(NOTIFICATION_ID_VOICE)
    }

    /**
     * Show connection status notification
     */
    fun showConnectionNotification(isConnected: Boolean, backendUrl: String?) {
        val title = if (isConnected) {
            context.getString(R.string.connection_success)
        } else {
            context.getString(R.string.connection_failed)
        }
        val content = if (isConnected) {
            "Connected to ${backendUrl ?: "backend"}"
        } else {
            "Failed to connect to ${backendUrl ?: "backend"}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CONNECTION)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(if (isConnected) R.drawable.ic_check else R.drawable.ic_error)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        notificationManager.notify(NOTIFICATION_ID_CONNECTION, builder.build())
    }

    /**
     * Get text for voice session based on state
     */
    private fun getVoiceSessionText(sessionData: VoiceSessionData): Pair<String, String> {
        val title = context.getString(R.string.voice_session_active)
        val content = when (sessionData.state) {
            VoiceSessionState.STARTING -> context.getString(R.string.processing)
            VoiceSessionState.LISTENING -> context.getString(R.string.listening)
            VoiceSessionState.PROCESSING -> context.getString(R.string.processing)
            VoiceSessionState.SPEAKING -> context.getString(R.string.speaking)
            VoiceSessionState.ERROR -> sessionData.errorMessage ?: "Error"
            VoiceSessionState.STOPPING, VoiceSessionState.IDLE -> context.getString(R.string.tap_to_stop)
        }
        return title to content
    }

    /**
     * Get icon resource for state
     */
    private fun getIconForState(state: VoiceSessionState): Int {
        return when (state) {
            VoiceSessionState.LISTENING -> R.drawable.ic_mic
            VoiceSessionState.PROCESSING -> R.drawable.ic_processing
            VoiceSessionState.SPEAKING -> R.drawable.ic_speaker
            VoiceSessionState.ERROR -> R.drawable.ic_error
            else -> R.drawable.ic_mic
        }
    }
}

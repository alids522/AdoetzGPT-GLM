package com.adoetz.gpt.models

import kotlinx.serialization.Serializable

/**
 * Voice session state model
 */
@Serializable
enum class VoiceSessionState {
    IDLE,
    STARTING,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
    STOPPING
}

/**
 * Voice session data model
 */
@Serializable
data class VoiceSessionData(
    val state: VoiceSessionState = VoiceSessionState.IDLE,
    val sessionId: String? = null,
    val startTime: Long = 0L,
    val lastActivityTime: Long = 0L,
    val errorMessage: String? = null,
    val isMicrophoneActive: Boolean = false,
    val isAudioPlaying: Boolean = false
) {
    companion object {
        const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    }

    /**
     * Check if the session is active
     */
    fun isActive(): Boolean {
        return state != VoiceSessionState.IDLE && state != VoiceSessionState.ERROR
    }

    /**
     * Check if the session has timed out
     */
    fun isTimedOut(): Boolean {
        return if (lastActivityTime > 0) {
            System.currentTimeMillis() - lastActivityTime > SESSION_TIMEOUT_MS
        } else {
            false
        }
    }

    /**
     * Get a human-readable state description
     */
    fun getStateDescription(): String {
        return when (state) {
            VoiceSessionState.IDLE -> "Idle"
            VoiceSessionState.STARTING -> "Starting..."
            VoiceSessionState.LISTENING -> "Listening..."
            VoiceSessionState.PROCESSING -> "Processing..."
            VoiceSessionState.SPEAKING -> "Speaking..."
            VoiceSessionState.ERROR -> errorMessage ?: "Error"
            VoiceSessionState.STOPPING -> "Stopping..."
        }
    }
}

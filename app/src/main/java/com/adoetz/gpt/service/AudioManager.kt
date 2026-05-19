package com.adoetz.gpt.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio focus for voice sessions
 * Handles audio focus requests and abandonment
 */
class AudioManager(private val context: Context) {

    private val systemAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioRecorder: AudioRecord? = null

    private val _hasAudioFocus = MutableStateFlow(false)
    val hasAudioFocus: StateFlow<Boolean> = _hasAudioFocus.asStateFlow()

    /**
     * Request audio focus for voice session
     */
    fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusApi26()
        } else {
            requestAudioFocusLegacy()
        }
    }

    /**
     * Request audio focus for API 26+
     */
    private fun requestAudioFocusApi26(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .build()

        val result = systemAudioManager.requestAudioFocus(audioFocusRequest!!)
        _hasAudioFocus.value = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return _hasAudioFocus.value
    }

    /**
     * Request audio focus for legacy APIs
     */
    @Suppress("DEPRECATION")
    private fun requestAudioFocusLegacy(): Boolean {
        val result = systemAudioManager.requestAudioFocus(
            { focusChange -> handleAudioFocusChange(focusChange) },
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN
        )
        _hasAudioFocus.value = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return _hasAudioFocus.value
    }

    /**
     * Abandon audio focus
     */
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                systemAudioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            systemAudioManager.abandonAudioFocus { }
        }
        _hasAudioFocus.value = false
    }

    /**
     * Handle audio focus changes
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                _hasAudioFocus.value = true
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                _hasAudioFocus.value = false
                // Permanent loss - abandon focus
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                _hasAudioFocus.value = false
                // Temporary loss - pause if needed
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck audio (lower volume) if needed
            }
        }
    }

    /**
     * Get the current volume for voice call stream
     */
    fun getCurrentVolume(): Int {
        return systemAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    }

    /**
     * Get the max volume for voice call stream
     */
    fun getMaxVolume(): Int {
        return systemAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
    }

    /**
     * Set the volume for voice call stream
     */
    fun setVolume(volume: Int) {
        val maxVolume = getMaxVolume()
        val normalizedVolume = volume.coerceIn(0, maxVolume) / maxVolume.toFloat()
        systemAudioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            volume,
            AudioManager.FLAG_SHOW_UI
        )
    }

    /**
     * Initialize audio recording
     */
    fun initializeRecording(): Boolean {
        return try {
            val sampleRate = 16000 // Standard sample rate for voice
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecorder?.state == AudioRecord.STATE_INITIALIZED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Start audio recording
     */
    fun startRecording(): Boolean {
        return try {
            audioRecorder?.startRecording()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Stop audio recording
     */
    fun stopRecording() {
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
    }
}

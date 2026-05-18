package com.adoetz.gpt.models

import kotlinx.serialization.Serializable

/**
 * Data model representing the backend configuration
 */
@Serializable
data class BackendConfig(
    val baseUrl: String = "",
    val apiKey: String? = null,
    val lastConnected: Long = 0L,
    val customHeaders: Map<String, String> = emptyMap(),
    val isConnected: Boolean = false
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:3000"
    }

    /**
     * Check if the configuration is valid
     */
    fun isValid(): Boolean {
        return baseUrl.isNotBlank() && isValidUrl(baseUrl)
    }

    /**
     * Check if a URL is valid
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol == "http" || urlObj.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the full WebSocket URL for the backend
     */
    fun getWebSocketUrl(): String {
        return when {
            baseUrl.startsWith("https://") -> baseUrl.replace("https://", "wss://")
            baseUrl.startsWith("http://") -> baseUrl.replace("http://", "ws://")
            else -> "ws://$baseUrl"
        }
    }

    /**
     * Get the base URL without trailing slash
     */
    fun getCleanBaseUrl(): String {
        return baseUrl.trimEnd('/')
    }
}

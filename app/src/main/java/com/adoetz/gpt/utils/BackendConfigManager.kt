package com.adoetz.gpt.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adoetz.gpt.models.BackendConfig
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manager for backend configuration using DataStore
 */
class BackendConfigManager(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val Context.backendConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "backend_config")

        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val API_KEY_KEY = stringPreferencesKey("api_key")
        private val LAST_CONNECTED_KEY = stringPreferencesKey("last_connected")
        private val IS_CONNECTED_KEY = booleanPreferencesKey("is_connected")
        private val CUSTOM_HEADERS_KEY = stringPreferencesKey("custom_headers")
    }

    /**
     * Get the backend configuration flow
     */
    val backendConfigFlow: Flow<BackendConfig> = context.backendConfigDataStore.data.map { preferences ->
        BackendConfig(
            baseUrl = preferences[BASE_URL_KEY] ?: "",
            apiKey = preferences[API_KEY_KEY],
            lastConnected = preferences[LAST_CONNECTED_KEY]?.toLongOrNull() ?: 0L,
            customHeaders = parseCustomHeaders(preferences[CUSTOM_HEADERS_KEY]),
            isConnected = preferences[IS_CONNECTED_KEY] ?: false
        )
    }

    /**
     * Get the current backend configuration (synchronous)
     */
    suspend fun getBackendConfig(): BackendConfig {
        var result = BackendConfig()
        backendConfigFlow.collect { config ->
            result = config
            return@collect
        }
        return result
    }

    /**
     * Save backend configuration
     */
    suspend fun saveBackendConfig(config: BackendConfig) {
        context.backendConfigDataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = config.baseUrl
            preferences[API_KEY_KEY] = config.apiKey ?: ""
            preferences[LAST_CONNECTED_KEY] = config.lastConnected.toString()
            preferences[IS_CONNECTED_KEY] = config.isConnected
            preferences[CUSTOM_HEADERS_KEY] = gson.toJson(config.customHeaders)
        }
    }

    /**
     * Update base URL
     */
    suspend fun updateBaseUrl(url: String) {
        context.backendConfigDataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = url
        }
    }

    /**
     * Update API key
     */
    suspend fun updateApiKey(apiKey: String?) {
        context.backendConfigDataStore.edit { preferences ->
            preferences[API_KEY_KEY] = apiKey ?: ""
        }
    }

    /**
     * Update connection status
     */
    suspend fun updateConnectionStatus(isConnected: Boolean) {
        context.backendConfigDataStore.edit { preferences ->
            preferences[IS_CONNECTED_KEY] = isConnected
            preferences[LAST_CONNECTED_KEY] = System.currentTimeMillis().toString()
        }
    }

    /**
     * Clear all backend configuration
     */
    suspend fun clearBackendConfig() {
        context.backendConfigDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Check if backend is configured
     */
    suspend fun isBackendConfigured(): Boolean {
        var config = BackendConfig()
        backendConfigFlow.collect { cfg ->
            config = cfg
            return@collect
        }
        return config.isValid()
    }

    /**
     * Parse custom headers from JSON string
     */
    private fun parseCustomHeaders(json: String?): Map<String, String> {
        return if (json.isNullOrBlank()) {
            emptyMap()
        } else {
            try {
                gson.fromJson(json, Map::class.java) as? Map<String, String> ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * Legacy SharedPreferences migration
     */
    private fun migrateFromSharedPreferences(context: Context) {
        val prefs = context.getSharedPreferences("backend_config", Context.MODE_PRIVATE)
        if (prefs.contains("base_url")) {
            // Migration happens here on first run
            prefs.edit().clear().apply()
        }
    }
}

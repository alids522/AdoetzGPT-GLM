package com.adoetz.gpt.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adoetz.gpt.models.BackendConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for BackendConfigManager
 */
@RunWith(AndroidJUnit4::class)
class BackendConfigManagerTest {

    @Test
    fun testSaveBackendConfig() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<androidx.test.platform.app.InstrumentationRegistry>()
        val manager = BackendConfigManager(context)
        
        val config = BackendConfig(
            baseUrl = "https://test.com",
            apiKey = "test-key",
            lastConnected = System.currentTimeMillis(),
            isConnected = true
        )
        
        // Save the config
        manager.saveBackendConfig(config)
        
        // Verify the config was saved
        val savedConfig = manager.getBackendConfig()
        assertEquals(config.baseUrl, savedConfig.baseUrl)
        assertEquals(config.apiKey, savedConfig.apiKey)
        assertEquals(config.isConnected, savedConfig.isConnected)
    }
}
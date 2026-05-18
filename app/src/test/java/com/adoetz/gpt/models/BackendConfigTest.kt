package com.adoetz.gpt.utils

import com.adoetz.gpt.models.BackendConfig
import org.junit.Test
import org.junit.Assert.*
import java.net.URL

/**
 * Unit tests for BackendConfig and related utilities
 */
class BackendConfigTest {

    @Test
    fun testBackendConfigValidation() {
        // Test valid URL
        val validConfig = BackendConfig(
            baseUrl = "https://test.com",
            apiKey = "test-key"
        )
        assertTrue(validConfig.isValid())
        
        // Test invalid URL
        val invalidConfig = BackendConfig()
        assertFalse(invalidConfig.isValid())
    }
    
    @Test
    fun testGetWebSocketUrl() {
        val config = BackendConfig(baseUrl = "https://test.com")
        assertEquals("wss://test.com", config.getWebSocketUrl())
    }
}
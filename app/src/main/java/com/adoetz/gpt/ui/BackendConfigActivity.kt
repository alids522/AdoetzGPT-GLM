package com.adoetz.gpt.ui

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adoetz.gpt.R
import com.adoetz.gpt.databinding.ActivityBackendConfigBinding
import com.adoetz.gpt.models.BackendConfig
import com.adoetz.gpt.utils.BackendConfigManager
import com.adoetz.gpt.utils.validateUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for configuring the backend connection
 */
class BackendConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackendConfigBinding
    private lateinit var configManager: BackendConfigManager

    private var currentConfig: BackendConfig = BackendConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackendConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupViews()
        loadCurrentConfig()
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.backend_settings)
    }

    private fun setupViews() {
        configManager = BackendConfigManager(this)

        // Backend URL input
        binding.etBackendUrl.setInputType(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )

        // API Key input (password type for security)
        binding.etApiKey.setInputType(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        // Save button
        binding.btnSave.setOnClickListener {
            saveConfig()
        }

        // Clear button
        binding.btnClear.setOnClickListener {
            clearConfig()
        }

        // Test connection button
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        // Clear WebView data checkbox
        binding.cbClearWebViewData.setOnCheckedChangeListener { _, _ ->
            // Show warning when checked
            if (binding.cbClearWebViewData.isChecked) {
                binding.tvClearWarning.visibility = View.VISIBLE
            } else {
                binding.tvClearWarning.visibility = View.GONE
            }
        }
    }

    private fun loadCurrentConfig() {
        lifecycleScope.launch {
            configManager.backendConfigFlow.collect { config ->
                currentConfig = config
                binding.etBackendUrl.setText(config.baseUrl)
                binding.etApiKey.setText(config.apiKey ?: "")
                updateConnectionStatus(config.isConnected)
            }
        }
    }

    private fun saveConfig() {
        val backendUrl = binding.etBackendUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim().takeIf { it.isNotBlank() }

        // Validate URL
        if (backendUrl.isBlank()) {
            binding.tilBackendUrl.error = getString(R.string.backend_url_required)
            return
        }

        if (!validateUrl(backendUrl)) {
            binding.tilBackendUrl.error = getString(R.string.invalid_url)
            return
        }

        binding.tilBackendUrl.error = null

        // Show loading
        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // Clear WebView data if requested
        if (binding.cbClearWebViewData.isChecked) {
            clearWebViewData()
        }

        // Save configuration
        lifecycleScope.launch {
            val newConfig = BackendConfig(
                baseUrl = backendUrl,
                apiKey = apiKey,
                lastConnected = System.currentTimeMillis(),
                isConnected = false
            )

            configManager.saveBackendConfig(newConfig)

            // Update UI
            binding.btnSave.isEnabled = true
            binding.progressBar.visibility = View.GONE

            Toast.makeText(
                this@BackendConfigActivity,
                getString(R.string.backend_saved),
                Toast.LENGTH_SHORT
            ).show()

            // Finish and return to main
            finish()
        }
    }

    private fun clearConfig() {
        lifecycleScope.launch {
            configManager.clearBackendConfig()

            // Clear WebView data
            clearWebViewData()

            // Clear inputs
            binding.etBackendUrl.text?.clear()
            binding.etApiKey.text?.clear()

            Toast.makeText(
                this@BackendConfigActivity,
                getString(R.string.backend_cleared),
                Toast.LENGTH_SHORT
            ).show()

            // Finish and return to main
            finish()
        }
    }

    private fun testConnection() {
        val backendUrl = binding.etBackendUrl.text.toString().trim()

        if (backendUrl.isBlank()) {
            binding.tilBackendUrl.error = getString(R.string.backend_url_required)
            return
        }

        if (!validateUrl(backendUrl)) {
            binding.tilBackendUrl.error = getString(R.string.invalid_url)
            return
        }

        binding.tilBackendUrl.error = null

        // Show loading
        binding.btnTestConnection.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = testBackendConnection(backendUrl)

            binding.btnTestConnection.isEnabled = true
            binding.progressBar.visibility = View.GONE

            if (success) {
                updateConnectionStatus(true)
                Toast.makeText(
                    this@BackendConfigActivity,
                    getString(R.string.connection_success),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                updateConnectionStatus(false)
                Toast.makeText(
                    this@BackendConfigActivity,
                    getString(R.string.connection_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun testBackendConnection(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Simple HTTP GET test
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private fun clearWebViewData() {
        // Set flag to clear WebView data and return result
        val resultIntent = Intent().apply {
            putExtra("clear_webview_data", true)
        }
        setResult(RESULT_OK, resultIntent)
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        binding.tvConnectionStatus.visibility = View.VISIBLE
        if (isConnected) {
            binding.tvConnectionStatus.setTextColor(getColor(R.color.success))
            binding.tvConnectionStatus.setText(R.string.connection_success)
        } else {
            binding.tvConnectionStatus.setTextColor(getColor(R.color.text_hint))
            binding.tvConnectionStatus.text = "Not connected"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

/**
 * URL validation extension function
 */
fun String.isValidUrl(): Boolean {
    return try {
        val url = java.net.URL(this)
        url.protocol == "http" || url.protocol == "https"
    } catch (e: Exception) {
        false
    }
}

fun validateUrl(url: String): Boolean {
    return url.isValidUrl()
}

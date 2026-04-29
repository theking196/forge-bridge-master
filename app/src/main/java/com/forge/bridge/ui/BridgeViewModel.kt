package com.forge.bridge.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forge.bridge.data.local.ProviderDao
import com.forge.bridge.data.local.VaultManager
import com.forge.bridge.data.local.entities.ProviderEntity
import com.forge.bridge.data.remote.providers.ProviderRegistry
import com.forge.bridge.data.remote.providers.chatgpt.ChatGptProxyAdapter
import com.forge.bridge.data.remote.providers.claude.ClaudeProxyAdapter
import com.forge.bridge.ui.webview.WebAuthActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUiModel(
    val id: String,
    val name: String,
    val tier: String,
    val status: String,
    val models: List<String>,
    val features: List<String>
)

sealed class AuthResult {
    data class Success(val provider: String) : AuthResult()
    data class Failed(val provider: String, val reason: String) : AuthResult()
}

@HiltViewModel
class BridgeViewModel @Inject constructor(
    application: Application,
    private val registry: ProviderRegistry,
    private val providerDao: ProviderDao,
    private val vaultManager: VaultManager,
    private val chatGptAdapter: ChatGptProxyAdapter,
    private val claudeAdapter: ClaudeProxyAdapter
) : AndroidViewModel(application) {

    private val _providers = MutableStateFlow<List<ProviderUiModel>>(emptyList())
    val providers: StateFlow<List<ProviderUiModel>> = _providers.asStateFlow()

    private val _serverStatus = MutableStateFlow("Starting...")
    val serverStatus: StateFlow<String> = _serverStatus.asStateFlow()

    private val _authResult = MutableSharedFlow<AuthResult>()
    val authResult: SharedFlow<AuthResult> = _authResult.asSharedFlow()

    init { loadProviders() }

    fun loadProviders() {
        viewModelScope.launch {
            val ui = registry.getAll().map { adapter ->
                val authed = runCatching { adapter.isAuthenticated() }.getOrDefault(false)
                ProviderUiModel(
                    id = adapter.providerId,
                    name = adapter.providerName,
                    tier = adapter.tier,
                    status = if (authed) "Connected" else "Disconnected",
                    models = adapter.availableModels,
                    features = adapter.features
                )
            }
            _providers.value = ui
            _serverStatus.value = "Running on localhost:8745"
        }
    }

    fun addApiKeyProvider(providerId: String, apiKey: String, name: String, type: String) {
        viewModelScope.launch {
            vaultManager.storeApiKey(providerId, apiKey)
            providerDao.insert(
                ProviderEntity(
                    id = providerId,
                    name = name,
                    providerType = type,
                    tier = "API",
                    status = "connected",
                    defaultModel = when (providerId) {
                        "openai-api" -> "gpt-4o"
                        "anthropic-api" -> "claude-sonnet-4-20250514"
                        "gemini-api" -> "gemini-2.5-flash"
                        else -> null
                    }
                )
            )
            loadProviders()
        }
    }

    fun addProxyProvider(providerId: String, token: String, name: String, type: String) {
        viewModelScope.launch {
            vaultManager.storeToken(providerId, "SESSION", token)
            providerDao.insert(
                ProviderEntity(
                    id = providerId,
                    name = name,
                    providerType = type,
                    tier = "PROXY",
                    status = "connected"
                )
            )
            loadProviders()
        }
    }

    fun removeProvider(id: String) {
        viewModelScope.launch {
            vaultManager.clearProvider(id)
            providerDao.deleteById(id)
            when (id) {
                "chatgpt-proxy" -> chatGptAdapter.resetConversation()
                "claude-proxy" -> claudeAdapter.resetConversation()
            }
            loadProviders()
        }
    }

    fun resetChatGptConversation() = chatGptAdapter.resetConversation()
    fun resetClaudeConversation() = claudeAdapter.resetConversation()

    fun getWebAuthIntent(provider: String): Intent =
        Intent(getApplication(), WebAuthActivity::class.java).apply {
            putExtra(WebAuthActivity.EXTRA_PROVIDER, provider)
        }

    fun handleWebAuthResult(provider: String, token: String?) {
        viewModelScope.launch {
            if (!token.isNullOrBlank()) {
                when (provider) {
                    "chatgpt" -> addProxyProvider("chatgpt-proxy", token, "ChatGPT (Proxy)", "CHATGPT")
                    "claude" -> addProxyProvider("claude-proxy", token, "Claude (Proxy)", "CLAUDE")
                }
                _authResult.emit(AuthResult.Success(provider))
            } else {
                _authResult.emit(AuthResult.Failed(provider, "No token captured"))
            }
        }
    }
}

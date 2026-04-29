package com.forge.bridge.data.remote.api

import com.forge.bridge.data.local.VaultManager
import com.forge.bridge.data.remote.providers.ProviderAdapter
import com.forge.bridge.data.remote.providers.ProviderRegistry
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodically asks every adapter that supports refresh to refresh its tokens
 * if they are close to expiry. Adapters opt in by overriding `refreshIfNeeded`.
 */
@Singleton
class TokenRefreshScheduler @Inject constructor(
    private val registry: ProviderRegistry,
    private val vault: VaultManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start(intervalMillis: Long = 5 * 60_000L) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runOnce()
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun runOnce() {
        val adapters: List<ProviderAdapter> = registry.getAll()
        for (a in adapters) {
            try { a.refreshIfNeeded(vault) } catch (_: Throwable) { /* best effort */ }
        }
    }
}

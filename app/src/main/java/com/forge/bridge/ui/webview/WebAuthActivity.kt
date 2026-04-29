package com.forge.bridge.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.forge.bridge.ui.theme.ForgeBridgeTheme

/**
 * Tiny embedded browser that loads the provider's login page. Once we detect
 * the session cookie that the relevant adapter needs, we capture it, finish,
 * and return it to the caller.
 *
 *  - ChatGPT  : `__Secure-next-auth.session-token` on chat.openai.com
 *  - Claude   : `sessionKey` on claude.ai
 */
class WebAuthActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "chatgpt"
        val (loginUrl, host, cookieName) = providerConfig(provider)

        setContent {
            ForgeBridgeTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Sign in to ${provider.replaceFirstChar { it.uppercase() }}") })
                    }
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.padding(padding).fillMaxSize().padding(0.dp),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val token = extractCookie(host, cookieName)
                                        if (!token.isNullOrBlank()) {
                                            finishWith(provider, token)
                                        }
                                    }
                                }
                                loadUrl(loginUrl)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun extractCookie(host: String, name: String): String? {
        val cookies = CookieManager.getInstance().getCookie(host) ?: return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.removePrefix("$name=")
    }

    private fun finishWith(provider: String, token: String) {
        val data = Intent().apply {
            putExtra(RESULT_PROVIDER, provider)
            putExtra(RESULT_TOKEN, token)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun providerConfig(provider: String): Triple<String, String, String> = when (provider) {
        "claude" -> Triple("https://claude.ai/login", "https://claude.ai", "sessionKey")
        else -> Triple(
            "https://chat.openai.com/auth/login",
            "https://chat.openai.com",
            "__Secure-next-auth.session-token"
        )
    }

    companion object {
        const val EXTRA_PROVIDER = "extra_provider"
        const val RESULT_PROVIDER = "result_provider"
        const val RESULT_TOKEN = "result_token"
    }
}

package com.forge.bridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private data class ProviderOption(
    val id: String,
    val name: String,
    val type: String,
    val tier: String  // "API" or "PROXY"
)

private val OPTIONS = listOf(
    ProviderOption("openai-api", "OpenAI", "OPENAI", "API"),
    ProviderOption("anthropic-api", "Anthropic", "ANTHROPIC", "API"),
    ProviderOption("gemini-api", "Gemini", "GEMINI", "API"),
    ProviderOption("chatgpt-proxy", "ChatGPT (Browser sign-in)", "CHATGPT", "PROXY"),
    ProviderOption("claude-proxy", "Claude (Browser sign-in)", "CLAUDE", "PROXY"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAddApiKey: (id: String, name: String, type: String, apiKey: String) -> Unit,
    onLaunchWebAuth: (provider: String) -> Unit
) {
    var selected by remember { mutableStateOf(OPTIONS.first()) }
    var apiKey by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Add provider") },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.95f)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selected.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        OPTIONS.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.name) },
                                onClick = { selected = opt; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (selected.tier == "API") {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Stored encrypted on-device. Never leaves the phone.",
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Text(
                        "We'll open a browser window so you can sign in. Forge Bridge captures the session locally — your password never touches the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (selected.tier == "API") {
                TextButton(
                    onClick = { onAddApiKey(selected.id, selected.name + " (API)", selected.type, apiKey.trim()) },
                    enabled = apiKey.isNotBlank()
                ) { Text("Save") }
            } else {
                TextButton(onClick = {
                    val provider = if (selected.id == "chatgpt-proxy") "chatgpt" else "claude"
                    onLaunchWebAuth(provider)
                }) { Text("Sign in with browser") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

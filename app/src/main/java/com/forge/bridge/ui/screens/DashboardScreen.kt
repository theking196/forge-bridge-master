package com.forge.bridge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.forge.bridge.ui.BridgeViewModel
import com.forge.bridge.ui.ProviderUiModel
import com.forge.bridge.ui.theme.ForgeOrange
import com.forge.bridge.ui.theme.ForgeSuccess
import com.forge.bridge.ui.theme.ForgeTextDim
import com.forge.bridge.ui.webview.WebAuthActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: BridgeViewModel = hiltViewModel()) {
    val providers by viewModel.providers.collectAsState()
    val status by viewModel.serverStatus.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    val webAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = result.data?.getStringExtra(WebAuthActivity.RESULT_PROVIDER) ?: return@rememberLauncherForActivityResult
        val token = result.data?.getStringExtra(WebAuthActivity.RESULT_TOKEN)
        viewModel.handleWebAuthResult(provider, token)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Forge Bridge", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = ForgeOrange,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add Provider") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            ServerStatusCard(status = status)
            Spacer(Modifier.height(16.dp))
            Text(
                "Providers",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            if (providers.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers, key = { it.id }) { p ->
                        ProviderCard(p, onRemove = { viewModel.removeProvider(p.id) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddProviderDialog(
            onDismiss = { showAdd = false },
            onAddApiKey = { id, name, type, key ->
                viewModel.addApiKeyProvider(id, key, name, type)
                showAdd = false
            },
            onLaunchWebAuth = { provider ->
                showAdd = false
                webAuthLauncher.launch(viewModel.getWebAuthIntent(provider))
            }
        )
    }
}

@Composable
private fun ServerStatusCard(status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ForgeSuccess)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Bridge Server", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = ForgeTextDim)
            }
        }
    }
}

@Composable
private fun ProviderCard(p: ProviderUiModel, onRemove: () -> Unit) {
    val connected = p.status == "Connected"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (connected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (connected) ForgeSuccess else ForgeTextDim
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${p.tier} · ${p.status} · ${p.models.size} models",
                    style = MaterialTheme.typography.bodyMedium, color = ForgeTextDim
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = ForgeTextDim)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("No providers yet — tap 'Add Provider' to connect one.",
            style = MaterialTheme.typography.bodyMedium, color = ForgeTextDim)
    }
}

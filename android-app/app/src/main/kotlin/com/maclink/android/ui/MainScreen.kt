package com.maclink.android.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maclink.android.network.ConnectionState
import com.maclink.android.network.MacLinkClient
import com.maclink.android.network.NsdDiscovery
import com.maclink.android.service.CallAnswerAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    client: MacLinkClient,
    discovery: NsdDiscovery
) {
    val connectionState by client.state.collectAsState()
    val devices by discovery.discoveredDevices.collectAsState()
    var manualIp by remember { mutableStateOf("") }

    // Odświeżaj stan usługi dostępności po powrocie z Ustawień
    var accessibilityEnabled by remember { mutableStateOf(CallAnswerAccessibilityService.isEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = CallAnswerAccessibilityService.isEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MacLink") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Status połączenia
            ConnectionStatusCard(connectionState)

            Spacer(modifier = Modifier.height(8.dp))

            // Karta usługi dostępności (odbieranie połączeń)
            AccessibilityStatusCard(enabled = accessibilityEnabled)

            Spacer(modifier = Modifier.height(16.dp))

            // Device list
            Text(
                "Urządzenia w sieci",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (devices.isEmpty() && connectionState != ConnectionState.CONNECTED) {
                Text(
                    "Szukam urządzeń MacLink w sieci...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            } else if (devices.isEmpty() && connectionState == ConnectionState.CONNECTED) {
                Text(
                    "Połączono ✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            isConnected = connectionState == ConnectionState.CONNECTED,
                            onClick = {
                                if (connectionState == ConnectionState.CONNECTED) {
                                    client.disconnect()
                                } else {
                                    client.connect(device.host, device.port)
                                }
                            }
                        )
                    }
                }
            }

            // Manual IP fallback
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Połącz ręcznie (IP Maca)", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    placeholder = { Text("192.168.0.162") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        if (manualIp.isNotBlank()) client.connect(manualIp.trim(), 9876)
                    })
                )
                Button(onClick = {
                    if (manualIp.isNotBlank()) client.connect(manualIp.trim(), 9876)
                }) { Text("Połącz") }
            }
        }
    }
}

// MARK: - Karta statusu usługi dostępności

@Composable
private fun AccessibilityStatusCard(enabled: Boolean) {
    val context = LocalContext.current

    if (enabled) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Odbieranie połączeń aktywne",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        "Możesz odbierać i odrzucać rozmowy z Maca",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32).copy(alpha = 0.8f)
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Odbieranie połączeń wyłączone",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Włącz usługę dostępności aby odbierać z Maca",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Włącz") }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState) {
    val (color, label) = when (state) {
        ConnectionState.CONNECTED    -> MaterialTheme.colorScheme.primary to "Połączono ✓"
        ConnectionState.CONNECTING   -> MaterialTheme.colorScheme.secondary to "Łączenie..."
        ConnectionState.ERROR        -> MaterialTheme.colorScheme.error to "Błąd połączenia"
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline to "Rozłączono"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = color, fontWeight = FontWeight.Medium)
            Text(
                "v0.2.0-tcp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: NsdDiscovery.MacDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Medium)
                Text(
                    "${device.host}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onClick) {
                Text(if (isConnected) "Rozłącz" else "Połącz")
            }
        }
    }
}

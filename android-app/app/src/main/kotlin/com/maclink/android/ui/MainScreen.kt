package com.maclink.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maclink.android.network.ConnectionState
import com.maclink.android.network.MacLinkClient
import com.maclink.android.network.NsdDiscovery

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    client: MacLinkClient,
    discovery: NsdDiscovery
) {
    val connectionState by client.state.collectAsState()
    val devices by discovery.discoveredDevices.collectAsState()

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
            // Status
            ConnectionStatusCard(connectionState)

            Spacer(modifier = Modifier.height(16.dp))

            // Device list
            Text(
                "Urządzenia w sieci",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    "Szukam urządzeń MacLink w sieci...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = color, fontWeight = FontWeight.Medium)
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

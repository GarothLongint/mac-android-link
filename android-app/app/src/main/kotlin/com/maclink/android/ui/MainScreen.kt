package com.maclink.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    var manualIp by remember { mutableStateOf("") }

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

            if devices.isEmpty() && connectionState != ConnectionState.CONNECTED {
                Text(
                    "Szukam urządzeń MacLink w sieci...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            } else if devices.isEmpty() && connectionState == ConnectionState.CONNECTED {
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

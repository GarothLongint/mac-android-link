package com.maclink.android.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Discovers MacLink services on the local network using mDNS (NSD).
 * Emits discovered [MacDevice] entries via [discoveredDevices].
 */
class NsdDiscovery(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_maclink._tcp."
    }

    data class MacDevice(
        val name: String,
        val host: String,
        val port: Int
    )

    private val _discoveredDevices = MutableStateFlow<List<MacDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<MacDevice>> = _discoveredDevices

    private val nsdManager by lazy { context.getSystemService(NsdManager::class.java) }
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                println("[NSD] Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                println("[NSD] Stop discovery failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                println("[NSD] Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                println("[NSD] Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                println("[NSD] Service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, makeResolveListener())
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                println("[NSD] Service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.value = _discoveredDevices.value
                    .filter { it.name != serviceInfo.serviceName }
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        discoveryListener = listener
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        discoveryListener = null
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            println("[NSD] Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host?.hostAddress ?: return
            val port = serviceInfo.port
            val device = MacDevice(serviceInfo.serviceName, host, port)
            println("[NSD] Resolved: $device")

            val current = _discoveredDevices.value.toMutableList()
            current.removeAll { it.name == device.name }
            current.add(device)
            _discoveredDevices.value = current
        }
    }
}

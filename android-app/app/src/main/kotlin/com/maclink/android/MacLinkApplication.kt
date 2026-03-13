package com.maclink.android

import android.app.Application
import android.provider.Settings
import com.maclink.android.network.MacLinkClient
import com.maclink.android.network.NsdDiscovery

class MacLinkApplication : Application() {
    lateinit var client: MacLinkClient
    lateinit var discovery: NsdDiscovery

    override fun onCreate() {
        super.onCreate()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL

        client = MacLinkClient(deviceName = deviceName, deviceId = deviceId)
        discovery = NsdDiscovery(this)
    }
}

/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import net.mullvad.mullvadvpn.BuildConfig
import net.mullvad.mullvadvpn.wireguard.di.wireGuardModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

private const val LOG_TAG = "WireGuard"

class WireGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize logging
        Logger.setTag(LOG_TAG)
        if (!BuildConfig.DEBUG) {
            Logger.setMinSeverity(Severity.Info)
        }

        // Initialize Koin
        startKoin {
            androidContext(this@WireGuardApplication)
            modules(wireGuardModule)
        }

        // Create notification channels
        createNotificationChannels()

        Logger.i("WireGuard application initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Tunnel status channel
            val tunnelChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_TUNNEL,
                "Tunnel Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications about tunnel connection status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(tunnelChannel)

            // Update channel
            val updateChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_UPDATE,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about available app updates"
            }
            notificationManager.createNotificationChannel(updateChannel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_TUNNEL = "wireguard_tunnel"
        const val NOTIFICATION_CHANNEL_UPDATE = "wireguard_update"
    }
}

/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.lib.model.wireguard.Config
import net.mullvad.mullvadvpn.lib.model.wireguard.Tunnel
import net.mullvad.mullvadvpn.lib.model.wireguard.TunnelState
import net.mullvad.talpid.TalpidVpnService
import org.koin.android.ext.android.inject
import java.net.Inet4Address
import java.net.Inet6Address

class WireGuardVpnService : TalpidVpnService() {

    private val tunnelManager: TunnelManager by inject()
    private var serviceScope: CoroutineScope? = null
    private var currentTunnel: Tunnel? = null
    private var tunFd: ParcelFileDescriptor? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: WireGuardVpnService
            get() = this@WireGuardVpnService
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i("WireGuardVpnService created")
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onBind(intent: Intent?): IBinder {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent) ?: binder
        } else {
            binder
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("WireGuardVpnService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_TUNNEL -> {
                val tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME)
                if (tunnelName != null) {
                    startTunnel(tunnelName)
                }
            }
            ACTION_STOP_TUNNEL -> {
                stopTunnel()
            }
            else -> {
                // Handle system VPN binding
            }
        }

        return START_STICKY
    }

    private fun startTunnel(tunnelName: String) {
        serviceScope?.launch {
            try {
                val tunnel = tunnelManager.getTunnel(tunnelName)
                if (tunnel == null) {
                    Logger.e("Tunnel not found: $tunnelName")
                    return@launch
                }

                // Start foreground service with notification
                startForeground(NOTIFICATION_ID, createNotification(tunnel))

                // Create VPN tunnel
                val config = tunnel.config
                val fd = createVpnTunnel(config)

                if (fd != null) {
                    tunFd = fd
                    currentTunnel = tunnel

                    // Update tunnel state
                    tunnelManager.setTunnelState(tunnel, TunnelState.UP)

                    Logger.i("Tunnel started: $tunnelName")
                } else {
                    Logger.e("Failed to create VPN tunnel")
                    stopSelf()
                }
            } catch (e: Exception) {
                Logger.e("Error starting tunnel", e)
                stopSelf()
            }
        }
    }

    private fun stopTunnel() {
        serviceScope?.launch {
            try {
                currentTunnel?.let { tunnel ->
                    tunnelManager.setTunnelState(tunnel, TunnelState.DOWN)
                }

                tunFd?.close()
                tunFd = null
                currentTunnel = null

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()

                Logger.i("Tunnel stopped")
            } catch (e: Exception) {
                Logger.e("Error stopping tunnel", e)
            }
        }
    }

    private fun createVpnTunnel(config: Config): ParcelFileDescriptor? {
        val builder = Builder()

        // Configure interface
        val iface = config.`interface`

        // Add addresses
        for (address in iface.addresses) {
            builder.addAddress(
                address.address,
                address.mask
            )
        }

        // Add DNS servers
        for (dns in iface.dnsServers) {
            builder.addDnsServer(dns)
        }

        // Add routes from peers' allowed IPs
        for (peer in config.peers) {
            for (allowedIp in peer.allowedIps) {
                builder.addRoute(allowedIp.address, allowedIp.mask)
            }
        }

        // Set MTU
        iface.mtu?.let { builder.setMtu(it) }

        // Configure split tunneling
        for (app in iface.excludedApplications) {
            try {
                builder.addDisallowedApplication(app)
            } catch (e: Exception) {
                Logger.w("Failed to exclude application: $app")
            }
        }

        for (app in iface.includedApplications) {
            try {
                builder.addAllowedApplication(app)
            } catch (e: Exception) {
                Logger.w("Failed to include application: $app")
            }
        }

        // Set session name
        builder.setSession(currentTunnel?.name ?: "WireGuard")

        // Set blocking mode
        builder.setBlocking(false)

        // Set metered hint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        return try {
            builder.establish()
        } catch (e: Exception) {
            Logger.e("Failed to establish VPN", e)
            null
        }
    }

    private fun createNotification(tunnel: Tunnel): Notification {
        val intent = Intent(this, WireGuardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, WireGuardVpnService::class.java).apply {
            action = ACTION_STOP_TUNNEL
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WireGuardApplication.NOTIFICATION_CHANNEL_TUNNEL)
            .setContentTitle("WireGuard")
            .setContentText("Connected to ${tunnel.name}")
            .setSmallIcon(R.drawable.ic_wireguard_connected)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_wireguard_disconnected,
                "Disconnect",
                disconnectPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onRevoke() {
        Logger.i("VPN revoked")
        stopTunnel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
        tunFd?.close()
        Logger.i("WireGuardVpnService destroyed")
    }

    companion object {
        const val ACTION_START_TUNNEL = "net.mullvad.wireguard.action.START_TUNNEL"
        const val ACTION_STOP_TUNNEL = "net.mullvad.wireguard.action.STOP_TUNNEL"
        const val EXTRA_TUNNEL_NAME = "tunnel_name"
        private const val NOTIFICATION_ID = 1
    }
}

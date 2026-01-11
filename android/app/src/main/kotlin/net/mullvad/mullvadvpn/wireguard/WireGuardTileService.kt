/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.lib.model.wireguard.TunnelState
import org.koin.android.ext.android.inject

class WireGuardTileService : TileService() {

    private val tunnelManager: TunnelManager by inject()
    private var serviceScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        serviceScope?.launch {
            combine(
                tunnelManager.tunnels,
                tunnelManager.activeTunnel
            ) { tunnels, activeTunnel ->
                Pair(tunnels, activeTunnel)
            }.collect { (tunnels, activeTunnel) ->
                updateTile(tunnels.isNotEmpty(), activeTunnel != null, activeTunnel?.name)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onClick() {
        super.onClick()

        val activeTunnel = tunnelManager.activeTunnel.value
        val tunnels = tunnelManager.tunnels.value

        if (activeTunnel != null) {
            // Toggle off the active tunnel
            serviceScope?.launch {
                tunnelManager.setTunnelState(activeTunnel, TunnelState.DOWN)
            }
        } else if (tunnels.isNotEmpty()) {
            // Try to activate the last used tunnel or the first one
            val lastUsedName = tunnelManager.lastUsedTunnel.value
            val tunnelToActivate = lastUsedName?.let { name ->
                tunnels.find { it.name == name }
            } ?: tunnels.firstOrNull()

            tunnelToActivate?.let { tunnel ->
                serviceScope?.launch {
                    tunnelManager.setTunnelState(tunnel, TunnelState.UP)
                }
            }
        }
    }

    private fun updateTile(hasTunnels: Boolean, isActive: Boolean, tunnelName: String?) {
        val tile = qsTile ?: return

        tile.state = when {
            !hasTunnels -> Tile.STATE_UNAVAILABLE
            isActive -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }

        tile.label = when {
            !hasTunnels -> "WireGuard"
            isActive -> tunnelName ?: "WireGuard"
            else -> "WireGuard"
        }

        tile.subtitle = when {
            !hasTunnels -> "No tunnels"
            isActive -> "Connected"
            else -> "Disconnected"
        }

        // Update icon based on state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.icon = Icon.createWithResource(
                this,
                if (isActive) R.drawable.ic_wireguard_connected else R.drawable.ic_wireguard_disconnected
            )
        }

        tile.updateTile()
    }

    companion object {
        fun requestListeningState(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, WireGuardTileService::class.java)
            )
        }
    }
}

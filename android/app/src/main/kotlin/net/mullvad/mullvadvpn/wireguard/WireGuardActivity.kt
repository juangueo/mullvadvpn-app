/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import co.touchlab.kermit.Logger
import net.mullvad.mullvadvpn.wireguard.ui.WireGuardApp
import net.mullvad.mullvadvpn.wireguard.ui.theme.WireGuardTheme

class WireGuardActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Logger.i("VPN permission granted")
            // Can now start VPN
        } else {
            Logger.w("VPN permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            WireGuardTheme {
                WireGuardApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_TUNNEL -> {
                val tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME)
                Logger.i("Toggle tunnel intent received: $tunnelName")
                // Handle toggle
            }
            else -> {
                Logger.d("Unhandled intent action: ${intent.action}")
            }
        }
    }

    /**
     * Request VPN permission from the user.
     */
    fun requestVpnPermission() {
        val intent = android.net.VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            Logger.i("VPN permission already granted")
        }
    }

    companion object {
        const val ACTION_TOGGLE_TUNNEL = "net.mullvad.wireguard.action.TOGGLE_TUNNEL"
        const val EXTRA_TUNNEL_NAME = "tunnel_name"
    }
}

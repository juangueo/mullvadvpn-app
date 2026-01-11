/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object WireGuardRoutes {
    const val TUNNEL_LIST = "tunnel_list"
    const val TUNNEL_DETAIL = "tunnel_detail/{tunnelName}"
    const val TUNNEL_EDITOR = "tunnel_editor"
    const val TUNNEL_EDITOR_WITH_NAME = "tunnel_editor/{tunnelName}"
    const val SETTINGS = "settings"
    const val LOG_VIEWER = "log_viewer"
    const val QR_SCANNER = "qr_scanner"

    fun tunnelDetail(tunnelName: String) = "tunnel_detail/$tunnelName"
    fun tunnelEditor(tunnelName: String?) = if (tunnelName != null) "tunnel_editor/$tunnelName" else TUNNEL_EDITOR
}

@Composable
fun WireGuardApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = WireGuardRoutes.TUNNEL_LIST,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(WireGuardRoutes.TUNNEL_LIST) {
            TunnelListScreen(
                onNavigateToDetail = { tunnelName ->
                    navController.navigate(WireGuardRoutes.tunnelDetail(tunnelName))
                },
                onNavigateToEditor = { tunnelName ->
                    navController.navigate(WireGuardRoutes.tunnelEditor(tunnelName))
                },
                onNavigateToSettings = {
                    navController.navigate(WireGuardRoutes.SETTINGS)
                }
            )
        }

        composable(
            route = WireGuardRoutes.TUNNEL_DETAIL,
            arguments = listOf(
                navArgument("tunnelName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tunnelName = backStackEntry.arguments?.getString("tunnelName") ?: ""
            TunnelDetailScreen(
                tunnelName = tunnelName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { name ->
                    navController.navigate(WireGuardRoutes.tunnelEditor(name))
                }
            )
        }

        composable(WireGuardRoutes.TUNNEL_EDITOR) {
            TunnelEditorScreen(
                tunnelName = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = WireGuardRoutes.TUNNEL_EDITOR_WITH_NAME,
            arguments = listOf(
                navArgument("tunnelName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tunnelName = backStackEntry.arguments?.getString("tunnelName")
            TunnelEditorScreen(
                tunnelName = tunnelName,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(WireGuardRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogs = {
                    navController.navigate(WireGuardRoutes.LOG_VIEWER)
                }
            )
        }

        composable(WireGuardRoutes.LOG_VIEWER) {
            LogViewerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(WireGuardRoutes.QR_SCANNER) {
            QrScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onConfigScanned = { config ->
                    // Handle scanned config
                    navController.popBackStack()
                }
            )
        }
    }
}

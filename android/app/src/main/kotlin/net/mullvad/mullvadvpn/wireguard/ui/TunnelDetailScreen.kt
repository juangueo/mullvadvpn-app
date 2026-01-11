/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.lib.model.wireguard.Interface
import net.mullvad.mullvadvpn.lib.model.wireguard.Peer
import net.mullvad.mullvadvpn.lib.model.wireguard.PeerStatistics
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelDetailScreen(
    tunnelName: String,
    viewModel: TunnelDetailViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToEditor: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TunnelDetailSideEffect.ShowError -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
                TunnelDetailSideEffect.NavigateBack -> {
                    onNavigateBack()
                }
                is TunnelDetailSideEffect.NavigateToEditor -> {
                    onNavigateToEditor(effect.tunnelName)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.tunnel?.name ?: tunnelName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.editTunnel() }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit tunnel")
                    }
                    IconButton(onClick = { viewModel.deleteTunnel() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete tunnel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        uiState.tunnel?.let { tunnel ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status card
                item {
                    StatusCard(
                        isActive = uiState.isActive,
                        onToggle = { viewModel.toggleTunnel() },
                        totalRx = uiState.totalRx,
                        totalTx = uiState.totalTx
                    )
                }

                // Interface section
                item {
                    InterfaceCard(iface = tunnel.config.`interface`)
                }

                // Peers section
                items(uiState.peerDetails) { peerDetail ->
                    PeerCard(
                        peer = peerDetail.peer,
                        stats = peerDetail.stats,
                        isActive = uiState.isActive
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    isActive: Boolean,
    onToggle: () -> Unit,
    totalRx: Long,
    totalTx: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            if (isActive && (totalRx > 0 || totalTx > 0)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "Received",
                        value = TunnelDetailViewModel.formatBytes(totalRx)
                    )
                    StatItem(
                        label = "Transmitted",
                        value = TunnelDetailViewModel.formatBytes(totalTx)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InterfaceCard(iface: Interface) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Interface",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(
                label = "Public key",
                value = iface.keyPair.publicKey.toBase64()
            )

            if (iface.addresses.isNotEmpty()) {
                DetailRow(
                    label = "Addresses",
                    value = iface.addresses.joinToString(", ")
                )
            }

            if (iface.dnsServers.isNotEmpty()) {
                DetailRow(
                    label = "DNS servers",
                    value = iface.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
                )
            }

            iface.mtu?.let {
                DetailRow(label = "MTU", value = it.toString())
            }

            iface.listenPort?.let {
                DetailRow(label = "Listen port", value = it.toString())
            }
        }
    }
}

@Composable
private fun PeerCard(
    peer: Peer,
    stats: PeerStatistics?,
    isActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Peer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(
                label = "Public key",
                value = peer.publicKey.toBase64()
            )

            peer.endpoint?.let {
                DetailRow(label = "Endpoint", value = it.toString())
            }

            if (peer.allowedIps.isNotEmpty()) {
                DetailRow(
                    label = "Allowed IPs",
                    value = peer.allowedIps.joinToString(", ")
                )
            }

            peer.persistentKeepalive?.let {
                if (it > 0) {
                    DetailRow(label = "Persistent keepalive", value = "$it seconds")
                }
            }

            // Show stats if tunnel is active
            if (isActive && stats != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                DetailRow(
                    label = "Transfer",
                    value = "${TunnelDetailViewModel.formatBytes(stats.rxBytes)} received, ${TunnelDetailViewModel.formatBytes(stats.txBytes)} sent"
                )

                if (stats.hasHandshake()) {
                    DetailRow(
                        label = "Latest handshake",
                        value = TunnelDetailViewModel.formatTimestamp(stats.lastHandshakeTimestamp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

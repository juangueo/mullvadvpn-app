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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelEditorScreen(
    tunnelName: String?,
    viewModel: TunnelEditorViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TunnelEditorSideEffect.ShowError -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
                TunnelEditorSideEffect.NavigateBack -> {
                    onNavigateBack()
                }
                is TunnelEditorSideEffect.SaveSuccess -> {
                    scope.launch {
                        snackbarHostState.showSnackbar("Tunnel saved")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isNewTunnel) "Create tunnel" else "Edit tunnel")
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.discardChanges() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interface section
            item {
                InterfaceEditorCard(
                    state = uiState.interfaceState,
                    onNameChange = viewModel::updateInterfaceName,
                    onPrivateKeyChange = viewModel::updatePrivateKey,
                    onGenerateKey = viewModel::generateNewKeyPair,
                    onAddressesChange = viewModel::updateAddresses,
                    onListenPortChange = viewModel::updateListenPort,
                    onMtuChange = viewModel::updateMtu,
                    onDnsServersChange = viewModel::updateDnsServers
                )
            }

            // Peers section
            items(uiState.peers, key = { it.id }) { peer ->
                PeerEditorCard(
                    state = peer,
                    canRemove = uiState.peers.size > 1,
                    onPublicKeyChange = { viewModel.updatePeerPublicKey(peer.id, it) },
                    onPreSharedKeyChange = { viewModel.updatePeerPreSharedKey(peer.id, it) },
                    onAllowedIpsChange = { viewModel.updatePeerAllowedIps(peer.id, it) },
                    onEndpointChange = { viewModel.updatePeerEndpoint(peer.id, it) },
                    onPersistentKeepaliveChange = { viewModel.updatePeerPersistentKeepalive(peer.id, it) },
                    onRemove = { viewModel.removePeer(peer.id) }
                )
            }

            // Add peer button
            item {
                OutlinedButton(
                    onClick = { viewModel.addPeer() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add peer")
                }
            }
        }
    }
}

@Composable
private fun InterfaceEditorCard(
    state: InterfaceState,
    onNameChange: (String) -> Unit,
    onPrivateKeyChange: (String) -> Unit,
    onGenerateKey: () -> Unit,
    onAddressesChange: (String) -> Unit,
    onListenPortChange: (String) -> Unit,
    onMtuChange: (String) -> Unit,
    onDnsServersChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Interface",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.privateKey,
                    onValueChange = onPrivateKeyChange,
                    label = { Text("Private key") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onGenerateKey) {
                    Icon(Icons.Default.Refresh, contentDescription = "Generate new key")
                }
            }

            if (state.publicKey.isNotBlank()) {
                Column {
                    Text(
                        text = "Public key",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.publicKey,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            OutlinedTextField(
                value = state.addresses,
                onValueChange = onAddressesChange,
                label = { Text("Addresses") },
                placeholder = { Text("e.g., 10.0.0.1/24, fd00::1/128") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.listenPort,
                    onValueChange = onListenPortChange,
                    label = { Text("Listen port") },
                    placeholder = { Text("Random") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )

                OutlinedTextField(
                    value = state.mtu,
                    onValueChange = onMtuChange,
                    label = { Text("MTU") },
                    placeholder = { Text("Auto") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
            }

            OutlinedTextField(
                value = state.dnsServers,
                onValueChange = onDnsServersChange,
                label = { Text("DNS servers") },
                placeholder = { Text("e.g., 1.1.1.1, 8.8.8.8") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
    }
}

@Composable
private fun PeerEditorCard(
    state: PeerState,
    canRemove: Boolean,
    onPublicKeyChange: (String) -> Unit,
    onPreSharedKeyChange: (String) -> Unit,
    onAllowedIpsChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onPersistentKeepaliveChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove peer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.publicKey,
                onValueChange = onPublicKeyChange,
                label = { Text("Public key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.preSharedKey,
                onValueChange = onPreSharedKeyChange,
                label = { Text("Pre-shared key (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.allowedIps,
                onValueChange = onAllowedIpsChange,
                label = { Text("Allowed IPs") },
                placeholder = { Text("e.g., 0.0.0.0/0, ::/0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.endpoint,
                onValueChange = onEndpointChange,
                label = { Text("Endpoint (optional)") },
                placeholder = { Text("e.g., vpn.example.com:51820") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.persistentKeepalive,
                onValueChange = onPersistentKeepaliveChange,
                label = { Text("Persistent keepalive (optional)") },
                placeholder = { Text("e.g., 25") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
        }
    }
}

/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.lib.model.wireguard.Config
import net.mullvad.mullvadvpn.lib.model.wireguard.InetEndpoint
import net.mullvad.mullvadvpn.lib.model.wireguard.InetNetwork
import net.mullvad.mullvadvpn.lib.model.wireguard.Interface
import net.mullvad.mullvadvpn.lib.model.wireguard.Key
import net.mullvad.mullvadvpn.lib.model.wireguard.KeyPair
import net.mullvad.mullvadvpn.lib.model.wireguard.Peer
import net.mullvad.mullvadvpn.wireguard.TunnelManager
import java.net.InetAddress

data class InterfaceState(
    val name: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val addresses: String = "",
    val listenPort: String = "",
    val mtu: String = "",
    val dnsServers: String = ""
)

data class PeerState(
    val id: Int,
    val publicKey: String = "",
    val preSharedKey: String = "",
    val allowedIps: String = "",
    val endpoint: String = "",
    val persistentKeepalive: String = ""
)

data class TunnelEditorUiState(
    val isNewTunnel: Boolean = true,
    val originalName: String = "",
    val interfaceState: InterfaceState = InterfaceState(),
    val peers: List<PeerState> = listOf(PeerState(id = 0)),
    val hasChanges: Boolean = false,
    val isSaving: Boolean = false,
    val errors: Map<String, String> = emptyMap()
)

sealed class TunnelEditorSideEffect {
    data class ShowError(val message: String) : TunnelEditorSideEffect()
    object NavigateBack : TunnelEditorSideEffect()
    data class SaveSuccess(val tunnelName: String) : TunnelEditorSideEffect()
}

class TunnelEditorViewModel(
    private val tunnelManager: TunnelManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tunnelName: String? = savedStateHandle["tunnelName"]
    private var peerIdCounter = 1

    private val _uiState = MutableStateFlow(TunnelEditorUiState())
    val uiState: StateFlow<TunnelEditorUiState> = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<TunnelEditorSideEffect>()
    val sideEffect: SharedFlow<TunnelEditorSideEffect> = _sideEffect.asSharedFlow()

    init {
        if (tunnelName != null) {
            loadExistingTunnel(tunnelName)
        } else {
            initNewTunnel()
        }
    }

    private fun loadExistingTunnel(name: String) {
        val tunnel = tunnelManager.getTunnel(name)
        if (tunnel == null) {
            viewModelScope.launch {
                _sideEffect.emit(TunnelEditorSideEffect.ShowError("Tunnel not found"))
                _sideEffect.emit(TunnelEditorSideEffect.NavigateBack)
            }
            return
        }

        val config = tunnel.config
        val iface = config.`interface`

        _uiState.value = TunnelEditorUiState(
            isNewTunnel = false,
            originalName = name,
            interfaceState = InterfaceState(
                name = name,
                privateKey = iface.keyPair.privateKey.toBase64(),
                publicKey = iface.keyPair.publicKey.toBase64(),
                addresses = iface.addresses.joinToString(", "),
                listenPort = iface.listenPort?.toString() ?: "",
                mtu = iface.mtu?.toString() ?: "",
                dnsServers = iface.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
            ),
            peers = config.peers.mapIndexed { index, peer ->
                PeerState(
                    id = index,
                    publicKey = peer.publicKey.toBase64(),
                    preSharedKey = peer.preSharedKey?.toBase64() ?: "",
                    allowedIps = peer.allowedIps.joinToString(", "),
                    endpoint = peer.endpoint?.toString() ?: "",
                    persistentKeepalive = peer.persistentKeepalive?.toString() ?: ""
                )
            }.ifEmpty { listOf(PeerState(id = 0)) }
        )
        peerIdCounter = config.peers.size
    }

    private fun initNewTunnel() {
        // Generate a new key pair for new tunnels
        try {
            val keyPair = KeyPair.generate()
            _uiState.value = TunnelEditorUiState(
                isNewTunnel = true,
                interfaceState = InterfaceState(
                    privateKey = keyPair.privateKey.toBase64(),
                    publicKey = keyPair.publicKey.toBase64()
                )
            )
        } catch (e: Exception) {
            _uiState.value = TunnelEditorUiState(isNewTunnel = true)
        }
    }

    fun updateInterfaceName(name: String) {
        _uiState.value = _uiState.value.copy(
            interfaceState = _uiState.value.interfaceState.copy(name = name),
            hasChanges = true
        )
    }

    fun updatePrivateKey(key: String) {
        val publicKey = try {
            if (key.length == Key.KEY_LENGTH_BASE64) {
                val privateKey = Key.fromBase64(key)
                KeyPair.fromPrivateKey(privateKey).publicKey.toBase64()
            } else ""
        } catch (e: Exception) { "" }

        _uiState.value = _uiState.value.copy(
            interfaceState = _uiState.value.interfaceState.copy(
                privateKey = key,
                publicKey = publicKey
            ),
            hasChanges = true
        )
    }

    fun generateNewKeyPair() {
        try {
            val keyPair = KeyPair.generate()
            _uiState.value = _uiState.value.copy(
                interfaceState = _uiState.value.interfaceState.copy(
                    privateKey = keyPair.privateKey.toBase64(),
                    publicKey = keyPair.publicKey.toBase64()
                ),
                hasChanges = true
            )
        } catch (e: Exception) {
            viewModelScope.launch {
                _sideEffect.emit(TunnelEditorSideEffect.ShowError("Failed to generate key pair"))
            }
        }
    }

    fun updateAddresses(addresses: String) {
        _uiState.value = _uiState.value.copy(
            interfaceState = _uiState.value.interfaceState.copy(addresses = addresses),
            hasChanges = true
        )
    }

    fun updateListenPort(port: String) {
        _uiState.value = _uiState.value.copy(
            interfaceState = _uiState.value.interfaceState.copy(listenPort = port),
            hasChanges = true
        )
    }

    fun updateMtu(mtu: String) {
        _uiState.value = _uiState.value.copy(
            interfaceState = _uiState.value.interfaceState.copy(mtu = mtu),
            hasChanges = true
        )
    }

    fun updateDnsServers(dns: String) {
        _uiState.value = _uiState.value.copy(
            interfaceState = _uiState.value.interfaceState.copy(dnsServers = dns),
            hasChanges = true
        )
    }

    // Peer updates
    fun updatePeerPublicKey(peerId: Int, key: String) {
        updatePeer(peerId) { it.copy(publicKey = key) }
    }

    fun updatePeerPreSharedKey(peerId: Int, key: String) {
        updatePeer(peerId) { it.copy(preSharedKey = key) }
    }

    fun updatePeerAllowedIps(peerId: Int, ips: String) {
        updatePeer(peerId) { it.copy(allowedIps = ips) }
    }

    fun updatePeerEndpoint(peerId: Int, endpoint: String) {
        updatePeer(peerId) { it.copy(endpoint = endpoint) }
    }

    fun updatePeerPersistentKeepalive(peerId: Int, keepalive: String) {
        updatePeer(peerId) { it.copy(persistentKeepalive = keepalive) }
    }

    private fun updatePeer(peerId: Int, update: (PeerState) -> PeerState) {
        _uiState.value = _uiState.value.copy(
            peers = _uiState.value.peers.map { peer ->
                if (peer.id == peerId) update(peer) else peer
            },
            hasChanges = true
        )
    }

    fun addPeer() {
        _uiState.value = _uiState.value.copy(
            peers = _uiState.value.peers + PeerState(id = peerIdCounter++),
            hasChanges = true
        )
    }

    fun removePeer(peerId: Int) {
        val peers = _uiState.value.peers.filter { it.id != peerId }
        _uiState.value = _uiState.value.copy(
            peers = peers.ifEmpty { listOf(PeerState(id = peerIdCounter++)) },
            hasChanges = true
        )
    }

    fun save() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            try {
                val state = _uiState.value
                val config = buildConfig()

                val name = state.interfaceState.name.trim()
                if (!Config.isNameValid(name)) {
                    throw IllegalArgumentException("Invalid tunnel name")
                }

                val result = if (state.isNewTunnel) {
                    tunnelManager.create(name, config)
                } else {
                    val tunnel = tunnelManager.getTunnel(state.originalName)
                    if (tunnel != null) {
                        if (name != state.originalName) {
                            tunnelManager.rename(tunnel, name)
                        }
                        tunnelManager.save(tunnel.copy(name = name), config)
                    } else {
                        Result.failure(Exception("Tunnel not found"))
                    }
                }

                result
                    .onSuccess {
                        _sideEffect.emit(TunnelEditorSideEffect.SaveSuccess(name))
                        _sideEffect.emit(TunnelEditorSideEffect.NavigateBack)
                    }
                    .onFailure { error ->
                        _sideEffect.emit(TunnelEditorSideEffect.ShowError(error.message ?: "Failed to save tunnel"))
                    }
            } catch (e: Exception) {
                _sideEffect.emit(TunnelEditorSideEffect.ShowError(e.message ?: "Invalid configuration"))
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    private fun buildConfig(): Config {
        val state = _uiState.value
        val ifaceState = state.interfaceState

        // Build interface
        val privateKey = Key.fromBase64(ifaceState.privateKey)
        val keyPair = KeyPair.fromPrivateKey(privateKey)

        val addresses = ifaceState.addresses.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { InetNetwork.parse(it) }
            .toSet()

        val dnsServers = ifaceState.dnsServers.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { InetAddress.getByName(it) }
            .toSet()

        val listenPort = ifaceState.listenPort.toIntOrNull()
        val mtu = ifaceState.mtu.toIntOrNull()

        val iface = Interface(
            keyPair = keyPair,
            addresses = addresses,
            dnsServers = dnsServers,
            listenPort = listenPort,
            mtu = mtu
        )

        // Build peers
        val peers = state.peers.filter { it.publicKey.isNotBlank() }.map { peerState ->
            val publicKey = Key.fromBase64(peerState.publicKey)
            val preSharedKey = if (peerState.preSharedKey.isNotBlank())
                Key.fromBase64(peerState.preSharedKey)
            else null

            val allowedIps = peerState.allowedIps.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { InetNetwork.parse(it) }
                .toSet()

            val endpoint = if (peerState.endpoint.isNotBlank())
                InetEndpoint.parse(peerState.endpoint)
            else null

            val persistentKeepalive = peerState.persistentKeepalive.toIntOrNull()

            Peer(
                publicKey = publicKey,
                preSharedKey = preSharedKey,
                allowedIps = allowedIps,
                endpoint = endpoint,
                persistentKeepalive = persistentKeepalive
            )
        }

        return Config(`interface` = iface, peers = peers)
    }

    fun discardChanges() {
        viewModelScope.launch {
            _sideEffect.emit(TunnelEditorSideEffect.NavigateBack)
        }
    }
}

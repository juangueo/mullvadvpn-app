/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.lib.model.wireguard.Peer
import net.mullvad.mullvadvpn.lib.model.wireguard.PeerStatistics
import net.mullvad.mullvadvpn.lib.model.wireguard.Tunnel
import net.mullvad.mullvadvpn.lib.model.wireguard.TunnelState
import net.mullvad.mullvadvpn.lib.model.wireguard.TunnelStatistics
import net.mullvad.mullvadvpn.wireguard.TunnelManager
import java.text.DateFormat
import java.util.Date

data class PeerDetailState(
    val peer: Peer,
    val stats: PeerStatistics?
)

data class TunnelDetailUiState(
    val tunnel: Tunnel? = null,
    val isActive: Boolean = false,
    val peerDetails: List<PeerDetailState> = emptyList(),
    val totalRx: Long = 0,
    val totalTx: Long = 0,
    val isLoading: Boolean = true
)

sealed class TunnelDetailSideEffect {
    data class ShowError(val message: String) : TunnelDetailSideEffect()
    object NavigateBack : TunnelDetailSideEffect()
    data class NavigateToEditor(val tunnelName: String) : TunnelDetailSideEffect()
}

class TunnelDetailViewModel(
    private val tunnelManager: TunnelManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tunnelName: String = savedStateHandle["tunnelName"] ?: ""

    private val _uiState = MutableStateFlow(TunnelDetailUiState())
    val uiState: StateFlow<TunnelDetailUiState> = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<TunnelDetailSideEffect>()
    val sideEffect: SharedFlow<TunnelDetailSideEffect> = _sideEffect.asSharedFlow()

    init {
        loadTunnel()
        startStatisticsPolling()
    }

    private fun loadTunnel() {
        val tunnel = tunnelManager.getTunnel(tunnelName)
        if (tunnel == null) {
            viewModelScope.launch {
                _sideEffect.emit(TunnelDetailSideEffect.ShowError("Tunnel not found"))
                _sideEffect.emit(TunnelDetailSideEffect.NavigateBack)
            }
            return
        }

        _uiState.value = TunnelDetailUiState(
            tunnel = tunnel,
            isActive = tunnel.state == TunnelState.UP,
            peerDetails = tunnel.config.peers.map { peer ->
                PeerDetailState(peer = peer, stats = null)
            },
            isLoading = false
        )
    }

    private fun startStatisticsPolling() {
        viewModelScope.launch {
            while (isActive) {
                updateStatistics()
                delay(1000) // Update every second
            }
        }
    }

    private suspend fun updateStatistics() {
        val tunnel = _uiState.value.tunnel ?: return
        if (tunnel.state != TunnelState.UP) {
            _uiState.value = _uiState.value.copy(
                peerDetails = tunnel.config.peers.map { peer ->
                    PeerDetailState(peer = peer, stats = null)
                },
                totalRx = 0,
                totalTx = 0
            )
            return
        }

        // In a real implementation, we would get stats from the backend
        // For now, this is a placeholder
    }

    fun toggleTunnel() {
        viewModelScope.launch {
            val tunnel = _uiState.value.tunnel ?: return@launch
            tunnelManager.setTunnelState(tunnel, TunnelState.TOGGLE)
                .onSuccess { newState ->
                    _uiState.value = _uiState.value.copy(
                        tunnel = tunnel.copy(state = newState),
                        isActive = newState == TunnelState.UP
                    )
                }
                .onFailure { error ->
                    _sideEffect.emit(TunnelDetailSideEffect.ShowError(error.message ?: "Failed to toggle tunnel"))
                }
        }
    }

    fun deleteTunnel() {
        viewModelScope.launch {
            val tunnel = _uiState.value.tunnel ?: return@launch
            tunnelManager.delete(tunnel)
                .onSuccess {
                    _sideEffect.emit(TunnelDetailSideEffect.NavigateBack)
                }
                .onFailure { error ->
                    _sideEffect.emit(TunnelDetailSideEffect.ShowError(error.message ?: "Failed to delete tunnel"))
                }
        }
    }

    fun editTunnel() {
        viewModelScope.launch {
            _sideEffect.emit(TunnelDetailSideEffect.NavigateToEditor(tunnelName))
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MiB"
                else -> "${bytes / (1024 * 1024 * 1024)} GiB"
            }
        }

        fun formatTimestamp(timestamp: Long): String {
            if (timestamp == 0L) return "Never"
            val date = Date(timestamp * 1000)
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(date)
        }
    }
}

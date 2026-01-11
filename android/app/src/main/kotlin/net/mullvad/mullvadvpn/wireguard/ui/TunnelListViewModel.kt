/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.lib.model.wireguard.Config
import net.mullvad.mullvadvpn.lib.model.wireguard.Tunnel
import net.mullvad.mullvadvpn.lib.model.wireguard.TunnelState
import net.mullvad.mullvadvpn.wireguard.TunnelManager

data class TunnelListUiState(
    val tunnels: List<Tunnel> = emptyList(),
    val activeTunnel: Tunnel? = null,
    val selectedTunnels: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isLoading: Boolean = true
)

sealed class TunnelListSideEffect {
    data class ShowError(val message: String) : TunnelListSideEffect()
    data class NavigateToTunnelDetail(val tunnelName: String) : TunnelListSideEffect()
    data class NavigateToTunnelEditor(val tunnelName: String?) : TunnelListSideEffect()
    object ShowAddTunnelSheet : TunnelListSideEffect()
    data class TunnelCreated(val tunnelName: String) : TunnelListSideEffect()
}

class TunnelListViewModel(
    private val tunnelManager: TunnelManager
) : ViewModel() {

    private val _selectedTunnels = MutableStateFlow<Set<String>>(emptySet())

    private val _uiState = MutableStateFlow(TunnelListUiState())
    val uiState: StateFlow<TunnelListUiState> = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<TunnelListSideEffect>()
    val sideEffect: SharedFlow<TunnelListSideEffect> = _sideEffect.asSharedFlow()

    init {
        combine(
            tunnelManager.tunnels,
            tunnelManager.activeTunnel,
            _selectedTunnels
        ) { tunnels, activeTunnel, selectedTunnels ->
            TunnelListUiState(
                tunnels = tunnels,
                activeTunnel = activeTunnel,
                selectedTunnels = selectedTunnels,
                isMultiSelectMode = selectedTunnels.isNotEmpty(),
                isLoading = false
            )
        }.launchIn(viewModelScope).also { _ ->
            _uiState.value = _uiState.value.copy(isLoading = false)
        }

        // Observe tunnel changes
        viewModelScope.launch {
            tunnelManager.tunnels.collect { tunnels ->
                _uiState.value = _uiState.value.copy(
                    tunnels = tunnels,
                    isLoading = false
                )
            }
        }

        viewModelScope.launch {
            tunnelManager.activeTunnel.collect { tunnel ->
                _uiState.value = _uiState.value.copy(activeTunnel = tunnel)
            }
        }
    }

    fun toggleTunnelState(tunnel: Tunnel) {
        viewModelScope.launch {
            tunnelManager.setTunnelState(tunnel, TunnelState.TOGGLE).onFailure { error ->
                _sideEffect.emit(TunnelListSideEffect.ShowError(error.message ?: "Failed to toggle tunnel"))
            }
        }
    }

    fun setTunnelState(tunnel: Tunnel, state: TunnelState) {
        viewModelScope.launch {
            tunnelManager.setTunnelState(tunnel, state).onFailure { error ->
                _sideEffect.emit(TunnelListSideEffect.ShowError(error.message ?: "Failed to set tunnel state"))
            }
        }
    }

    fun deleteTunnel(tunnel: Tunnel) {
        viewModelScope.launch {
            tunnelManager.delete(tunnel).onFailure { error ->
                _sideEffect.emit(TunnelListSideEffect.ShowError(error.message ?: "Failed to delete tunnel"))
            }
        }
    }

    fun deleteSelectedTunnels() {
        viewModelScope.launch {
            val selected = _selectedTunnels.value.toList()
            selected.forEach { name ->
                tunnelManager.getTunnel(name)?.let { tunnel ->
                    tunnelManager.delete(tunnel)
                }
            }
            clearSelection()
        }
    }

    fun toggleSelection(tunnelName: String) {
        val current = _selectedTunnels.value.toMutableSet()
        if (current.contains(tunnelName)) {
            current.remove(tunnelName)
        } else {
            current.add(tunnelName)
        }
        _selectedTunnels.value = current
        _uiState.value = _uiState.value.copy(
            selectedTunnels = current,
            isMultiSelectMode = current.isNotEmpty()
        )
    }

    fun selectAll() {
        val allNames = _uiState.value.tunnels.map { it.name }.toSet()
        _selectedTunnels.value = allNames
        _uiState.value = _uiState.value.copy(
            selectedTunnels = allNames,
            isMultiSelectMode = allNames.isNotEmpty()
        )
    }

    fun clearSelection() {
        _selectedTunnels.value = emptySet()
        _uiState.value = _uiState.value.copy(
            selectedTunnels = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun onTunnelClick(tunnel: Tunnel) {
        if (_uiState.value.isMultiSelectMode) {
            toggleSelection(tunnel.name)
        } else {
            viewModelScope.launch {
                _sideEffect.emit(TunnelListSideEffect.NavigateToTunnelDetail(tunnel.name))
            }
        }
    }

    fun onTunnelLongClick(tunnel: Tunnel) {
        toggleSelection(tunnel.name)
    }

    fun onAddTunnelClick() {
        viewModelScope.launch {
            _sideEffect.emit(TunnelListSideEffect.ShowAddTunnelSheet)
        }
    }

    fun createEmptyTunnel() {
        viewModelScope.launch {
            _sideEffect.emit(TunnelListSideEffect.NavigateToTunnelEditor(null))
        }
    }

    fun importTunnel(name: String, configText: String) {
        viewModelScope.launch {
            tunnelManager.importTunnel(name, configText)
                .onSuccess { tunnel ->
                    _sideEffect.emit(TunnelListSideEffect.TunnelCreated(tunnel.name))
                }
                .onFailure { error ->
                    _sideEffect.emit(TunnelListSideEffect.ShowError(error.message ?: "Failed to import tunnel"))
                }
        }
    }

    fun createTunnel(name: String, config: Config) {
        viewModelScope.launch {
            tunnelManager.create(name, config)
                .onSuccess { tunnel ->
                    _sideEffect.emit(TunnelListSideEffect.TunnelCreated(tunnel.name))
                }
                .onFailure { error ->
                    _sideEffect.emit(TunnelListSideEffect.ShowError(error.message ?: "Failed to create tunnel"))
                }
        }
    }
}

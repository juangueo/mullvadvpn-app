/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mullvad.mullvadvpn.lib.model.wireguard.Config
import net.mullvad.mullvadvpn.lib.model.wireguard.Tunnel
import net.mullvad.mullvadvpn.lib.model.wireguard.TunnelState
import java.io.File

/**
 * Manages the lifecycle and storage of WireGuard tunnels.
 */
class TunnelManager(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configStore = FileConfigStore(context)

    private val _tunnels = MutableStateFlow<List<Tunnel>>(emptyList())
    val tunnels: StateFlow<List<Tunnel>> = _tunnels.asStateFlow()

    private val _activeTunnel = MutableStateFlow<Tunnel?>(null)
    val activeTunnel: StateFlow<Tunnel?> = _activeTunnel.asStateFlow()

    private val _lastUsedTunnel = MutableStateFlow<String?>(null)
    val lastUsedTunnel: StateFlow<String?> = _lastUsedTunnel.asStateFlow()

    init {
        scope.launch {
            loadTunnels()
        }
    }

    /**
     * Load all tunnels from storage.
     */
    private suspend fun loadTunnels() {
        val loaded = configStore.enumerate().mapNotNull { name ->
            try {
                val config = configStore.load(name)
                Tunnel(name = name, config = config)
            } catch (e: Exception) {
                null
            }
        }.sorted()
        _tunnels.value = loaded
    }

    /**
     * Create a new tunnel.
     */
    suspend fun create(name: String, config: Config): Result<Tunnel> = withContext(Dispatchers.IO) {
        try {
            require(Config.isNameValid(name)) { "Invalid tunnel name" }
            require(_tunnels.value.none { it.name.equals(name, ignoreCase = true) }) {
                "A tunnel with that name already exists"
            }

            configStore.save(name, config)
            val tunnel = Tunnel(name = name, config = config)
            _tunnels.value = (_tunnels.value + tunnel).sorted()
            Result.success(tunnel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a tunnel.
     */
    suspend fun delete(tunnel: Tunnel): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // If tunnel is active, deactivate it first
            if (_activeTunnel.value?.name == tunnel.name) {
                setTunnelState(tunnel, TunnelState.DOWN)
            }

            configStore.delete(tunnel.name)
            _tunnels.value = _tunnels.value.filter { it.name != tunnel.name }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename a tunnel.
     */
    suspend fun rename(tunnel: Tunnel, newName: String): Result<Tunnel> = withContext(Dispatchers.IO) {
        try {
            require(Config.isNameValid(newName)) { "Invalid tunnel name" }
            require(_tunnels.value.none { it.name.equals(newName, ignoreCase = true) && it.name != tunnel.name }) {
                "A tunnel with that name already exists"
            }

            configStore.rename(tunnel.name, newName)
            val newTunnel = tunnel.copy(name = newName)
            _tunnels.value = _tunnels.value.map {
                if (it.name == tunnel.name) newTunnel else it
            }.sorted()

            if (_activeTunnel.value?.name == tunnel.name) {
                _activeTunnel.value = newTunnel
            }

            Result.success(newTunnel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a tunnel's configuration.
     */
    suspend fun save(tunnel: Tunnel, config: Config): Result<Tunnel> = withContext(Dispatchers.IO) {
        try {
            configStore.save(tunnel.name, config)
            val updatedTunnel = tunnel.copy(config = config)
            _tunnels.value = _tunnels.value.map {
                if (it.name == tunnel.name) updatedTunnel else it
            }

            if (_activeTunnel.value?.name == tunnel.name) {
                _activeTunnel.value = updatedTunnel
            }

            Result.success(updatedTunnel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the state of a tunnel.
     */
    suspend fun setTunnelState(tunnel: Tunnel, state: TunnelState): Result<TunnelState> = withContext(Dispatchers.IO) {
        try {
            val targetState = when (state) {
                TunnelState.TOGGLE -> if (tunnel.state == TunnelState.UP) TunnelState.DOWN else TunnelState.UP
                else -> state
            }

            when (targetState) {
                TunnelState.UP -> {
                    // Deactivate any other active tunnel first
                    _activeTunnel.value?.let { active ->
                        if (active.name != tunnel.name) {
                            setTunnelStateInternal(active, TunnelState.DOWN)
                        }
                    }
                    setTunnelStateInternal(tunnel, TunnelState.UP)
                    val activeTunnel = tunnel.copy(state = TunnelState.UP)
                    _activeTunnel.value = activeTunnel
                    _lastUsedTunnel.value = tunnel.name
                    updateTunnelInList(activeTunnel)
                }
                TunnelState.DOWN -> {
                    setTunnelStateInternal(tunnel, TunnelState.DOWN)
                    if (_activeTunnel.value?.name == tunnel.name) {
                        _activeTunnel.value = null
                    }
                    updateTunnelInList(tunnel.copy(state = TunnelState.DOWN))
                }
                else -> {}
            }

            Result.success(targetState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateTunnelInList(tunnel: Tunnel) {
        _tunnels.value = _tunnels.value.map {
            if (it.name == tunnel.name) tunnel else it
        }
    }

    /**
     * Internal method to actually change tunnel state via the backend.
     * This will be implemented to interface with the Rust WireGuard backend.
     */
    private suspend fun setTunnelStateInternal(tunnel: Tunnel, state: TunnelState) {
        // This will be implemented to use the native WireGuard backend
        // For now, this is a placeholder
    }

    /**
     * Get a tunnel by name.
     */
    fun getTunnel(name: String): Tunnel? = _tunnels.value.find { it.name.equals(name, ignoreCase = true) }

    /**
     * Import tunnels from configuration text.
     */
    suspend fun importTunnel(name: String, configText: String): Result<Tunnel> = withContext(Dispatchers.IO) {
        try {
            val config = Config.parse(configText)
            create(name, config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export a tunnel's configuration.
     */
    suspend fun exportTunnel(tunnel: Tunnel): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(tunnel.config.toWgQuickString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * File-based configuration store for tunnel configurations.
 */
class FileConfigStore(private val context: Context) {
    private val configDir: File
        get() = File(context.filesDir, "tunnels").also { it.mkdirs() }

    private fun configFile(name: String) = File(configDir, "$name.conf")

    suspend fun enumerate(): List<String> = withContext(Dispatchers.IO) {
        configDir.listFiles()
            ?.filter { it.extension == "conf" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    suspend fun load(name: String): Config = withContext(Dispatchers.IO) {
        val file = configFile(name)
        require(file.exists()) { "Configuration file not found" }
        Config.parse(file.inputStream())
    }

    suspend fun save(name: String, config: Config) = withContext(Dispatchers.IO) {
        val file = configFile(name)
        file.writeText(config.toWgQuickString())
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        val file = configFile(name)
        require(file.delete()) { "Failed to delete configuration file" }
    }

    suspend fun rename(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val oldFile = configFile(oldName)
        val newFile = configFile(newName)
        require(oldFile.renameTo(newFile)) { "Failed to rename configuration file" }
    }
}

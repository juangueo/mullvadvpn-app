/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

/**
 * Interface for WireGuard VPN backends.
 */
interface Backend {

    /**
     * Get the current state of a tunnel.
     */
    suspend fun getState(tunnel: Tunnel): TunnelState

    /**
     * Get the current configuration of a tunnel.
     */
    suspend fun getConfig(tunnel: Tunnel): Config?

    /**
     * Set the state of a tunnel.
     *
     * @param tunnel The tunnel to modify
     * @param state The desired state (UP, DOWN, or TOGGLE)
     * @return The new state of the tunnel
     */
    suspend fun setState(tunnel: Tunnel, state: TunnelState): TunnelState

    /**
     * Get the current statistics for a tunnel.
     */
    suspend fun getStatistics(tunnel: Tunnel): TunnelStatistics?

    /**
     * Get the type identifier for this backend.
     */
    val typeName: String
}

/**
 * Exception thrown when a backend operation fails.
 */
class BackendException(
    val reason: Reason,
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    enum class Reason {
        UNKNOWN_TUNNEL,
        INVALID_CONFIG,
        VPN_NOT_AUTHORIZED,
        BACKEND_NOT_RUNNING,
        TUNNEL_ALREADY_UP,
        GENERIC_ERROR
    }
}

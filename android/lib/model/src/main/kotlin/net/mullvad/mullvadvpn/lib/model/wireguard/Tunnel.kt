/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the state of a WireGuard tunnel.
 */
enum class TunnelState {
    DOWN,
    TOGGLE,
    UP;

    companion object {
        fun of(running: Boolean): TunnelState = if (running) UP else DOWN
    }
}

/**
 * Represents a named WireGuard tunnel.
 */
@Parcelize
data class Tunnel(
    val name: String,
    val config: Config,
    val state: TunnelState = TunnelState.DOWN
) : Parcelable, Comparable<Tunnel> {

    init {
        require(Config.isNameValid(name)) { "Invalid tunnel name: $name" }
    }

    override fun compareTo(other: Tunnel): Int = name.compareTo(other.name, ignoreCase = true)
}

/**
 * Statistics for a WireGuard tunnel.
 */
@Parcelize
data class TunnelStatistics(
    val peerStats: Map<Key, PeerStatistics>
) : Parcelable {

    /**
     * Get total received bytes across all peers.
     */
    fun totalRx(): Long = peerStats.values.sumOf { it.rxBytes }

    /**
     * Get total transmitted bytes across all peers.
     */
    fun totalTx(): Long = peerStats.values.sumOf { it.txBytes }
}

/**
 * Statistics for a single peer.
 */
@Parcelize
data class PeerStatistics(
    val rxBytes: Long,
    val txBytes: Long,
    val lastHandshakeTimestamp: Long
) : Parcelable {

    /**
     * Returns true if a handshake has occurred with this peer.
     */
    fun hasHandshake(): Boolean = lastHandshakeTimestamp > 0
}

/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Represents a WireGuard endpoint (host:port).
 */
@Parcelize
data class InetEndpoint(
    val host: String,
    val port: Int
) : Parcelable {

    init {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
    }

    /**
     * Returns the resolved address if the host is a valid IP address, null otherwise.
     */
    fun getResolvedAddress(): InetAddress? = try {
        InetAddress.getByName(host)
    } catch (e: Exception) {
        null
    }

    override fun toString(): String {
        val resolvedAddress = getResolvedAddress()
        return when {
            resolvedAddress is Inet6Address -> "[$host]:$port"
            resolvedAddress is Inet4Address -> "$host:$port"
            host.contains(":") -> "[$host]:$port"  // IPv6 hostname
            else -> "$host:$port"
        }
    }

    companion object {
        fun parse(endpoint: String): InetEndpoint {
            // Handle IPv6 addresses in brackets
            return if (endpoint.startsWith("[")) {
                val closeBracket = endpoint.indexOf("]")
                require(closeBracket > 1) { "Invalid IPv6 endpoint format" }
                val host = endpoint.substring(1, closeBracket)
                val portPart = endpoint.substring(closeBracket + 1)
                require(portPart.startsWith(":")) { "Invalid endpoint format, missing port" }
                val port = portPart.substring(1).toInt()
                InetEndpoint(host, port)
            } else {
                val lastColon = endpoint.lastIndexOf(":")
                require(lastColon > 0) { "Invalid endpoint format" }
                val host = endpoint.substring(0, lastColon)
                val port = endpoint.substring(lastColon + 1).toInt()
                InetEndpoint(host, port)
            }
        }
    }
}

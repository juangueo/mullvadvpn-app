/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a [Peer] section of a WireGuard configuration file.
 */
@Parcelize
data class Peer(
    val allowedIps: Set<InetNetwork> = emptySet(),
    val endpoint: InetEndpoint? = null,
    val persistentKeepalive: Int? = null,
    val preSharedKey: Key? = null,
    val publicKey: Key
) : Parcelable {

    init {
        persistentKeepalive?.let {
            require(it in 0..MAX_PERSISTENT_KEEPALIVE) {
                "Persistent keepalive must be between 0 and $MAX_PERSISTENT_KEEPALIVE"
            }
        }
    }

    /**
     * Convert to wg-quick configuration format.
     */
    fun toWgQuickString(): String = buildString {
        appendLine("[Peer]")
        if (allowedIps.isNotEmpty()) {
            appendLine("AllowedIPs = ${allowedIps.joinToString(", ")}")
        }
        endpoint?.let { appendLine("Endpoint = $it") }
        persistentKeepalive?.let {
            if (it > 0) appendLine("PersistentKeepalive = $it")
        }
        preSharedKey?.let { appendLine("PresharedKey = ${it.toBase64()}") }
        appendLine("PublicKey = ${publicKey.toBase64()}")
    }

    class Builder {
        private val allowedIps = mutableSetOf<InetNetwork>()
        private var endpoint: InetEndpoint? = null
        private var persistentKeepalive: Int? = null
        private var preSharedKey: Key? = null
        private var publicKey: Key? = null

        fun addAllowedIp(ip: InetNetwork) = apply { allowedIps.add(ip) }
        fun addAllowedIps(ips: Collection<InetNetwork>) = apply { allowedIps.addAll(ips) }
        fun setEndpoint(endpoint: InetEndpoint?) = apply { this.endpoint = endpoint }
        fun setPersistentKeepalive(keepalive: Int?) = apply { persistentKeepalive = keepalive }
        fun setPreSharedKey(key: Key?) = apply { preSharedKey = key }
        fun setPublicKey(key: Key) = apply { publicKey = key }

        fun parsePublicKey(base64: String) = apply {
            publicKey = Key.fromBase64(base64)
        }

        fun parsePreSharedKey(base64: String) = apply {
            preSharedKey = Key.fromBase64(base64)
        }

        fun build(): Peer {
            val pk = publicKey ?: throw IllegalStateException("PublicKey is required")
            return Peer(
                allowedIps = allowedIps.toSet(),
                endpoint = endpoint,
                persistentKeepalive = persistentKeepalive,
                preSharedKey = preSharedKey,
                publicKey = pk
            )
        }
    }

    companion object {
        const val MAX_PERSISTENT_KEEPALIVE = 65535

        fun parse(lines: List<String>): Peer {
            val builder = Builder()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val equalIndex = trimmed.indexOf('=')
                if (equalIndex < 0) continue

                val key = trimmed.substring(0, equalIndex).trim().lowercase()
                val value = trimmed.substring(equalIndex + 1).trim()

                when (key) {
                    "allowedips" -> {
                        value.split(",").map { it.trim() }.forEach {
                            builder.addAllowedIp(InetNetwork.parse(it))
                        }
                    }
                    "endpoint" -> builder.setEndpoint(InetEndpoint.parse(value))
                    "persistentkeepalive" -> builder.setPersistentKeepalive(value.toInt())
                    "presharedkey" -> builder.parsePreSharedKey(value)
                    "publickey" -> builder.parsePublicKey(value)
                }
            }
            return builder.build()
        }
    }
}

/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Represents the contents of a wg-quick configuration file.
 */
@Parcelize
data class Config(
    val `interface`: Interface,
    val peers: List<Peer>
) : Parcelable {

    /**
     * Convert to wg-quick configuration format.
     */
    fun toWgQuickString(): String = buildString {
        append(`interface`.toWgQuickString())
        for (peer in peers) {
            appendLine()
            append(peer.toWgQuickString())
        }
    }

    /**
     * Convert to WireGuard userspace format (for API).
     */
    fun toWgUserspaceString(): String = buildString {
        // Interface settings
        appendLine("private_key=${`interface`.keyPair.privateKey.toHex()}")
        `interface`.listenPort?.let { appendLine("listen_port=$it") }

        // Peers
        for (peer in peers) {
            appendLine("public_key=${peer.publicKey.toHex()}")
            peer.preSharedKey?.let { appendLine("preshared_key=${it.toHex()}") }
            peer.endpoint?.let { appendLine("endpoint=$it") }
            peer.persistentKeepalive?.let {
                if (it > 0) appendLine("persistent_keepalive_interval=$it")
            }
            for (allowedIp in peer.allowedIps) {
                appendLine("allowed_ip=$allowedIp")
            }
        }
    }

    private fun Key.toHex(): String {
        return toBytes().joinToString("") { "%02x".format(it) }
    }

    class Builder {
        private var iface: Interface? = null
        private val peers = mutableListOf<Peer>()

        fun setInterface(iface: Interface) = apply { this.iface = iface }
        fun addPeer(peer: Peer) = apply { peers.add(peer) }
        fun addPeers(peers: Collection<Peer>) = apply { this.peers.addAll(peers) }

        fun build(): Config {
            val i = iface ?: throw IllegalStateException("Interface is required")
            return Config(i, peers.toList())
        }
    }

    companion object {
        private val SECTION_PATTERN = Regex("^\\s*\\[\\s*(\\w+)\\s*]\\s*$")

        fun parse(input: InputStream): Config = parse(BufferedReader(InputStreamReader(input)))

        fun parse(reader: BufferedReader): Config {
            val builder = Builder()
            var currentSection: String? = null
            val currentLines = mutableListOf<String>()

            fun processSection() {
                if (currentLines.isEmpty()) return
                when (currentSection?.lowercase()) {
                    "interface" -> builder.setInterface(Interface.parse(currentLines))
                    "peer" -> builder.addPeer(Peer.parse(currentLines))
                }
                currentLines.clear()
            }

            reader.forEachLine { line ->
                val trimmed = line.trim()

                // Check for section header
                val match = SECTION_PATTERN.matchEntire(trimmed)
                if (match != null) {
                    processSection()
                    currentSection = match.groupValues[1]
                } else if (currentSection != null) {
                    currentLines.add(trimmed)
                }
            }

            // Process the last section
            processSection()

            return builder.build()
        }

        fun parse(configText: String): Config = parse(configText.byteInputStream())

        /**
         * Maximum allowed length for a tunnel name.
         */
        const val MAX_NAME_LENGTH = 15

        /**
         * Pattern for valid tunnel names.
         */
        val NAME_PATTERN = Regex("[a-zA-Z0-9_=+.-]+")

        /**
         * Validate a tunnel name.
         */
        fun isNameValid(name: String): Boolean {
            return name.isNotEmpty() &&
                    name.length <= MAX_NAME_LENGTH &&
                    NAME_PATTERN.matches(name)
        }
    }
}

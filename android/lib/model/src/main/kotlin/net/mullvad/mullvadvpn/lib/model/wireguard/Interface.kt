/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.net.InetAddress

/**
 * Represents the [Interface] section of a WireGuard configuration file.
 */
@Parcelize
data class Interface(
    val addresses: Set<InetNetwork> = emptySet(),
    val dnsServers: Set<InetAddress> = emptySet(),
    val dnsSearchDomains: Set<String> = emptySet(),
    val excludedApplications: Set<String> = emptySet(),
    val includedApplications: Set<String> = emptySet(),
    val keyPair: KeyPair,
    val listenPort: Int? = null,
    val mtu: Int? = null
) : Parcelable {

    init {
        require(excludedApplications.isEmpty() || includedApplications.isEmpty()) {
            "Cannot have both excluded and included applications"
        }
        listenPort?.let {
            require(it in 1..65535) { "Listen port must be between 1 and 65535" }
        }
        mtu?.let {
            require(it in MIN_MTU..MAX_MTU) { "MTU must be between $MIN_MTU and $MAX_MTU" }
        }
    }

    /**
     * Convert to wg-quick configuration format.
     */
    fun toWgQuickString(): String = buildString {
        appendLine("[Interface]")
        if (addresses.isNotEmpty()) {
            appendLine("Address = ${addresses.joinToString(", ")}")
        }
        if (dnsServers.isNotEmpty() || dnsSearchDomains.isNotEmpty()) {
            val dnsString = buildList {
                addAll(dnsServers.map { it.hostAddress })
                addAll(dnsSearchDomains)
            }.joinToString(", ")
            appendLine("DNS = $dnsString")
        }
        if (excludedApplications.isNotEmpty()) {
            appendLine("ExcludedApplications = ${excludedApplications.joinToString(", ")}")
        }
        if (includedApplications.isNotEmpty()) {
            appendLine("IncludedApplications = ${includedApplications.joinToString(", ")}")
        }
        listenPort?.let { appendLine("ListenPort = $it") }
        mtu?.let { appendLine("MTU = $it") }
        appendLine("PrivateKey = ${keyPair.privateKey.toBase64()}")
    }

    class Builder {
        private val addresses = mutableSetOf<InetNetwork>()
        private val dnsServers = mutableSetOf<InetAddress>()
        private val dnsSearchDomains = mutableSetOf<String>()
        private val excludedApplications = mutableSetOf<String>()
        private val includedApplications = mutableSetOf<String>()
        private var keyPair: KeyPair? = null
        private var listenPort: Int? = null
        private var mtu: Int? = null

        fun addAddress(address: InetNetwork) = apply { addresses.add(address) }
        fun addAddresses(addresses: Collection<InetNetwork>) = apply { this.addresses.addAll(addresses) }
        fun addDnsServer(server: InetAddress) = apply { dnsServers.add(server) }
        fun addDnsServers(servers: Collection<InetAddress>) = apply { dnsServers.addAll(servers) }
        fun addDnsSearchDomain(domain: String) = apply { dnsSearchDomains.add(domain) }
        fun excludeApplication(app: String) = apply { excludedApplications.add(app) }
        fun includeApplication(app: String) = apply { includedApplications.add(app) }
        fun setKeyPair(keyPair: KeyPair) = apply { this.keyPair = keyPair }
        fun setListenPort(port: Int?) = apply { listenPort = port }
        fun setMtu(mtu: Int?) = apply { this.mtu = mtu }

        fun parsePrivateKey(base64: String) = apply {
            val privateKey = Key.fromBase64(base64)
            keyPair = KeyPair.fromPrivateKey(privateKey)
        }

        fun build(): Interface {
            val kp = keyPair ?: throw IllegalStateException("KeyPair is required")
            return Interface(
                addresses = addresses.toSet(),
                dnsServers = dnsServers.toSet(),
                dnsSearchDomains = dnsSearchDomains.toSet(),
                excludedApplications = excludedApplications.toSet(),
                includedApplications = includedApplications.toSet(),
                keyPair = kp,
                listenPort = listenPort,
                mtu = mtu
            )
        }
    }

    companion object {
        const val MIN_MTU = 576
        const val MAX_MTU = 65535

        fun parse(lines: List<String>): Interface {
            val builder = Builder()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val equalIndex = trimmed.indexOf('=')
                if (equalIndex < 0) continue

                val key = trimmed.substring(0, equalIndex).trim().lowercase()
                val value = trimmed.substring(equalIndex + 1).trim()

                when (key) {
                    "address" -> {
                        value.split(",").map { it.trim() }.forEach {
                            builder.addAddress(InetNetwork.parse(it))
                        }
                    }
                    "dns" -> {
                        value.split(",").map { it.trim() }.forEach { part ->
                            try {
                                builder.addDnsServer(InetAddress.getByName(part))
                            } catch (e: Exception) {
                                // Might be a search domain
                                builder.addDnsSearchDomain(part)
                            }
                        }
                    }
                    "excludedapplications" -> {
                        value.split(",").map { it.trim() }.forEach {
                            builder.excludeApplication(it)
                        }
                    }
                    "includedapplications" -> {
                        value.split(",").map { it.trim() }.forEach {
                            builder.includeApplication(it)
                        }
                    }
                    "listenport" -> builder.setListenPort(value.toInt())
                    "mtu" -> builder.setMtu(value.toInt())
                    "privatekey" -> builder.parsePrivateKey(value)
                }
            }
            return builder.build()
        }
    }
}

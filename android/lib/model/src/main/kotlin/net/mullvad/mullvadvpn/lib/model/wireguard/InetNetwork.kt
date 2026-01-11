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
 * Represents an IP network (address + prefix length).
 */
@Parcelize
data class InetNetwork(
    val address: InetAddress,
    val mask: Int
) : Parcelable, Comparable<InetNetwork> {

    init {
        val maxMask = if (address is Inet4Address) 32 else 128
        require(mask in 0..maxMask) { "Invalid mask $mask for address family" }
    }

    override fun compareTo(other: InetNetwork): Int {
        val addrCompare = compareAddresses(address, other.address)
        return if (addrCompare != 0) addrCompare else mask.compareTo(other.mask)
    }

    override fun toString(): String = "${address.hostAddress}/$mask"

    companion object {
        fun parse(network: String): InetNetwork {
            val parts = network.split("/")
            val address = InetAddress.getByName(parts[0])
            val mask = if (parts.size > 1) {
                parts[1].toInt()
            } else {
                if (address is Inet4Address) 32 else 128
            }
            return InetNetwork(address, mask)
        }

        private fun compareAddresses(a: InetAddress, b: InetAddress): Int {
            val aBytes = a.address
            val bBytes = b.address
            if (aBytes.size != bBytes.size) {
                return aBytes.size.compareTo(bBytes.size)
            }
            for (i in aBytes.indices) {
                val cmp = (aBytes[i].toInt() and 0xFF).compareTo(bBytes[i].toInt() and 0xFF)
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

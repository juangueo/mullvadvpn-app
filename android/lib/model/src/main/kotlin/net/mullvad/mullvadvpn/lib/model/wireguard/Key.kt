/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.security.SecureRandom
import java.util.Base64

/**
 * Represents a WireGuard key (public or private).
 */
@Parcelize
data class Key(private val key: ByteArray) : Parcelable {

    init {
        require(key.size == KEY_LENGTH) { "Key must be $KEY_LENGTH bytes" }
    }

    fun toBase64(): String = Base64.getEncoder().encodeToString(key)

    fun toBytes(): ByteArray = key.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Key
        return key.contentEquals(other.key)
    }

    override fun hashCode(): Int = key.contentHashCode()

    override fun toString(): String = toBase64()

    companion object {
        const val KEY_LENGTH = 32
        const val KEY_LENGTH_BASE64 = 44

        fun fromBase64(base64: String): Key {
            require(base64.length == KEY_LENGTH_BASE64) {
                "Base64 key must be $KEY_LENGTH_BASE64 characters"
            }
            val bytes = Base64.getDecoder().decode(base64)
            return Key(bytes)
        }

        fun generatePrivateKey(): Key {
            val privateKey = ByteArray(KEY_LENGTH)
            SecureRandom().nextBytes(privateKey)
            // Clamp the private key as per WireGuard spec
            privateKey[0] = (privateKey[0].toInt() and 248).toByte()
            privateKey[31] = (privateKey[31].toInt() and 127).toByte()
            privateKey[31] = (privateKey[31].toInt() or 64).toByte()
            return Key(privateKey)
        }
    }
}

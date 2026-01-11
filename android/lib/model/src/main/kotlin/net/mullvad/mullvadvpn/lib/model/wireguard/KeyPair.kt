/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.lib.model.wireguard

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a WireGuard key pair (private key and derived public key).
 */
@Parcelize
data class KeyPair(
    val privateKey: Key,
    val publicKey: Key
) : Parcelable {

    companion object {
        /**
         * Generate a new random key pair.
         */
        fun generate(): KeyPair {
            val privateKey = Key.generatePrivateKey()
            // In a real implementation, we would derive the public key from the private key
            // using Curve25519. For now, we'll generate another random key as a placeholder.
            // The actual derivation should be done via native code or a crypto library.
            val publicKey = derivePublicKey(privateKey)
            return KeyPair(privateKey, publicKey)
        }

        /**
         * Create a key pair from an existing private key.
         */
        fun fromPrivateKey(privateKey: Key): KeyPair {
            val publicKey = derivePublicKey(privateKey)
            return KeyPair(privateKey, publicKey)
        }

        /**
         * Derive the public key from a private key using Curve25519.
         * Note: In a production implementation, this would use native crypto code.
         */
        private external fun derivePublicKey(privateKey: Key): Key

        init {
            try {
                System.loadLibrary("mullvad_jni")
            } catch (e: UnsatisfiedLinkError) {
                // Library not loaded yet, will be loaded by the service
            }
        }
    }
}

/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.di

import net.mullvad.mullvadvpn.wireguard.TunnelManager
import net.mullvad.mullvadvpn.wireguard.ui.TunnelDetailViewModel
import net.mullvad.mullvadvpn.wireguard.ui.TunnelEditorViewModel
import net.mullvad.mullvadvpn.wireguard.ui.TunnelListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val wireGuardModule = module {
    // Tunnel Manager - singleton
    single { TunnelManager(androidContext()) }

    // ViewModels
    viewModel { TunnelListViewModel(get()) }
    viewModel { TunnelDetailViewModel(get(), get()) }
    viewModel { TunnelEditorViewModel(get(), get()) }
}

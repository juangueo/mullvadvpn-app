/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// WireGuard brand colors
val WireGuardRed = Color(0xFFCC0000)
val WireGuardDarkRed = Color(0xFF8B0000)
val WireGuardLightRed = Color(0xFFFF5252)

// Light color scheme matching WireGuard's design
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF673AB7),         // Deep Purple (matches WG app)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1C4E9),
    onPrimaryContainer = Color(0xFF311B92),

    secondary = Color(0xFF00897B),       // Teal
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),

    tertiary = Color(0xFF795548),        // Brown
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7CCC8),
    onTertiaryContainer = Color(0xFF3E2723),

    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFCD8DF),
    onErrorContainer = Color(0xFF8B0000),

    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),

    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),

    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),

    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF)
)

// Dark color scheme matching WireGuard's dark mode
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB39DDB),         // Light Purple
    onPrimary = Color(0xFF311B92),
    primaryContainer = Color(0xFF4527A0),
    onPrimaryContainer = Color(0xFFD1C4E9),

    secondary = Color(0xFF80CBC4),       // Light Teal
    onSecondary = Color(0xFF003833),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFB2DFDB),

    tertiary = Color(0xFFBCAAA4),        // Light Brown
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFF5D4037),
    onTertiaryContainer = Color(0xFFD7CCC8),

    error = Color(0xFFCF6679),
    onError = Color(0xFF8B0000),
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFCD8DF),

    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),

    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF673AB7)
)

@Composable
fun WireGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // Disabled by default to match WG's consistent styling
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WireGuardTypography,
        content = content
    )
}

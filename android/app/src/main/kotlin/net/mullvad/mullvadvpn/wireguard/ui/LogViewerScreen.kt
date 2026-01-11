/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package net.mullvad.mullvadvpn.wireguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit = {}
) {
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // Simulate loading logs
    LaunchedEffect(Unit) {
        // In a real implementation, this would load actual logs
        logLines.addAll(listOf(
            "WireGuard for Android",
            "Build: ${android.os.Build.VERSION.RELEASE}",
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "",
            "Ready to display logs..."
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { logLines.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear log")
                    }
                    IconButton(onClick = { /* Export logs */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share log")
                    }
                    IconButton(onClick = { /* Save logs */ }) {
                        Icon(Icons.Default.Save, contentDescription = "Save log")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                items(logLines) { line ->
                    LogLine(text = line)
                }
            }
        }
    }
}

@Composable
private fun LogLine(text: String) {
    val color = when {
        text.contains("error", ignoreCase = true) -> Color(0xFFFF6B6B)
        text.contains("warning", ignoreCase = true) -> Color(0xFFFFD93D)
        text.contains("debug", ignoreCase = true) -> Color(0xFF6BCB77)
        else -> Color(0xFFE0E0E0)
    }

    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

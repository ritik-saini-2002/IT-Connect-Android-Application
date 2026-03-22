package com.example.ritik_2.windowscontrol.ui.theme

import androidx.compose.runtime.Composable
import com.example.ritik_2.theme.ITConnectTheme

// PcControl uses the main app's ITConnectTheme
// This wrapper exists so pccontrol screens stay themed consistently
@Composable
fun PcControlTheme(content: @Composable () -> Unit) {
    ITConnectTheme(content = content)
}

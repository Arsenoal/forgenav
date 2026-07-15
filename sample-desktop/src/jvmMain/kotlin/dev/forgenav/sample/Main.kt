package dev.forgenav.sample

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ForgeNav Sample",
        state = rememberWindowState(width = 960.dp, height = 720.dp),
    ) {
        // Escape / Ctrl+[ back is handled inside SampleApp via onPreviewKeyEvent
        SampleApp()
    }
}

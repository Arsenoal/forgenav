package dev.forgenav.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point for the Xcode host (`iosApp`).
 * Swift calls: `MainViewControllerKt.MainViewController()`
 */
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        SampleApp()
    }

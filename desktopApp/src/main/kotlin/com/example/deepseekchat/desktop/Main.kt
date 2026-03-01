package com.example.deepseekchat.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.deepseekchat.shared.DeepSeekChatApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DeepSeekChatApp"
    ) {
        DeepSeekChatApp()
    }
}

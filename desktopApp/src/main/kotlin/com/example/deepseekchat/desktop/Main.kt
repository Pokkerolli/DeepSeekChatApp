package com.example.deepseekchat.desktop

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.deepseekchat.shared.DeepSeekChatApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DeepSeekChatApp"
    ) {
        DeepSeekChatApp()

        Spacer(modifier = Modifier.padding(2.dp))
    }
}

package com.example.deepseekchat.shared.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatRoute(onOpenSettings: () -> Unit) {
    val store = remember { ChatStore() }
    val state by store.uiState.collectAsState()

    ChatScreen(
        state = state,
        onInputChanged = store::onInputChanged,
        onSendClicked = store::onSendClicked,
        onOpenSettings = onOpenSettings
    )
}

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DeepSeek Chat",
                style = MaterialTheme.typography.h6
            )
            Button(onClick = onOpenSettings) {
                Text("Settings")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = state.messages,
                key = { message -> message.id }
            ) { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = message.author,
                            style = MaterialTheme.typography.caption
                        )
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.body1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                label = { Text("Message") }
            )
            Button(
                onClick = onSendClicked,
                modifier = Modifier.width(100.dp)
            ) {
                Text("Send")
            }
        }
    }
}

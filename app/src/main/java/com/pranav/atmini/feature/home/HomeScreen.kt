package com.pranav.atmini.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    onVoiceClick: () -> Unit,
    onMapsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = HomeViewModel()
) {

    val state = viewModel.state.collectAsStateWithLifecycle().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = when (state.status) {
                AssistantStatus.Idle -> "🟢 Idle"
                AssistantStatus.Listening -> "🎤 Listening..."
                AssistantStatus.Thinking -> "🧠 Thinking..."
                AssistantStatus.Acting -> "⚙️ Acting..."
            }
        )

        Text(
            text = "Atmini",
            style = MaterialTheme.typography.headlineLarge
        )

        Divider()

        Text(text = "🎤 Heard: ${state.lastHeard}")
        Text(
            text = ActionUiMapper.toUiText(state.lastAction),
            style = MaterialTheme.typography.bodyLarge
        )

        Divider()

        Button(
            onClick = onMapsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Maps 📍")
        }

        Button(
            onClick = onVoiceClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Talk to Atmini 🎤")
        }
    }
}
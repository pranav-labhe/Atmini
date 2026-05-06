package com.pranav.atmini.feature.home

import androidx.lifecycle.ViewModel
import com.pranav.atmini.feature.action.AtminiAction
import com.pranav.atmini.feature.app.InstalledApp
import com.pranav.atmini.feature.command.CommandRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HomeState(
    val lastHeard: String = "",
    val lastAction: AtminiAction = AtminiAction.None,
    val status: AssistantStatus = AssistantStatus.Idle
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state
    private var apps: List<InstalledApp> = emptyList()

    fun setApps(list: List<InstalledApp>) {
        apps = list
    }
    fun onVoiceInput(text: String?) {

        // 1. Listening finished → Thinking starts
        _state.value = _state.value.copy(
            status = AssistantStatus.Thinking,
            lastHeard = text ?: ""
        )

        val action = CommandRegistry.process(text, apps)

        // 2. Acting phase
        _state.value = _state.value.copy(
            status = AssistantStatus.Acting,
            lastHeard = text ?: "",
            lastAction = action
        )

        // 3. Done → back to idle
        _state.value = _state.value.copy(
            status = AssistantStatus.Idle
        )
    }
}
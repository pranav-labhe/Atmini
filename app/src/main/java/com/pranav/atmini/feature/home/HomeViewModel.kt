package com.pranav.atmini.feature.home

import androidx.lifecycle.ViewModel
import com.pranav.atmini.core.l0.AtminiAction
import com.pranav.atmini.core.l0.AtminiBrain
import com.pranav.atmini.core.l0.ReflexResult
import com.pranav.atmini.feature.app.InstalledApp
import com.pranav.atmini.core.l0.command.CommandRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HomeState(
    val lastHeard: String = "",
    val spokenText: String = "",
    val lastAction: AtminiAction = AtminiAction.None,
    val status: AssistantStatus = AssistantStatus.Idle
)

class HomeViewModel : ViewModel() {
    val brain = AtminiBrain()
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state
    private var apps: List<InstalledApp> = emptyList()

    fun setApps(list: List<InstalledApp>) {
        apps = list
    }
    fun onActionExecuted(result: ReflexResult) {

        _state.value = _state.value.copy(
            lastAction = result.action,
            spokenText = result.spokenText,
            status = AssistantStatus.Idle
        )
    }

    fun onVoiceInput(text: String?) {

        _state.value = _state.value.copy(
            status = AssistantStatus.Thinking,
            lastHeard = text ?: ""
        )

        val action = brain.process(text, apps)

        _state.value = _state.value.copy(
            status = AssistantStatus.Acting,
            lastAction = action
        )

        _state.value = _state.value.copy(
            status = AssistantStatus.Idle
        )
    }
}
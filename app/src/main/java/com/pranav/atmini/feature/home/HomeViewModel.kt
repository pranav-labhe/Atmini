package com.pranav.atmini.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import com.pranav.atmini.core.VoiceCommandEngine
import com.pranav.atmini.core.l0.AtminiAction
import com.pranav.atmini.core.l0.AtminiBrain
import com.pranav.atmini.core.l0.ReflexResult
import com.pranav.atmini.feature.app.InstalledApp
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
    private var commandEngine: VoiceCommandEngine? = null

    fun setApps(list: List<InstalledApp>) {
        apps = list
    }

    fun initEngine(context: Context) {
        if (commandEngine == null) {
            commandEngine = VoiceCommandEngine(context.applicationContext)
        }
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

        if (!text.isNullOrBlank() && commandEngine != null) {
            commandEngine?.processTranscript(text) { statusMsg ->
                _state.value = _state.value.copy(
                    spokenText = statusMsg,
                    status = if (statusMsg.startsWith("Done") || statusMsg.startsWith("Execution failed")) AssistantStatus.Idle else AssistantStatus.Acting
                )
            }
        } else {
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
}

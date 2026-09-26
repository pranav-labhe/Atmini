package com.pranav.atmini.core

import android.content.Context
import com.pranav.atmini.core.l0.ai.FastRuleIntentModel
import com.pranav.atmini.core.l0.ai.LocalIntentModel
import com.pranav.atmini.core.l0.model.ExecutionResult
import com.pranav.atmini.core.l0.model.ValidatedCommand
import com.pranav.atmini.core.l0.resolver.AppResolver
import com.pranav.atmini.core.l0.resolver.ContactResolver
import com.pranav.atmini.core.l0.router.ActionRouter
import com.pranav.atmini.core.l0.validation.CommandValidator
import com.pranav.atmini.feature.voice.speech.SttEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceCommandEngine(
    private val context: Context,
    private val intentModel: LocalIntentModel = FastRuleIntentModel(),
    private val validator: CommandValidator = CommandValidator(),
    private val actionRouter: ActionRouter = ActionRouter(
        context = context,
        appResolver = AppResolver(context),
        contactResolver = ContactResolver(context)
    )
) {
    /**
     * Entry point when a wake word is triggered. Initiates speech recognition and routes output.
     */
    fun onWakeWordTriggered(onStatusUpdate: (String) -> Unit) {
        onStatusUpdate("Listening for command...")

        val sttEngine = SttEngineFactory.create(context)
        sttEngine.listen(
            onResult = { transcript ->
                onStatusUpdate("Recognized: '$transcript'")
                processTranscript(transcript, onStatusUpdate)
            },
            onError = { error ->
                onStatusUpdate("STT Error: $error")
            }
        )
    }

    /**
     * Directly processes a recognized transcript text through the deterministic pipeline.
     */
    fun processTranscript(transcript: String, onStatusUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            onStatusUpdate("Compiling semantic intent...")
            val rawJson = intentModel.parseToStructuredJson(transcript)

            onStatusUpdate("Validating command structure...")
            val command = validator.validate(rawJson)

            onStatusUpdate("Resolving entities & applying policy...")
            val result = actionRouter.execute(command)

            when (result) {
                is ExecutionResult.Success -> onStatusUpdate("Done: ${result.message}")
                is ExecutionResult.NeedsConfirmation -> {
                    onStatusUpdate("Confirmation required: ${result.promptMessage}")
                }
                is ExecutionResult.NeedsClarification -> {
                    onStatusUpdate("Disambiguation required: ${result.promptMessage} (${result.choices.joinToString()})")
                }
                is ExecutionResult.Failure -> onStatusUpdate("Execution failed: ${result.error}")
            }
        }
    }

    /**
     * Executes a confirmed command bypassing policy check (e.g. after user confirms phone call).
     */
    fun confirmAndExecute(command: ValidatedCommand): ExecutionResult {
        return actionRouter.execute(command, bypassPolicy = true)
    }
}

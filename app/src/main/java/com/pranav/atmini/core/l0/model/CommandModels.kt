package com.pranav.atmini.core.l0.model

import java.time.LocalDateTime
import java.time.LocalTime

enum class ActionType {
    OPEN_APP,
    CALL_CONTACT,
    NAVIGATE,
    SET_TIMER,
    SET_ALARM,
    SET_REMINDER,
    UNKNOWN
}

/**
 * Raw JSON schema output extracted from the LLM/SLM.
 * Uses semantic time expressions instead of asking the SLM to calculate absolute timestamps.
 */
data class RawParsedCommand(
    val action: String? = null,
    val target_name: String? = null,
    val time: String? = null,                   // Format "HH:mm"
    val day_offset: Int? = null,                 // 0 = today, 1 = tomorrow
    val relative_delay_seconds: Long? = null,    // Relative timer/delay duration in seconds
    val reminder_text: String? = null
)

/**
 * Strongly-typed commands validated by Kotlin business logic.
 */
sealed class ValidatedCommand {
    data class OpenApp(val appQuery: String) : ValidatedCommand()
    data class CallContact(val contactQuery: String) : ValidatedCommand()
    data class Navigate(val destination: String) : ValidatedCommand()
    data class SetTimer(val durationSeconds: Long) : ValidatedCommand()
    data class SetAlarm(val alarmTime: LocalTime) : ValidatedCommand()
    data class SetReminder(
        val reminderText: String,
        val triggerDateTime: LocalDateTime
    ) : ValidatedCommand()
    object Invalid : ValidatedCommand()
}

/**
 * Type-safe resolution state for fuzzy-matched local entities.
 */
sealed class ResolutionResult<out T> {
    data class SingleMatch<T>(val item: T) : ResolutionResult<T>()
    data class Ambiguous<T>(
        val candidates: List<T>,
        val query: String
    ) : ResolutionResult<T>()
    object NoMatch : ResolutionResult<Nothing>()
}

/**
 * Policy rules for command execution.
 */
data class CommandPolicy(
    val requiresConfirmation: Boolean,
    val description: String
)

/**
 * Result returned after policy checking and system action execution.
 */
sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class NeedsConfirmation(
        val command: ValidatedCommand,
        val promptMessage: String
    ) : ExecutionResult()
    data class NeedsClarification(
        val promptMessage: String,
        val choices: List<String>
    ) : ExecutionResult()
    data class Failure(val error: String) : ExecutionResult()
}

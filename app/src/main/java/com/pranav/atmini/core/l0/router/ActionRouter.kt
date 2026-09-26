package com.pranav.atmini.core.l0.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.pranav.atmini.core.l0.model.*
import com.pranav.atmini.core.l0.resolver.AppCandidate
import com.pranav.atmini.core.l0.resolver.AppResolver
import com.pranav.atmini.core.l0.resolver.ContactCandidate
import com.pranav.atmini.core.l0.resolver.ContactResolver
import java.time.ZoneId

object PolicyEngine {
    fun getPolicy(action: ActionType): CommandPolicy = when (action) {
        ActionType.CALL_CONTACT -> CommandPolicy(
            requiresConfirmation = true,
            description = "Initiating an external phone call requires confirmation."
        )
        ActionType.OPEN_APP,
        ActionType.SET_TIMER,
        ActionType.SET_ALARM,
        ActionType.SET_REMINDER,
        ActionType.NAVIGATE,
        ActionType.UNKNOWN -> CommandPolicy(
            requiresConfirmation = false,
            description = "Direct execution allowed."
        )
    }
}

class ActionRouter(
    private val context: Context,
    private val appResolver: AppResolver,
    private val contactResolver: ContactResolver
) {

    fun execute(command: ValidatedCommand, bypassPolicy: Boolean = false): ExecutionResult {
        return when (command) {
            is ValidatedCommand.OpenApp -> handleOpenApp(command.appQuery)
            is ValidatedCommand.CallContact -> handleCallContact(command, bypassPolicy)
            is ValidatedCommand.Navigate -> handleNavigate(command.destination)
            is ValidatedCommand.SetTimer -> handleSetTimer(command.durationSeconds)
            is ValidatedCommand.SetAlarm -> handleSetAlarm(command)
            is ValidatedCommand.SetReminder -> handleSetReminder(command)
            is ValidatedCommand.Invalid -> ExecutionResult.Failure("Invalid command structure.")
        }
    }

    private fun handleOpenApp(appQuery: String): ExecutionResult {
        return when (val res = appResolver.resolveApp(appQuery)) {
            is ResolutionResult.SingleMatch<AppCandidate> -> {
                val intent = context.packageManager.getLaunchIntentForPackage(res.item.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: return ExecutionResult.Failure("App launch intent unavailable.")
                context.startActivity(intent)
                ExecutionResult.Success("Opening ${res.item.label}")
            }
            is ResolutionResult.Ambiguous<AppCandidate> -> {
                ExecutionResult.NeedsClarification(
                    "Which app did you mean?",
                    res.candidates.map { it.label }
                )
            }
            is ResolutionResult.NoMatch -> ExecutionResult.Failure("App '$appQuery' not found.")
        }
    }

    private fun handleCallContact(command: ValidatedCommand.CallContact, bypassPolicy: Boolean): ExecutionResult {
        val policy = PolicyEngine.getPolicy(ActionType.CALL_CONTACT)
        if (policy.requiresConfirmation && !bypassPolicy) {
            return ExecutionResult.NeedsConfirmation(command, "Call ${command.contactQuery}?")
        }

        return when (val res = contactResolver.resolveContact(command.contactQuery)) {
            is ResolutionResult.SingleMatch<ContactCandidate> -> {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${res.item.number}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(callIntent)
                    ExecutionResult.Success("Calling ${res.item.name}")
                } catch (_: Exception) {
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${res.item.number}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    ExecutionResult.Success("Dialing ${res.item.name}")
                }
            }
            is ResolutionResult.Ambiguous<ContactCandidate> -> {
                ExecutionResult.NeedsClarification(
                    "Which contact did you mean?",
                    res.candidates.map { "${it.name} (${it.number})" }
                )
            }
            is ResolutionResult.NoMatch -> ExecutionResult.Failure("Contact '${command.contactQuery}' not found.")
        }
    }

    private fun handleNavigate(destination: String): ExecutionResult {
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(destination)}")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(mapIntent)
            ExecutionResult.Success("Navigating to $destination")
        } catch (_: Exception) {
            val genericGeoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(genericGeoIntent)
                ExecutionResult.Success("Navigating to $destination")
            } catch (e: Exception) {
                ExecutionResult.Failure("Maps application unavailable.")
            }
        }
    }

    private fun handleSetTimer(durationSeconds: Long): ExecutionResult {
        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds.toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(timerIntent)
            ExecutionResult.Success("Timer set for $durationSeconds seconds")
        } catch (e: Exception) {
            ExecutionResult.Failure("Timer app unavailable.")
        }
    }

    private fun handleSetAlarm(alarm: ValidatedCommand.SetAlarm): ExecutionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarm.alarmTime.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, alarm.alarmTime.minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(alarmIntent)
                ExecutionResult.Success("Alarm set for %02d:%02d".format(alarm.alarmTime.hour, alarm.alarmTime.minute))
            } catch (e: Exception) {
                ExecutionResult.Failure("Clock app unavailable.")
            }
        }
        return ExecutionResult.Failure("Setting alarm requires API level 26+")
    }

    private fun handleSetReminder(reminder: ValidatedCommand.SetReminder): ExecutionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val startMillis = reminder.triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, reminder.reminderText)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                ExecutionResult.Success("Reminder created: '${reminder.reminderText}'")
            } catch (e: Exception) {
                ExecutionResult.Failure("Calendar app unavailable.")
            }
        }
        return ExecutionResult.Failure("Setting reminder requires API level 26+")
    }
}

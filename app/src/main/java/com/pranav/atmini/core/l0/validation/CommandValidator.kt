package com.pranav.atmini.core.l0.validation

import android.os.Build
import androidx.annotation.RequiresApi
import com.pranav.atmini.core.l0.model.ActionType
import com.pranav.atmini.core.l0.model.RawParsedCommand
import com.pranav.atmini.core.l0.model.ValidatedCommand
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CommandValidator {

    /**
     * Sanitizes raw LLM output, parses JSON, and validates command structure.
     */
    fun validate(rawJson: String): ValidatedCommand {
        val parsed = parseRawJson(rawJson) ?: return ValidatedCommand.Invalid

        val actionEnum = try {
            parsed.action?.uppercase()?.let { ActionType.valueOf(it) } ?: ActionType.UNKNOWN
        } catch (_: IllegalArgumentException) {
            ActionType.UNKNOWN
        }

        return when (actionEnum) {
            ActionType.OPEN_APP -> {
                val query = parsed.target_name
                if (!query.isNullOrBlank()) ValidatedCommand.OpenApp(query.trim())
                else ValidatedCommand.Invalid
            }
            ActionType.CALL_CONTACT -> {
                val contact = parsed.target_name
                if (!contact.isNullOrBlank()) ValidatedCommand.CallContact(contact.trim())
                else ValidatedCommand.Invalid
            }
            ActionType.NAVIGATE -> {
                val dest = parsed.target_name
                if (!dest.isNullOrBlank()) ValidatedCommand.Navigate(dest.trim())
                else ValidatedCommand.Invalid
            }
            ActionType.SET_TIMER -> {
                val seconds = parsed.relative_delay_seconds
                if (seconds != null && seconds > 0) ValidatedCommand.SetTimer(seconds)
                else ValidatedCommand.Invalid
            }
            ActionType.SET_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val parsedTime = parseLocalTime(parsed.time)
                    if (parsedTime != null) ValidatedCommand.SetAlarm(parsedTime)
                    else ValidatedCommand.Invalid
                } else {
                    ValidatedCommand.Invalid
                }
            }
            ActionType.SET_REMINDER -> {
                val text = parsed.reminder_text ?: parsed.target_name ?: "Reminder"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val dateTime = resolveSemanticDateTime(parsed.time, parsed.day_offset, parsed.relative_delay_seconds)
                    if (dateTime != null) ValidatedCommand.SetReminder(text, dateTime)
                    else ValidatedCommand.Invalid
                } else {
                    ValidatedCommand.Invalid
                }
            }
            ActionType.UNKNOWN -> ValidatedCommand.Invalid
        }
    }

    private fun parseRawJson(rawJson: String): RawParsedCommand? {
        return try {
            val cleaned = cleanJson(rawJson)
            val json = JSONObject(cleaned)

            RawParsedCommand(
                action = if (json.has("action") && !json.isNull("action")) json.getString("action") else null,
                target_name = if (json.has("target_name") && !json.isNull("target_name")) json.getString("target_name") else null,
                time = if (json.has("time") && !json.isNull("time")) json.getString("time") else null,
                day_offset = if (json.has("day_offset") && !json.isNull("day_offset")) json.optInt("day_offset") else null,
                relative_delay_seconds = if (json.has("relative_delay_seconds") && !json.isNull("relative_delay_seconds")) json.optLong("relative_delay_seconds") else null,
                reminder_text = if (json.has("reminder_text") && !json.isNull("reminder_text")) json.getString("reminder_text") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseLocalTime(timeStr: String?): LocalTime? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveSemanticDateTime(timeStr: String?, dayOffset: Int?, relativeSeconds: Long?): LocalDateTime? {
        val now = LocalDateTime.now()
        if (relativeSeconds != null && relativeSeconds > 0) {
            return now.plusSeconds(relativeSeconds)
        }
        val targetTime = parseLocalTime(timeStr) ?: return null
        val targetDate = LocalDate.now().plusDays((dayOffset ?: 0).toLong())
        return LocalDateTime.of(targetDate, targetTime)
    }

    private fun cleanJson(input: String): String {
        val start = input.indexOf('{')
        val end = input.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) input.substring(start, end + 1) else "{}"
    }
}

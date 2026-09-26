# Local Voice Command Engine Architecture

This document presents the complete architectural specification and implementation plan for **LocalVoiceAssistant** — an offline, privacy-first Android voice command engine.

Instead of treating an on-device Small Language Model (SLM) as an autonomous decision maker, this engine models the LLM as a constrained Natural Language to Structured Command Compiler. The downstream Kotlin validation and execution pipeline is deterministic:

$$\text{Speech} \longrightarrow \text{Text} \longrightarrow \text{Semantic Compilation (LLM)} \longrightarrow \text{Validation/Disambiguation} \longrightarrow \text{Android Execution}$$

---

## 1. System Architecture & Information Pipeline

```
+-----------------------------------------------------------------------------------+
|                                 AUDIO SUBSYSTEM                                   |
|  [ Microphones ] --> ( AudioRecord 16kHz PCM )                                    |
|                             |                                                     |
|                             v                                                     |
|                     [ RMS Energy Gate ]                                           |
|                             | (Above Noise Floor)                                 |
|                             v                                                     |
|              [ WakeWordDetector (Stateful Engine) ]                               |
|        - Feature Extractor (Log-Mel Spectrogram / Stride Buffer)                  |
|        - Init Signature Validation (Validates tensor shape on setup)              |
|                             | (Wake Word Detected: "Hey Gemma")                   |
+-----------------------------|-----------------------------------------------------+
                              v
+-----------------------------------------------------------------------------------+
|                        OFFLINE SPEECH-TO-TEXT (STT) ENGINE                        |
|                     [ Hybrid SttEngine Factory / Abstraction ]                    |
|         - API 31+ Check -> SpeechRecognizer.createOnDeviceSpeechRecognizer        |
|         - Fallback      -> LocalWhisperSttEngine Stub (Phase 2, isAvailable=false) |
|                             | (Transcript Output)                                 |
+-----------------------------|-----------------------------------------------------+
                              v
+-----------------------------------------------------------------------------------+
|                     LOCAL SEMANTIC COMPILER (Gemma / SLM)                         |
|                     [ LocalIntentModel Abstraction ]                              |
|         - Configurable Runtime & Dynamic Benchmark Enforcer                       |
|         - Zero-shot JSON extraction with Semantic Time (No raw ISO calculation)   |
|                             | (Raw Unvalidated JSON)                              |
+-----------------------------|-----------------------------------------------------+
                              v
+-----------------------------------------------------------------------------------+
|                        TRUST BOUNDARY & COMMAND VALIDATOR                         |
|                       [ CommandValidator (Kotlin Rules) ]                         |
|         - Structural Parsing & Type Normalization                                 |
|         - Rejection of unknown keys or invalid parameter combinations             |
|         - Semantic Time Resolution (Resolves HH:mm & offset via System Clock)       |
|                             | (Validated Command Object)                          |
+-----------------------------|-----------------------------------------------------+
                              v
+-----------------------------------------------------------------------------------+
|                     DETERMINISTIC ENTITY RESOLUTION LAYER                         |
|    +----------------------------------+------------------------------------+      |
|    | AppResolver                      | ContactResolver                    |      |
|    | - Levenshtein Matching           | - Jaro-Winkler Matching            |      |
|    | - Returns ResolutionResult    | - Returns ResolutionResult      |      |
|    +----------------------------------+------------------------------------+      |
|                             |                                                     |
|           [ Single Match ]  |  [ Ambiguous Match (Delta < 0.12) ]                 |
|                             v                                                     |
+-----------------------------|-----------------------------------------------------+
                              |
     +------------------------+------------------------+
     |                                                 |
     v                                                 v
+----------------------------------+  +---------------------------------------------+
| POLICY ENGINE & ACTION ROUTER    |  | CLARIFICATION / DIALOGUE SYSTEM             |
| - CALL_CONTACT -> Policy Check   |  | - Prompt user: "Did you mean Spotify or     |
|   (requires confirmation)        |  |   Spotify Lite?"                            |
| - OPEN_APP     -> Package Launch |  +---------------------------------------------+
| - SET_TIMER    -> AlarmClock     |
| - SET_ALARM    -> AlarmClock     |
| - SET_REMINDER -> Calendar/Alarm |
+----------------------------------+

```

---

## 2. Dependencies & Runtime Instructions

> **Coding Agent Directive:** Before writing implementation code, inspect the currently supported Google AI Edge Android runtime, dependency coordinates, model formats, and Gemma integrations from official Google documentation. Do not assume `com.google.mediapipe:tasks-genai:0.10.14` or hardcoded bindings.

### `build.gradle.kts` (App Module)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.localvoiceassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.localvoiceassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.addAll(setOf("arm64-v8a"))
        }
    }
}

dependencies {
    // Google AI Edge / LiteRT / MediaPipe ecosystem bindings
    implementation("com.google.ai.edge.litert:litert:1.0.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")

    // Kotlin Utilities & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")
}

```

---

## 3. Core Data Models & Command Contracts (`CommandModels.kt`)

```kotlin
package com.example.localvoiceassistant.model

import kotlinx.serialization.Serializable

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
 * Raw JSON schema output extracted from the LLM.
 * Note: Uses semantic time expressions instead of asking the SLM to calculate absolute timestamps.
 */
@Serializable
data class RawParsedCommand(
    val action: String? = null,
    val target_name: String? = null,
    val time: String? = null,                   // Format "HH:mm"
    val day_offset: Int? = null,                 // 0 = today, 1 = tomorrow
    val relative_delay_seconds: Long? = null,    // Relative timer/delay duration
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
    data class SetAlarm(val alarmTime: java.time.LocalTime) : ValidatedCommand()
    data class SetReminder(
        val reminderText: String,
        val triggerDateTime: java.time.LocalDateTime
    ) : ValidatedCommand()
    data object Invalid : ValidatedCommand()
}

/**
 * Type-safe resolution state for fuzzy-matched local entities.
 */
sealed class ResolutionResult {
    data class SingleMatch(val item: T) : ResolutionResult()
    data class Ambiguous(
        val candidates: List,
        val query: String
    ) : ResolutionResult()
    data object NoMatch : ResolutionResult()
}

/**
 * Policy rules for command execution.
 */
data class CommandPolicy(
    val requiresConfirmation: Boolean,
    val description: String
)

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class NeedsConfirmation(val command: ValidatedCommand, val promptMessage: String) : ExecutionResult()
    data class NeedsClarification(val promptMessage: String, val choices: List) : ExecutionResult()
    data class Failure(val error: String) : ExecutionResult()
}

```

---

## 4. Configurable Intent Parsing Layer (`LocalIntentModel.kt`)

```kotlin
package com.example.localvoiceassistant.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ModelConfig(
    val modelPath: String,
    val maxTokens: Int = 128,
    val temperature: Float = 0.0f,
    val maxMemoryMb: Int = 1200,
    val targetLatencyMs: Long = 800
)

interface LocalIntentModel {
    suspend fun initialize(): Boolean
    suspend fun parseToStructuredJson(transcript: String): String
    fun close()
}

/**
 * Gemma 3 270M adapter implementing LocalIntentModel.
 */
class GemmaIntentModel(
    private val context: Context,
    private val config: ModelConfig
) : LocalIntentModel {

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        // Instantiate target LiteRT / AI Edge runtime using verified APIs
        true
    }

    override suspend fun parseToStructuredJson(transcript: String): String = withContext(Dispatchers.Default) {
        val prompt = buildConstrainedPrompt(transcript)
        return@withContext executeInference(prompt)
    }

    private fun buildConstrainedPrompt(query: String): String {
        return """
            user
            Extract intent from user speech into strict raw JSON. Do not include markdown or reasoning.
            Allowed actions: OPEN_APP, CALL_CONTACT, NAVIGATE, SET_TIMER, SET_ALARM, SET_REMINDER, UNKNOWN.

            JSON Schema:
            {
              "action": "ACTION_NAME",
              "target_name": "string or null",
              "time": "HH:mm or null",
              "day_offset": integer_or_null,
              "relative_delay_seconds": integer_or_null,
              "reminder_text": "string or null"
            }

            Rules:
            - Do not calculate absolute calendar timestamps. Output semantic time fields.
            - For timers, output "relative_delay_seconds".
            - For alarms, output "time" in "HH:mm" (24-hour).
            - For reminders, output "reminder_text", "time", and "day_offset" (0 for today, 1 for tomorrow).

            Input: "$query"
            Output:
            model
        """.trimIndent()
    }

    private fun executeInference(prompt: String): String {
        // Native runtime inference invocation stub
        return "{}"
    }

    override fun close() {}
}

```

---

## 5. Stateful Wake-Word Engine (`WakeWordDetector.kt`)

> **Coding Agent Directive:** Validate the model signature strictly during initialization and fail clearly if incompatible. Do not execute signature reflection or tensor shape dynamic inspection inside per-frame audio processing loops.

```kotlin
package com.example.localvoiceassistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import kotlin.math.sqrt

interface AudioFeatureExtractor {
    fun processPcmFrame(pcmChunk: ShortArray): FloatArray?
}

class WakeWordDetector(
    private val modelBuffer: ByteBuffer,
    private val featureExtractor: AudioFeatureExtractor,
    private val onWakeWordDetected: () -> Unit
) {
    private var isListening = false
    private var audioRecord: AudioRecord? = null
    private var interpreter: Interpreter? = null

    fun start() {
        if (isListening) return

        val tflite = Interpreter(modelBuffer)
        validateModelSignature(tflite)
        interpreter = tflite
        isListening = true

        @SuppressLint("MissingPermission")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        )

        audioRecord?.startRecording()

        CoroutineScope(Dispatchers.IO).launch {
            val pcmFrame = ShortArray(512)
            val outputTensor = Array(1) { FloatArray(1) }

            while (isListening) {
                val readSize = audioRecord?.read(pcmFrame, 0, pcmFrame.size) ?: 0
                if (readSize > 0 && calculateRms(pcmFrame, readSize) > 250.0) {
                    val features = featureExtractor.processPcmFrame(pcmFrame)
                    if (features != null) {
                        interpreter?.run(features, outputTensor)
                        val triggerScore = outputTensor[0][0]
                        if (triggerScore > 0.80f) {
                            withContext(Dispatchers.Main) { onWakeWordDetected() }
                            delay(1200) // Cooldown period
                        }
                    }
                }
            }
        }
    }

    private fun validateModelSignature(model: Interpreter) {
        val inputShape = model.getInputTensor(0).shape()
        require(inputShape.contentEquals(intArrayOf(1, 40, 32))) {
            "Incompatible wake-word model tensor signature: ${inputShape.contentToString()}. Expected [1, 40, 32]."
        }
    }

    fun stop() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        interpreter?.close()
    }

    private fun calculateRms(audioData: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) sum += audioData[i] * audioData[i]
        return sqrt(sum / size)
    }
}

```

---

## 6. Offline Speech-To-Text Layer (`SttEngine.kt`)

> **Coding Agent Directive:** Implement the fallback interface stub, but do not pretend the fallback is available (`isAvailable = false`) until an actual offline engine is integrated.

```kotlin
package com.example.localvoiceassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface SttEngine {
    val isAvailable: Boolean
    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun destroy()
}

class SystemOnDeviceSttEngine(private val context: Context) : SttEngine {
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!isAvailable) {
            onError("System on-device SpeechRecognizer is unavailable.")
            return
        }

        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) onResult(matches[0]) else onError("No speech detected.")
            }
            override fun onError(error: Int) { onError("STT Error Code: $error") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}

class LocalWhisperSttEngine : SttEngine {
    // Explicit Phase 2 stub to prevent false claims of offline STT availability
    override val isAvailable: Boolean = false

    override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onError("Local offline Whisper/Vosk STT engine is scheduled for Phase 2.")
    }

    override fun destroy() {}
}

object SttEngineFactory {
    fun create(context: Context): SttEngine {
        val systemEngine = SystemOnDeviceSttEngine(context)
        return if (systemEngine.isAvailable) {
            systemEngine
        } else {
            LocalWhisperSttEngine()
        }
    }
}

```

---

## 7. Deterministic Command Validator (`CommandValidator.kt`)

```kotlin
package com.example.localvoiceassistant.validation

import com.example.localvoiceassistant.model.ActionType
import com.example.localvoiceassistant.model.RawParsedCommand
import com.example.localvoiceassistant.model.ValidatedCommand
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CommandValidator {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun validate(rawJson: String): ValidatedCommand {
        val parsed = runCatching {
            jsonParser.decodeFromString(cleanJson(rawJson))
        }.getOrNull() ?: return ValidatedCommand.Invalid

        val actionEnum = try {
            parsed.action?.uppercase()?.let { ActionType.valueOf(it) } ?: ActionType.UNKNOWN
        } catch (e: IllegalArgumentException) {
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
                val parsedTime = parseLocalTime(parsed.time)
                if (parsedTime != null) ValidatedCommand.SetAlarm(parsedTime)
                else ValidatedCommand.Invalid
            }
            ActionType.SET_REMINDER -> {
                val text = parsed.reminder_text ?: parsed.target_name ?: "Reminder"
                val dateTime = resolveSemanticDateTime(parsed.time, parsed.day_offset, parsed.relative_delay_seconds)
                if (dateTime != null) ValidatedCommand.SetReminder(text, dateTime)
                else ValidatedCommand.Invalid
            }
            ActionType.UNKNOWN -> ValidatedCommand.Invalid
        }
    }

    private fun parseLocalTime(timeStr: String?): LocalTime? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            null
        }
    }

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

```

---

## 8. Entity Resolvers with Type-Safe Ambiguity Delta Check

### App Resolver (`AppResolver.kt`)

```kotlin
package com.example.localvoiceassistant.resolvers

import android.content.Context
import android.content.Intent
import com.example.localvoiceassistant.model.ResolutionResult

data class AppCandidate(val packageName: String, val label: String, val score: Double)

class AppResolver(private val context: Context) {

    private val ambiguityDeltaThreshold = 0.12

    fun resolveApp(query: String): ResolutionResult {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(mainIntent, 0)

        val candidates = apps.map { app ->
            val label = app.loadLabel(pm).toString()
            val score = calculateSimilarity(query.lowercase(), label.lowercase())
            AppCandidate(app.activityInfo.packageName, label, score)
        }.filter { it.score > 0.60 }.sortedByDescending { it.score }

        if (candidates.isEmpty()) return ResolutionResult.NoMatch
        if (candidates.size == 1) return ResolutionResult.SingleMatch(candidates[0])

        val top = candidates[0]
        val second = candidates[1]

        if (top.score >= 0.98) return ResolutionResult.SingleMatch(top)

        return if ((top.score - second.score) < ambiguityDeltaThreshold) {
            ResolutionResult.Ambiguous(candidates.take(3), query)
        } else {
            ResolutionResult.SingleMatch(top)
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s2.contains(s1) || s1.contains(s2)) return 0.85
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (levenshtein(s1, s2).toDouble() / maxLen)
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }
        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = minOf(cost[j] + 1, newCost[j - 1] + 1, cost[j - 1] + match)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhs.length]
    }
}

```

### Contact Resolver (`ContactResolver.kt`)

```kotlin
package com.example.localvoiceassistant.resolvers

import android.content.Context
import android.provider.ContactsContract
import com.example.localvoiceassistant.model.ResolutionResult

data class ContactCandidate(val name: String, val number: String, val score: Double)

class ContactResolver(private val context: Context) {

    fun resolveContact(query: String): ResolutionResult {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        ) ?: return ResolutionResult.NoMatch

        val candidates = mutableListOf()

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numIdx) ?: continue
                val score = if (name.equals(query, ignoreCase = true)) 1.0 
                            else if (name.contains(query, ignoreCase = true)) 0.85 else 0.0
                if (score > 0.70) candidates.add(ContactCandidate(name, number, score))
            }
        }

        val sorted = candidates.sortedByDescending { it.score }
        if (sorted.isEmpty()) return ResolutionResult.NoMatch
        if (sorted.size == 1 || sorted[0].score == 1.0) return ResolutionResult.SingleMatch(sorted[0])

        return if ((sorted[0].score - sorted[1].score) < 0.15) {
            ResolutionResult.Ambiguous(sorted.take(3), query)
        } else {
            ResolutionResult.SingleMatch(sorted[0])
        }
    }
}

```

---

## 9. Policy Engine & Action Execution Router (`ActionRouter.kt`)

```kotlin
package com.example.localvoiceassistant.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.example.localvoiceassistant.model.*
import com.example.localvoiceassistant.resolvers.AppResolver
import com.example.localvoiceassistant.resolvers.ContactResolver
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
            is ResolutionResult.SingleMatch -> {
                val intent = context.packageManager.getLaunchIntentForPackage(res.item.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?: return ExecutionResult.Failure("App launch intent unavailable.")
                context.startActivity(intent)
                ExecutionResult.Success("Opening ${res.item.label}")
            }
            is ResolutionResult.Ambiguous -> {
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
            is ResolutionResult.SingleMatch -> {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${res.item.number}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                ExecutionResult.Success("Calling ${res.item.name}")
            }
            is ResolutionResult.Ambiguous -> {
                ExecutionResult.NeedsClarification(
                    "Which contact did you mean?",
                    res.candidates.map { "\({it.name} (\){it.number})" }
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
        return if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            ExecutionResult.Success("Navigating to $destination")
        } else {
            ExecutionResult.Failure("Maps application unavailable.")
        }
    }

    private fun handleSetTimer(durationSeconds: Long): ExecutionResult {
        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds.toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(timerIntent)
        return ExecutionResult.Success("Timer set for $durationSeconds seconds")
    }

    private fun handleSetAlarm(alarm: ValidatedCommand.SetAlarm): ExecutionResult {
        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarm.alarmTime.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.alarmTime.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(alarmIntent)
        return ExecutionResult.Success("Alarm set for %02d:%02d".format(alarm.alarmTime.hour, alarm.alarmTime.minute))
    }

    private fun handleSetReminder(reminder: ValidatedCommand.SetReminder): ExecutionResult {
        val startMillis = reminder.triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, reminder.reminderText)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionResult.Success("Reminder created: '${reminder.reminderText}'")
    }
}

```

---

## 10. System Orchestrator (`VoiceCommandEngine.kt`)

```kotlin
package com.example.localvoiceassistant.engine

import android.content.Context
import com.example.localvoiceassistant.ai.LocalIntentModel
import com.example.localvoiceassistant.model.ExecutionResult
import com.example.localvoiceassistant.router.ActionRouter
import com.example.localvoiceassistant.validation.CommandValidator
import com.example.localvoiceassistant.voice.SttEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceCommandEngine(
    private val context: Context,
    private val intentModel: LocalIntentModel,
    private val validator: CommandValidator,
    private val actionRouter: ActionRouter
) {
    fun onWakeWordTriggered(onStatusUpdate: (String) -> Unit) {
        onStatusUpdate("Listening for command...")

        val sttEngine = SttEngineFactory.create(context)
        sttEngine.listen(
            onResult = { transcript ->
                onStatusUpdate("Recognized: '$transcript'")
                processTranscript(transcript, onStatusUpdate)
            },
            onError = { error -> onStatusUpdate("STT Error: $error") }
        )
    }

    private fun processTranscript(transcript: String, onStatusUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            onStatusUpdate("Compiling semantic intent...")
            val rawJson = intentModel.parseToStructuredJson(transcript)

            onStatusUpdate("Validating command structure...")
            val command = validator.validate(rawJson)

            onStatusUpdate("Resolving entities & applying policy...")
            when (val result = actionRouter.execute(command)) {
                is ExecutionResult.Success -> onStatusUpdate("Done: ${result.message}")
                is ExecutionResult.NeedsConfirmation -> {
                    onStatusUpdate("Confirmation required: ${result.promptMessage}")
                }
                is ExecutionResult.NeedsClarification -> {
                    onStatusUpdate("Disambiguation required: \({result.promptMessage} (\){result.choices.joinToString()})")
                }
                is ExecutionResult.Failure -> onStatusUpdate("Execution failed: ${result.error}")
            }
        }
    }
}

```

---

## 11. Model Benchmarking & Verification Protocol

### Performance Benchmark Targets (Non-Fatal Target Measurements)

Latency and resource utilization targets are logged on reference devices for telemetry and optimization rather than serving as strict pass/fail runtime triggers.

| Target / Metric | Requirement Target | Measurement Method |
| --- | --- | --- |
| **Memory Ceiling** | Peak PSS RAM < 1200 MB | `adb shell dumpsys meminfo com.example.localvoiceassistant` |
| **Time To First Token** | TTFT < 400 ms | Timestamp delta from prompt submission to 1st token emission |
| **JSON Completion Latency** | Inference < 900 ms | Total duration of `parseToStructuredJson()` execution |
| **Battery Impact** | Report mA draw / 100 runs | Android Battery Historian / Energy Profiler |

### Semantic Intent Accuracy Matrix (Primary Success Criteria)

Evaluation suite executed over a standard benchmark set of 50–100 natural language command prompts:

| Test Case Prompt | Expected Action | Expected Parameters | Evaluation Criteria |
| --- | --- | --- | --- |
| `"open YouTube"` | `OPEN_APP` | `target_name: "YouTube"` | Direct Intent |
| `"launch Settings"` | `OPEN_APP` | `target_name: "Settings"` | Synonym Intent |
| `"call Mom"` | `CALL_CONTACT` | `target_name: "Mom"` | Direct Contact |
| `"call him"` | `UNKNOWN` | — | Ambiguous Pronoun Rejection |
| `"set a timer for 10 minutes"` | `SET_TIMER` | `relative_delay_seconds: 600` | Duration Unit Parsing |
| `"set an alarm for 7:30"` | `SET_ALARM` | `time: "07:30"` | Clock Parsing |
| `"remind me tomorrow at 8pm to call Rahul"` | `SET_REMINDER` | `day_offset: 1`, `time: "20:00"`, `reminder_text: "call Rahul"` | Semantic Time Parsing |
| `"what is the capital of France?"` | `UNKNOWN` | — | Out-of-Domain Safety |
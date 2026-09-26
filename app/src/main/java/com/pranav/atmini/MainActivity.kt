package com.pranav.atmini

import WakeSignatureStore
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pranav.atmini.core.l0.AtminiAction
import com.pranav.atmini.core.l0.L0ReflexSystem
import com.pranav.atmini.feature.app.AppDiscovery
import com.pranav.atmini.feature.home.HomeScreen
import com.pranav.atmini.feature.home.HomeViewModel
import com.pranav.atmini.feature.voice.VoiceManager
import com.pranav.atmini.feature.voice.wake.WakeService
import com.pranav.atmini.ui.theme.AtminiTheme

import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import com.pranav.atmini.feature.voice.overlay.FloatingAtminiService
import com.pranav.atmini.feature.voice.wake.FeatureExtractor
import com.pranav.atmini.feature.voice.wake.WakeEnrollmentController

class MainActivity : ComponentActivity() {

    private lateinit var enrollmentController: WakeEnrollmentController
    private var lastSpeech: Boolean = false
    lateinit var enrollmentControllerRef: WakeEnrollmentController
    private val AUDIO_PERMISSION_REQUEST = 1001
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private lateinit var voiceManager: VoiceManager
    private var viewModelRef: HomeViewModel? = null

    private val wakeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            Log.d("AtminiUI", "🔥 RECEIVED WAKE BROADCAST: ${intent?.action}")

            if (intent?.action == "ATMINI_VOICE_HEARD") {
                val heardText = intent.getStringExtra("voice_text")
                if (!heardText.isNullOrBlank()) {
                    viewModelRef?.onVoiceInput(heardText)
                }
            } else if (intent?.action == "ATMINI_WAKE_DETECTED") {
                val voiceText = intent.getStringExtra("voice_text")
                try {
                    Toast.makeText(this@MainActivity, "Atmini: $voiceText 🎙️", Toast.LENGTH_SHORT).show()
                    playWakeSound()
                } catch (e: Exception) {
                    Log.d("AtminiUI", "Wake stat error $e")
                }
            }
        }
    }
    private fun startSpeech() {

        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Atmini listening...")
        }

        speechLauncher.launch(intent)
    }
    private fun playWakeSound() {
        try {
            val mediaPlayer = android.media.MediaPlayer.create(
                this,
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            )
            mediaPlayer.setOnCompletionListener {
                it.release()
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("AtminiUI", "Wake sound failed", e)
        }
    }
    private fun ensureAudioPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                AUDIO_PERMISSION_REQUEST
            )

        } else {

            startWakeService()
        }
    }
    private fun startWakeService() {
        // Request overlay permission if needed, then start floating character and wake service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            val overlayIntent = Intent(this, FloatingAtminiService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
        }

        val wakeIntent = Intent(
            this,
            WakeService::class.java
        )

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {

            startForegroundService(wakeIntent)

        } else {

            startService(wakeIntent)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Log.d("AtminiUI", "New intent received:  ${intent?.action}")

        if (intent.action == "ATMINI_WAKE_DETECTED") {
            handleWakeFlow()
        }
    }
    private fun handleWakeFlow() {

        Log.d("AtminiUI", "🚀 Wake flow started in foreground")

        Toast.makeText(this, "Atmini activated 🎙️", Toast.LENGTH_SHORT).show()

        playWakeSound()

        startSpeech()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == AUDIO_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startWakeService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.d("AtminiUI", "OPend ACtivity with intent :  ${intent?.action}")

        if (intent?.action == "ATMINI_WAKE_DETECTED") {
            handleWakeFlow()
        }

        val extractor = FeatureExtractor()
        val store = WakeSignatureStore(this)

        enrollmentController = WakeEnrollmentController(
            store = store
        )

        val wakeIntent = Intent(
            this,
            WakeService::class.java
        )

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            ensureAudioPermission()
        } else {
            startService(wakeIntent)
        }
        enableEdgeToEdge()

        // ViewModel
        val viewModel = HomeViewModel()
        viewModel.initEngine(this)
        val reflex = L0ReflexSystem()

        // Voice result handler
        speechLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->

                if (result.resultCode == RESULT_OK) {

                    val text = result.data
                        ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                        ?.lowercase()
                        ?.trim()
                    viewModel.setApps(AppDiscovery.getInstalledApps(this))
                    viewModel.onVoiceInput(text)

                    // 🔥 NEW: dispatch action automatically
                    val result = reflex.execute(this, viewModel.state.value.lastAction)
                    viewModel.onActionExecuted(result)
                }
            }

        // Voice manager
        voiceManager = VoiceManager(this, speechLauncher)
        viewModelRef = viewModel
        val filter = IntentFilter().apply {
            addAction("ATMINI_WAKE_DETECTED")
            addAction("ATMINI_VOICE_HEARD")
        }
        ContextCompat.registerReceiver(this, wakeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            AtminiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    //ToDo : Remove later
                    //Column(
                    //    modifier =  Modifier.fillMaxSize()
                    //        .fillMaxSize()
                    //        .padding(24.dp),
                    //    verticalArrangement = Arrangement.spacedBy(16.dp)
                    //) {
                    //    Button(
                    //        onClick = {

                    //            Log.d("Atmini", "Clicked on temp button startWakeService")
                    //            startWakeService()
                    //        }
                    //    ) {
                    //        Text("Start Wake")
                    //    }
                    //    Button(onClick = @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
                    //        enrollmentController.start()
                    //    }) {
                    //       Text("Start Record")
                    //   }

                    //    Button(onClick = {
                    //        enrollmentController.save()
                    //    }) {
                    //        Text("Save Atmini")
                    //    }
                    //}
                    HomeScreen(
                        viewModel = viewModel,
                        onVoiceClick = {
                            voiceManager.startListening()
                        },
                        onMapsClick = {
                            val action = AtminiAction.OpenMaps
                            reflex.execute(this, action)
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
    override fun onDestroy() {
        unregisterReceiver(wakeReceiver)
        super.onDestroy()
    }
}
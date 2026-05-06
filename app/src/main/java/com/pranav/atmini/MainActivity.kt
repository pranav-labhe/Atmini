package com.pranav.atmini

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.pranav.atmini.feature.action.ActionDispatcher
import com.pranav.atmini.feature.app.AppDiscovery
import com.pranav.atmini.feature.home.HomeScreen
import com.pranav.atmini.feature.home.HomeViewModel
import com.pranav.atmini.feature.voice.VoiceManager
import com.pranav.atmini.ui.theme.AtminiTheme

class MainActivity : ComponentActivity() {

    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // ViewModel
        val viewModel = HomeViewModel()

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
                    ActionDispatcher.dispatch(this, viewModel.state.value.lastAction)
                }
            }

        // Voice manager
        voiceManager = VoiceManager(this, speechLauncher)
        voiceManager.startListening()

        setContent {
            AtminiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->

                    HomeScreen(
                        viewModel = viewModel,
                        onVoiceClick = {
                            voiceManager.startListening()
                        },
                        onMapsClick = {
                            openMaps()
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }

    private fun openMaps() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=Nagpur")
        )
        startActivity(intent)
    }
}
package com.example.naviglas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.naviglas.engine.ActionExecutor
import com.example.naviglas.engine.AssistantState
import com.example.naviglas.engine.ContactMatcher
import com.example.naviglas.engine.ParsedCommandResult
import com.example.naviglas.engine.VoiceEngineCallback
import com.example.naviglas.engine.VoiceEngineManager
import com.example.naviglas.service.VoiceAssistantService
import com.example.naviglas.ui.MainDrivingScreen
import com.example.naviglas.ui.PermissionScreen
import com.example.naviglas.ui.theme.NaviGlasTheme

class MainActivity : ComponentActivity(), VoiceEngineCallback {

    private lateinit var voiceEngineManager: VoiceEngineManager
    private var currentState by mutableStateOf(AssistantState.IDLE)
    private var recognizedText by mutableStateOf("")
    private var spokenFeedback by mutableStateOf("")
    private var hasAllPermissions by mutableStateOf(false)

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        hasAllPermissions = granted
        if (granted) {
            startAssistantService()
            loadContacts()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Podrška za rad preko zaključanog ekrana
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        voiceEngineManager = VoiceEngineManager(this, this)
        checkPermissions()

        setContent {
            NaviGlasTheme {
                if (!hasAllPermissions) {
                    PermissionScreen(onRequestPermissions = { requestAllPermissions() })
                } else {
                    MainDrivingScreen(
                        currentState = currentState,
                        recognizedText = recognizedText,
                        spokenFeedback = spokenFeedback,
                        isServiceRunning = true,
                        onMicClick = { voiceEngineManager.startListening() },
                        onQuickEtaClick = {
                            val contacts = ContactMatcher.loadContactsFromPhone(this)
                            val target = contacts.firstOrNull()?.name ?: "Kontakt"
                            val parsed = com.example.naviglas.engine.VoiceCommandParser.parseInput(
                                "Pošalji poruku $target da stižem za 10 minuta", contacts
                            )
                            onCommandParsed(parsed)
                        },
                        onSosClick = {
                            val contacts = ContactMatcher.loadContactsFromPhone(this)
                            val parsed = com.example.naviglas.engine.VoiceCommandParser.parseInput("Šalji SOS", contacts)
                            onCommandParsed(parsed)
                        }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        hasAllPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAllPermissions) {
            startAssistantService()
            loadContacts()
        }
    }

    private fun requestAllPermissions() {
        permissionLauncher.launch(requiredPermissions)

        // Dozvola za lebdeći mehur (Overlay Window)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun loadContacts() {
        val contacts = ContactMatcher.loadContactsFromPhone(this)
        voiceEngineManager.updateContacts(contacts)
    }

    private fun startAssistantService() {
        val intent = Intent(this, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_START_SERVICE
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onStateChanged(state: AssistantState) {
        currentState = state
    }

    override fun onSpeechRecognized(text: String) {
        recognizedText = text
    }

    override fun onCommandParsed(result: ParsedCommandResult) {
        spokenFeedback = result.spokenFeedback
        val contacts = ContactMatcher.loadContactsFromPhone(this)
        ActionExecutor.execute(this, result, contacts) { feedback ->
            spokenFeedback = feedback
            voiceEngineManager.speak(feedback)
        }
    }

    override fun onError(errorMessage: String) {
        spokenFeedback = errorMessage
    }

    override fun onDestroy() {
        voiceEngineManager.destroy()
        super.onDestroy()
    }
}

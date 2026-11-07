package com.example.naviglas.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WAITING_FOR_CONFIRMATION
}

interface VoiceEngineCallback {
    fun onStateChanged(state: AssistantState)
    fun onSpeechRecognized(text: String)
    fun onCommandParsed(result: ParsedCommandResult)
    fun onError(errorMessage: String)
}

class VoiceEngineManager(
    private val context: Context,
    private val callback: VoiceEngineCallback
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val audioRoutingManager = AudioRoutingManager(context)

    var currentState: AssistantState = AssistantState.IDLE
        private set(value) {
            field = value
            callback.onStateChanged(value)
        }

    private var pendingActionToConfirm: ParsedCommandResult? = null
    private var loadedContacts: List<Contact> = emptyList()

    init {
        initSpeechRecognizer()
        initTts()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        currentState = AssistantState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        currentState = AssistantState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        currentState = AssistantState.IDLE
                        audioRoutingManager.abandonAudioFocus()
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Nisam čuo komandu."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Vreme za govor je isteklo."
                            else -> "Greška pri slušanju."
                        }
                        callback.onError(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: ""
                        callback.onSpeechRecognized(spokenText)
                        processSpokenInput(spokenText)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("sr", "RS"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.setLanguage(Locale.ROOT)
            }
            isTtsReady = true

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    currentState = AssistantState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    if (pendingActionToConfirm != null) {
                        // Nakon što pročita pitanje za potvrdu, odmah počinje da sluša odgovor ("Da" / "Otkaži")
                        startListeningForConfirmation()
                    } else {
                        currentState = AssistantState.IDLE
                        audioRoutingManager.abandonAudioFocus()
                    }
                }

                override fun onError(utteranceId: String?) {
                    currentState = AssistantState.IDLE
                    audioRoutingManager.abandonAudioFocus()
                }
            })
        }
    }

    fun updateContacts(contacts: List<Contact>) {
        this.loadedContacts = contacts
    }

    /**
     * Pokreće slušanje glasovne komande.
     */
    fun startListening() {
        audioRoutingManager.requestAudioFocus()
        audioRoutingManager.enableCarAudioMode()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            callback.onError("Ne mogu da pokrenem mikrofon.")
        }
    }

    /**
     * Pokreće brzo slušanje za potvrdu ("Da" / "Otkaži").
     */
    private fun startListeningForConfirmation() {
        currentState = AssistantState.WAITING_FOR_CONFIRMATION
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            currentState = AssistantState.IDLE
        }
    }

    /**
     * Izgovara poruku naglas preko Text-To-Speech-a.
     */
    fun speak(text: String, utteranceId: String = "NaviGlasTTS") {
        if (!isTtsReady || textToSpeech == null) return
        audioRoutingManager.requestAudioFocus()
        audioRoutingManager.enableCarAudioMode()

        val normalized = SerbianTextNormalizer.prepareForTts(text)
        textToSpeech?.speak(normalized, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun processSpokenInput(input: String) {
        // Ako čekamo na potvrdu (npr. "Da" ili "Otkaži")
        if (pendingActionToConfirm != null) {
            val lower = SerbianTextNormalizer.stripDiacritics(input).lowercase(Locale.ROOT)
            val action = pendingActionToConfirm!!
            pendingActionToConfirm = null

            if (lower.contains("da") || lower.contains("potvrdi") || lower.contains("salji") || lower.contains("zovi")) {
                speak("Izvršavam.")
                callback.onCommandParsed(action)
            } else {
                speak("Otkazano.")
                callback.onCommandParsed(action.copy(action = CommandAction.CANCEL, spokenFeedback = "Otkazano."))
            }
            return
        }

        // Standardno parsiranje komande
        val parsed = VoiceCommandParser.parseInput(input, loadedContacts)

        if (parsed.action == CommandAction.SEND_SMS || parsed.action == CommandAction.CALL || parsed.action == CommandAction.SEND_SOS) {
            // Postavljamo potvrdni krug
            pendingActionToConfirm = parsed
            speak("${parsed.spokenFeedback} Da li želite da potvrdite?")
        } else {
            speak(parsed.spokenFeedback)
            callback.onCommandParsed(parsed)
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        audioRoutingManager.disableCarAudioMode()
    }
}

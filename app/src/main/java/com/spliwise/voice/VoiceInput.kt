package com.spliwise.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class VoiceState {
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
    internal var begin: () -> Unit = {}
    internal var end: () -> Unit = {}
    fun start() = begin()
    fun stop() = end()
}

/**
 * On-device speech recognition (Android 13+ uses the offline recogniser only, so audio never leaves the phone).
 * Older phones fall back to the system recogniser with "prefer offline".
 */
@Composable
fun rememberVoice(onText: (String) -> Unit, onError: (String) -> Unit): VoiceState {
    val ctx = LocalContext.current
    val state = remember { VoiceState() }
    val text by rememberUpdatedState(onText)
    val err by rememberUpdatedState(onError)
    var rec by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun listen() {
        rec?.destroy()
        val r = create(ctx) ?: run { err("Speech recognition isn't available. Install “Speech Services by Google” and download English (India) offline."); return }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { state.listening = true; state.partial = "" }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { state.partial = it }
            }
            override fun onResults(b: Bundle?) {
                state.listening = false
                val best = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (best.isNullOrBlank()) err("I didn't catch that — try again.") else text(best)
            }
            override fun onError(code: Int) {
                state.listening = false
                err(
                    when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that — try again."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "Download the English (India) offline speech pack in phone Settings → Speech."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                        else -> "Voice error ($code). Try again."
                    }
                )
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
        state.listening = true
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else err("Allow the microphone to add expenses by voice.")
    }
    state.begin = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }
    state.end = { rec?.stopListening(); state.listening = false }
    DisposableEffect(Unit) { onDispose { rec?.destroy() } }
    return state
}

private fun create(ctx: Context): SpeechRecognizer? = when {
    Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) ->
        SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
    SpeechRecognizer.isRecognitionAvailable(ctx) -> SpeechRecognizer.createSpeechRecognizer(ctx)
    else -> null
}

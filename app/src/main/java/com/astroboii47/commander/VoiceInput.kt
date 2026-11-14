package com.astroboii47.commander

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResultLauncher

/**
 * Optional push-to-talk voice input. RecognizerIntent keeps transcription in the
 * system UI (no audio handling in Houston); results flow through the same
 * assistant pipeline as typed text. No hotword, no background listening.
 */
object VoiceInput {
    fun isAvailable(activity: Activity): Boolean = runCatching {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(activity.packageManager) != null
    }.getOrDefault(false)

    fun startListening(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>,
    ): Boolean {
        if (!isAvailable(activity)) return false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your request")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        return runCatching {
            launcher.launch(intent)
            true
        }.getOrDefault(false)
    }

    fun parseResult(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
}

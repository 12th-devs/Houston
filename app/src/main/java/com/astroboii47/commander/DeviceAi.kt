package com.astroboii47.commander

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.collect

/** Google's supported AICore path to the model installed on an eligible device. */
object DeviceAi {
    private val model by lazy { Generation.getClient() }

    suspend fun status(): String = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> "Ready on this device"
            FeatureStatus.DOWNLOADABLE -> "Supported · model download needed"
            FeatureStatus.DOWNLOADING -> "Model downloading"
            else -> "Unavailable on this device"
        }
    }.getOrElse { error ->
        if (error is LinkageError) "Availability check failed. Try a prompt; on-device AI may still work."
        else "Unavailable: ${error.message ?: "AICore is not ready"}"
    }

    suspend fun download(): Result<Unit> = runCatching {
        check(model.checkStatus() == FeatureStatus.DOWNLOADABLE) { "Model is not downloadable right now" }
        model.download().collect()
    }

    suspend fun generate(history: List<AskMessage>, systemPrompt: String = ""): Result<String> = runCatching {
        val featureStatus = runCatching { model.checkStatus() }.getOrElse { error ->
            if (error is LinkageError) null else throw error
        }
        when (featureStatus) {
            FeatureStatus.DOWNLOADABLE -> error("Download the on-device model in AI settings first")
            FeatureStatus.DOWNLOADING -> error("The on-device model is still downloading")
            FeatureStatus.AVAILABLE -> Unit
            null -> Unit // Some AICore versions can generate despite a broken status check.
            else -> error("On-device AI is unavailable on this phone")
        }
        val prompt = systemPrompt + "\n\n" + history.takeLast(8).joinToString("\n\n") {
            "${if (it.role == "assistant") "Assistant" else "User"}: ${it.text}"
        } + "\n\nAssistant:"
        val image = history.lastOrNull { it.imagePath != null }?.imagePath?.let(BitmapFactory::decodeFile)
        val response = if (image != null) model.generateContent(
            GenerateContentRequest.Builder(ImagePart(image), TextPart(prompt)).build()
        ) else model.generateContent(prompt)
        response.candidates.firstOrNull()?.text?.trim()
            ?.takeIf(String::isNotBlank) ?: error("On-device AI returned no text")
    }
}

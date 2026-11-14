package com.astroboii47.commander

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object AiApi {
    data class Answer(val text: String, val attachments: List<AskMessage> = emptyList())

    suspend fun generate(context: Context, history: List<AskMessage>, onTool: suspend (String) -> Unit = {}): Result<Answer> = runCatching {
        val provider = AiSettings.provider(context)
        val systemPrompt = AiSettings.systemPrompt(context) + if (AiSettings.toolsEnabled(context) && provider != AiProvider.LiteRt) "\n\n" + AiSettings.toolPrompt(context) else ""
        if (provider == AiProvider.LiteRt) return@runCatching LiteRtAi.generate(context, history, systemPrompt, AiSettings.toolsEnabled(context), onTool).getOrThrow()
        if (!AiSettings.toolsEnabled(context)) return@runCatching Answer(generateOnce(context, history, systemPrompt).getOrThrow())
        val conversation = mutableListOf<AskMessage>()
        conversation.addAll(history)
        val attachments = mutableListOf<AskMessage>()
        repeat(5) {
            val answer = generateOnce(context, conversation, systemPrompt).getOrThrow()
            // Registry-aware parsing: legacy AiTools names plus every SystemAction id.
            val calls = AssistantTools.parseFallbackCalls(answer)
            if (calls.isEmpty()) return@runCatching Answer(answer, attachments)
            conversation.add(AskMessage("assistant", answer))
            calls.take(5).forEach { parsed ->
                onTool(prettyToolName(parsed.id))
                val result = ActionExecutor.runLegacy(
                    context,
                    parsed.id,
                    JSONObject(parsed.args),
                )
                val message = AskMessage("tool", "Tool result for ${parsed.id}: ${result.text}", result.imagePath)
                conversation.add(message)
                if (result.imagePath != null) attachments.add(message)
            }
        }
        Answer(generateOnce(context, conversation + AskMessage("user", "Answer now using the tool results above."), systemPrompt).getOrThrow(), attachments)
    }

    private fun prettyToolName(id: String): String =
        ActionRegistry.find(id)?.name ?: AiTools.label(JSONObject().put("tool", id))

    /** Single model call without the tool loop. Used by the assistant runner, which gates tools itself. */
    suspend fun generateText(
        context: Context,
        history: List<AskMessage>,
        systemPrompt: String = AiSettings.systemPrompt(context),
    ): Result<String> = generateOnce(context, history, systemPrompt)

    /** One native-tool step for OpenAI-compatible providers. Returns text or tool calls. */
    sealed interface NativeStep {
        data class Text(val text: String) : NativeStep
        data class ToolCall(val id: String, val name: String, val arguments: Map<String, String>)
        data class ToolCalls(val calls: List<ToolCall>) : NativeStep
    }

    suspend fun chatWithNativeToolsOnce(
        context: Context,
        history: List<AskMessage>,
        tools: JSONArray,
        provider: AiProvider = AiSettings.provider(context),
    ): Result<NativeStep> = runCatching {
        require(provider == AiProvider.OpenAI || provider == AiProvider.OpenRouter) {
            "${provider.label} does not use native tools here"
        }
        val key = AiSettings.apiKey(context, provider)
        require(key.isNotBlank()) { "Add a ${provider.label} API key in Houston settings" }
        val model = AiSettings.model(context, provider)
        val url = when (provider) {
            AiProvider.OpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", AiSettings.systemPrompt(context))).apply {
                val userMessages = messages(history)
                for (i in 0 until userMessages.length()) put(userMessages.get(i))
            })
            .put("tools", tools)
            .put("tool_choice", "auto")
            .toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            if (provider == AiProvider.OpenRouter) connection.setRequestProperty("X-Title", "Houston")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { error("${provider.label} returned an invalid response · $code") }
            if (code !in 200..299) {
                val detail = json.optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
                    ?: json.optString("message").takeIf(String::isNotBlank)
                error(detail ?: "${provider.label} request failed · $code")
            }
            val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: error("${provider.label} returned no message")
            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                val calls = (0 until toolCalls.length()).mapNotNull { index ->
                    val call = toolCalls.optJSONObject(index) ?: return@mapNotNull null
                    val function = call.optJSONObject("function") ?: return@mapNotNull null
                    val name = function.optString("name").trim()
                    if (name.isBlank()) return@mapNotNull null
                    val argsRaw = function.optString("arguments").trim()
                    val args = if (argsRaw.isBlank()) emptyMap() else runCatching {
                        val parsed = JSONObject(argsRaw)
                        buildMap {
                            val keys = parsed.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, parsed.opt(key)?.toString().orEmpty())
                            }
                        }
                    }.getOrDefault(emptyMap())
                    NativeStep.ToolCall(call.optString("id").ifBlank { name }, name, args)
                }
                if (calls.isEmpty()) error("${provider.label} returned no text")
                NativeStep.ToolCalls(calls)
            } else {
                val text = message.optString("content").trim()
                if (text.isBlank()) error("${provider.label} returned no text")
                NativeStep.Text(text)
            }
        } finally { connection.disconnect() }
    }

    private suspend fun generateOnce(context: Context, history: List<AskMessage>, systemPrompt: String): Result<String> = runCatching {
        val provider = AiSettings.provider(context)
        if (provider == AiProvider.Device) return@runCatching DeviceAi.generate(history, systemPrompt).getOrThrow()
        val key = AiSettings.apiKey(context, provider)
        require(key.isNotBlank()) { "Add a ${provider.label} API key in Houston settings" }
        val model = AiSettings.model(context, provider)
        if (provider == AiProvider.Gemini && model == "auto") return@runCatching GeminiApi.generate(context, history, systemPrompt).getOrThrow()
        val url = when (provider) {
            AiProvider.Gemini -> "https://generativelanguage.googleapis.com/v1beta/models/${model.removePrefix("models/")}:generateContent"
            AiProvider.OpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
            AiProvider.OpenAI -> "https://api.openai.com/v1/chat/completions"
            AiProvider.Anthropic -> "https://api.anthropic.com/v1/messages"
            AiProvider.Device -> error("On-device AI does not use a network endpoint")
            AiProvider.LiteRt -> error("LiteRT-LM does not use a network endpoint")
        }
        val body = when (provider) {
            AiProvider.Gemini -> JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", JSONArray().apply { history.forEach { message ->
                put(JSONObject().put("role", if (message.role == "assistant") "model" else "user")
                    .put("parts", geminiParts(message)))
            } })
            AiProvider.Anthropic -> JSONObject().put("model", model).put("max_tokens", 1024).put("system", systemPrompt)
                .put("messages", anthropicMessages(history))
            AiProvider.Device -> error("On-device AI does not use a network endpoint")
            AiProvider.LiteRt -> error("LiteRT-LM uses a local model")
            else -> JSONObject().put("model", model).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt)).apply {
                val userMessages = messages(history)
                for (i in 0 until userMessages.length()) put(userMessages.get(i))
            })
        }.toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            when (provider) {
                AiProvider.Gemini -> connection.setRequestProperty("x-goog-api-key", key)
                AiProvider.Anthropic -> {
                    connection.setRequestProperty("x-api-key", key)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                }
                AiProvider.Device -> Unit
                AiProvider.LiteRt -> Unit
                else -> connection.setRequestProperty("Authorization", "Bearer $key")
            }
            if (provider == AiProvider.OpenRouter) {
                connection.setRequestProperty("X-Title", "Houston")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { error("${provider.label} returned an invalid response · $code") }
            if (code !in 200..299) {
                val detail = json.optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
                    ?: json.optString("message").takeIf(String::isNotBlank)
                error(detail ?: "${provider.label} request failed · $code")
            }
            val answer = when (provider) {
                AiProvider.Gemini -> json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                AiProvider.Anthropic -> json.optJSONArray("content")?.optJSONObject(0)?.optString("text")
                AiProvider.Device -> null
                AiProvider.LiteRt -> null
                else -> json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            }.orEmpty().trim()
            answer.ifBlank { error("${provider.label} returned no text") }
        } finally { connection.disconnect() }
    }
    private fun messages(history: List<AskMessage>) = JSONArray().apply {
        history.forEach { message ->
            val image = imageData(message.imagePath)
            val content: Any = if (image == null) message.text else JSONArray()
                .put(JSONObject().put("type", "text").put("text", message.text))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")))
            put(JSONObject().put("role", if (message.role == "assistant") "assistant" else "user").put("content", content))
        }
    }
    private fun anthropicMessages(history: List<AskMessage>) = JSONArray().apply {
        history.forEach { message ->
            val image = imageData(message.imagePath)
            val content: Any = if (image == null) message.text else JSONArray()
                .put(JSONObject().put("type", "text").put("text", message.text))
                .put(JSONObject().put("type", "image").put("source", JSONObject()
                    .put("type", "base64").put("media_type", "image/jpeg").put("data", image)))
            put(JSONObject().put("role", if (message.role == "assistant") "assistant" else "user").put("content", content))
        }
    }

    fun geminiParts(message: AskMessage): JSONArray = JSONArray().put(JSONObject().put("text", message.text)).apply {
        imageData(message.imagePath)?.let { put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", it))) }
    }

    private fun imageData(path: String?): String? = path?.let { file -> runCatching {
        val source = BitmapFactory.decodeFile(file) ?: return@runCatching null
        val scale = minOf(1f, 1024f / maxOf(source.width, source.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 78, it) }.toByteArray()
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull() }
}

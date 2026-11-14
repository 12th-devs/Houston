package com.astroboii47.commander

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Optional native tool calling for imported LiteRT-LM Gemma 4 models. */
object LiteRtAi {
    /** LiteRT-LM model bundles start with this signature, before their metadata. */
    fun validateModel(file: File) {
        require(file.isFile && file.length() > 100_000_000L) {
            "Import a complete Gemma 4 .litertlm model in AI settings"
        }
        val header = ByteArray(8)
        file.inputStream().use { require(it.read(header) == header.size) { "Model file is incomplete" } }
        require(header.contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII))) {
            "This is not a LiteRT-LM model. Download the complete gemma-4-E2B-it.litertlm file, then import it again."
        }
    }

    private data class Spec(val name: String, val description: String, val properties: JSONObject = JSONObject(), val required: List<String> = emptyList())
    private fun field(type: String, description: String) = JSONObject().put("type", type).put("description", description)
    private val specs = listOf(
        Spec("date", "Get the current local date and time"),
        Spec("web_search", "Search the web for current information", JSONObject().put("query", field("string", "Search terms")), listOf("query")),
        Spec("weather", "Get current weather for a city", JSONObject().put("query", field("string", "City or place"))),
        Spec("search_files", "Find device files by name", JSONObject().put("query", field("string", "File name terms")), listOf("query")),
        Spec("search_screenshots", "Find screenshots by filename", JSONObject().put("query", field("string", "Optional filename terms"))),
        Spec("bluetooth_status", "Check whether Bluetooth is enabled"),
        Spec("bluetooth_devices", "List paired and connected Bluetooth devices"),
        Spec("bluetooth_set", "Turn Bluetooth on or off", JSONObject().put("enabled", field("boolean", "True to enable")), listOf("enabled")),
        Spec("volume_get", "Get media volume percentage"),
        Spec("volume_set", "Set media volume percentage", JSONObject().put("percent", field("integer", "0 to 100")), listOf("percent")),
        Spec("brightness_get", "Get screen brightness percentage"),
        Spec("brightness_set", "Set screen brightness percentage", JSONObject().put("percent", field("integer", "0 to 100")), listOf("percent")),
        Spec("music_status", "Get the current song and playback state"),
        Spec("music_control", "Control music playback", JSONObject().put("action", field("string", "play, pause, next, or previous")), listOf("action")),
        Spec("device.flashlight.toggle", "Toggle the camera flashlight"),
        Spec("device.volume.set", "Set media volume percentage", JSONObject().put("percent", field("integer", "0 to 100")), listOf("percent")),
        Spec("device.brightness.set", "Set screen brightness percentage", JSONObject().put("percent", field("integer", "0 to 100")), listOf("percent")),
        Spec("app.launch", "Launch an installed app by name", JSONObject().put("query", field("string", "App name")), listOf("query")),
        Spec("timer.create", "Start a timer", JSONObject().put("seconds", field("integer", "Duration in seconds")).put("label", field("string", "Optional label")), listOf("seconds")),
        Spec("calc.evaluate", "Evaluate arithmetic or convert units", JSONObject().put("expression", field("string", "Expression")), listOf("expression")),
        Spec("screenshot", "Capture the current screen and attach it to this conversation"),
    )

    private fun toolSpec(spec: Spec) = tool(object : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = JSONObject()
            .put("name", spec.name).put("description", spec.description)
            .put("parameters", JSONObject().put("type", "object").put("properties", spec.properties)
                .put("required", org.json.JSONArray(spec.required))).toString()
        override fun execute(paramsJsonString: String): String = "{}" // Manual tool calling executes through AiTools below.
    })

    suspend fun generate(
        context: Context, history: List<AskMessage>, systemPrompt: String, toolsEnabled: Boolean,
        onTool: suspend (String) -> Unit,
    ): Result<AiApi.Answer> = runCatching { withContext(Dispatchers.IO) {
        val modelPath = AiSettings.liteRtModelPath(context)
        require(modelPath.isNotBlank() && File(modelPath).isFile) { "Import a Gemma 4 .litertlm model in AI settings first" }
        validateModel(File(modelPath))
        val engine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath))
        try {
            engine.initialize()
            val prior = history.dropLast(1).map { message ->
                val content = contents(message)
                if (message.role == "assistant") Message.model(content) else Message.user(content)
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt), initialMessages = prior,
                tools = if (toolsEnabled) specs.map(::toolSpec) else emptyList(),
                automaticToolCalling = false,
            )
            engine.createConversation(config).use { conversation ->
                var response = conversation.sendMessage(Message.user(contents(history.last())))
                val attachments = mutableListOf<AskMessage>()
                repeat(6) {
                    if (response.toolCalls.isEmpty()) return@withContext AiApi.Answer(responseText(response), attachments)
                    val toolResponses = mutableListOf<Content>()
                    val newImages = mutableListOf<AskMessage>()
                    for (call in response.toolCalls.take(5)) {
                        val request = JSONObject().put("tool", call.name).put("args", JSONObject(call.arguments))
                        onTool(AiTools.label(request))
                        val result = ActionExecutor.runLegacy(context, call.name, JSONObject(call.arguments))
                        toolResponses.add(Content.ToolResponse(call.name, JSONObject().put("result", result.text).toString()))
                        if (result.imagePath != null) {
                            val attachment = AskMessage("tool", "Screenshot captured and attached", result.imagePath)
                            attachments.add(attachment)
                            newImages.add(attachment)
                        }
                    }
                    response = conversation.sendMessage(Message.tool(Contents.of(toolResponses)))
                    for (image in newImages) response = conversation.sendMessage(Message.user(contents(image)))
                }
                AiApi.Answer(responseText(response).ifBlank { "Tool call limit reached. Please try a narrower request." }, attachments)
            }
        } finally {
            // LiteRT-LM's close() throws when initialize() failed. Preserve the
            // original model-loading error so the user can diagnose it.
            if (engine.isInitialized()) runCatching { engine.close() }
        }
    } }

    private fun contents(message: AskMessage): Contents {
        val image = message.imagePath?.takeIf { File(it).isFile }
        return if (image == null) Contents.of(message.text)
            else Contents.of(Content.Text(message.text), Content.ImageFile(image))
    }

    private fun responseText(message: Message): String = message.contents.contents
        .filterIsInstance<Content.Text>().joinToString("\n") { it.text }.trim()
}

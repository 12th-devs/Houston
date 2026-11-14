package com.astroboii47.commander

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedAiChat(val id: String, val title: String, val updatedAt: Long, val messages: List<AskMessage>)

/** Chats stay in app-private storage and are never sent to a provider unless reopened. */
object AiChatHistory {
    private fun file(context: Context) = context.filesDir.resolve("ai_chats.json")

    @Synchronized fun all(context: Context): List<SavedAiChat> = runCatching {
        val array = JSONArray(file(context).takeIf { it.exists() }?.readText() ?: "[]")
        (0 until array.length()).map { index ->
            val chat = array.getJSONObject(index)
            val messages = chat.getJSONArray("messages")
            SavedAiChat(chat.getString("id"), chat.getString("title"), chat.getLong("updatedAt"),
                (0 until messages.length()).map { i ->
                    val message = messages.getJSONObject(i)
                    AskMessage(message.getString("role"), message.getString("text"), message.optString("imagePath").takeIf(String::isNotBlank))
                })
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    @Synchronized fun save(context: Context, id: String?, messages: List<AskMessage>): String {
        val chatId = id ?: UUID.randomUUID().toString()
        val title = messages.firstOrNull { it.role == "user" }?.text?.lineSequence()?.firstOrNull()?.take(60) ?: "New chat"
        val chats = (all(context).filterNot { it.id == chatId } + SavedAiChat(chatId, title, System.currentTimeMillis(), messages))
            .sortedByDescending { it.updatedAt }.take(100)
        val array = JSONArray().apply { chats.forEach { chat ->
            put(JSONObject().put("id", chat.id).put("title", chat.title).put("updatedAt", chat.updatedAt)
                .put("messages", JSONArray().apply { chat.messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("text", message.text).put("imagePath", message.imagePath.orEmpty()))
                } }))
        } }
        file(context).writeText(array.toString())
        return chatId
    }

    @Synchronized fun delete(context: Context, id: String) {
        val remaining = all(context).filterNot { it.id == id }
        file(context).writeText(JSONArray().apply { remaining.forEach { chat ->
            put(JSONObject().put("id", chat.id).put("title", chat.title).put("updatedAt", chat.updatedAt)
                .put("messages", JSONArray().apply { chat.messages.forEach { put(JSONObject().put("role", it.role).put("text", it.text).put("imagePath", it.imagePath.orEmpty())) } }))
        } }.toString())
    }
}

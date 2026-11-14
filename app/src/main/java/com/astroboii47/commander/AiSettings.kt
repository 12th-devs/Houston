package com.astroboii47.commander

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider(val label: String, val defaultModel: String) {
    Gemini("Gemini", "auto"),
    OpenRouter("OpenRouter", "openrouter/auto"),
    OpenAI("OpenAI", "gpt-4o-mini"),
    Anthropic("Anthropic", "claude-sonnet-4-5"),
    Device("On-device AI", "AICore model"),
    LiteRt("Gemma 4 (LiteRT-LM)", "Gemma 4 E2B/E4B");
}

/** Provider keys are encrypted with a device-bound Android Keystore key. */
object AiSettings {
    private const val PREFS = "commander_ai"
    private const val KEY_ALIAS = "commander_ai_keys"
    const val DEFAULT_SYSTEM_PROMPT = "You are Houston, a concise assistant inside an Android command bar. Answer clearly. Use the conversation and available tools when useful. Never claim a device action succeeded unless a tool confirms it."
    fun systemPrompt(context: Context): String = context.getSharedPreferences(PREFS, 0)
        .getString("system_prompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
    fun setSystemPrompt(context: Context, prompt: String) = context.getSharedPreferences(PREFS, 0)
        .edit().putString("system_prompt", prompt).apply()
    fun resetSystemPrompt(context: Context) = context.getSharedPreferences(PREFS, 0)
        .edit().remove("system_prompt").apply()
    fun toolPrompt(context: Context): String = context.getSharedPreferences(PREFS, 0)
        .getString("tool_prompt", AiTools.instructions) ?: AiTools.instructions
    fun setToolPrompt(context: Context, prompt: String) = context.getSharedPreferences(PREFS, 0)
        .edit().putString("tool_prompt", prompt).apply()
    fun resetToolPrompt(context: Context) = context.getSharedPreferences(PREFS, 0)
        .edit().remove("tool_prompt").apply()
    fun liteRtModelPath(context: Context): String = context.getSharedPreferences(PREFS, 0).getString("litert_model", "").orEmpty()
    fun setLiteRtModelPath(context: Context, path: String) = context.getSharedPreferences(PREFS, 0).edit().putString("litert_model", path).apply()
    fun toolsEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, 0).getBoolean("tools_enabled", false)
    fun setToolsEnabled(context: Context, enabled: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean("tools_enabled", enabled).apply()
    fun provider(context: Context): AiProvider = AiProvider.entries.getOrElse(
        context.getSharedPreferences(PREFS, 0).getInt("provider", 0)) { AiProvider.Gemini }
    fun setProvider(context: Context, provider: AiProvider) = context.getSharedPreferences(PREFS, 0).edit().putInt("provider", provider.ordinal).apply()
    fun model(context: Context, provider: AiProvider = provider(context)): String = context.getSharedPreferences(PREFS, 0)
        .getString("model_${provider.name}", null)?.trim().orEmpty().ifBlank { provider.defaultModel }
    fun setModel(context: Context, provider: AiProvider, value: String) = context.getSharedPreferences(PREFS, 0)
        .edit().putString("model_${provider.name}", value.trim()).apply()
    fun apiKey(context: Context, provider: AiProvider = provider(context)): String {
        if (provider == AiProvider.Device || provider == AiProvider.LiteRt) return ""
        if (provider == AiProvider.Gemini) return GeminiSettings.apiKey(context)
        return runCatching {
            val encoded = context.getSharedPreferences(PREFS, 0).getString("key_${provider.name}", null) ?: return ""
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = bytes.first().toInt()
            val iv = bytes.copyOfRange(1, 1 + ivLength)
            val encrypted = bytes.copyOfRange(1 + ivLength, bytes.size)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
                String(doFinal(encrypted), Charsets.UTF_8)
            }
        }.getOrDefault("")
    }
    fun setApiKey(context: Context, provider: AiProvider, value: String) {
        if (provider == AiProvider.Device || provider == AiProvider.LiteRt) return
        if (provider == AiProvider.Gemini) { GeminiSettings.setApiKey(context, value); return }
        val clean = value.trim()
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (clean.isEmpty()) { prefs.edit().remove("key_${provider.name}").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val packed = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("key_${provider.name}", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
}

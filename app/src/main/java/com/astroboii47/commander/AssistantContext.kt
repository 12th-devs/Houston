package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Optional, consent-gated context captured when Houston is invoked as assistant.
 *
 * Nothing here is monitored continuously: an instance is built only for the
 * lifetime of one assistant invocation, from data Android hands to the
 * foreground invocation (assist intent extras, shared text, or — when Houston
 * is the selected assistant — AssistStructure/AssistContent delivered to
 * [HoustonVoiceInteractionSession]).
 *
 * Screenshots and visible text are NEVER attached for cloud providers unless
 * [AssistantConsent.isScreenSharingAllowed] is true AND the user explicitly
 * asked about the current screen ("this screen", "summarize this", ...).
 */
data class AssistantContext(
    val sourcePackage: String? = null,
    val assistText: String? = null,
    val sharedText: String? = null,
    val link: String? = null,
    val screenshotPath: String? = null,
) {
    fun hasContent(): Boolean =
        !assistText.isNullOrBlank() || !sharedText.isNullOrBlank() || !link.isNullOrBlank()

    /** Short human-readable origin line for the assistant UI ("from Chrome"). */
    fun originLabel(): String? = sourcePackage
        ?.substringAfterLast('.')
        ?.replaceFirstChar { it.uppercase() }
        ?.takeIf { it.isNotBlank() }

    companion object {
        const val EXTRA_ASSIST_PACKAGE = "houston_assist_package"
        const val EXTRA_ASSIST_TEXT = "houston_assist_text"
        const val EXTRA_SHARED_TEXT = "houston_shared_text"
        const val EXTRA_ASSIST_LINK = "houston_assist_link"

        fun fromIntent(intent: Intent?): AssistantContext {
            if (intent == null) return AssistantContext()
            val shared = when (intent.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                else -> intent.getStringExtra(EXTRA_SHARED_TEXT)
            }
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val link = listOfNotNull(shared, subject)
                .flatMap { Regex("https?://[^\\s]+").findAll(it).map { m -> m.value } }
                .firstOrNull()
            return AssistantContext(
                sourcePackage = intent.getStringExtra(EXTRA_ASSIST_PACKAGE)
                    ?: intent.getStringExtra("com.samsung.android.intent.extra.ASSIST_PACKAGE"),
                assistText = intent.getStringExtra(EXTRA_ASSIST_TEXT),
                sharedText = shared,
                link = intent.getStringExtra(EXTRA_ASSIST_LINK) ?: link,
            )
        }

        fun toIntent(context: AssistantContext, intent: Intent): Intent {
            context.sourcePackage?.let { intent.putExtra(EXTRA_ASSIST_PACKAGE, it) }
            context.assistText?.let { intent.putExtra(EXTRA_ASSIST_TEXT, it) }
            context.sharedText?.let { intent.putExtra(EXTRA_SHARED_TEXT, it) }
            context.link?.let { intent.putExtra(EXTRA_ASSIST_LINK, it) }
            return intent
        }
    }
}

/** User consent for optional assistant context sharing. Defaults to OFF. */
object AssistantConsent {
    private const val PREFS = "houston_assistant"
    private const val KEY_SHARE_SCREEN = "share_screen_context"
    private const val KEY_VOICE = "voice_input_enabled"

    fun isScreenSharingAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHARE_SCREEN, false)

    fun setScreenSharingAllowed(context: Context, allowed: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHARE_SCREEN, allowed).apply()
    }

    fun isVoiceEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VOICE, true)

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VOICE, enabled).apply()
    }

    /** Screenshot/visible text may only accompany a request that is about the screen. */
    fun mayAttachScreen(request: String, context: Context): Boolean {
        if (!isScreenSharingAllowed(context)) return false
        return ScreenReferenceMentions.hasReference(request)
    }
}

/** Pure helper: does the request refer to on-screen content? */
object ScreenReferenceMentions {
    private val patterns = listOf(
        Regex("(?i)\\bthis\\s+(screen|page|app|error|product|article)\\b"),
        Regex("(?i)\\bsummarize\\s+this\\b"),
        Regex("(?i)\\bwhat\\s+(am\\s+i\\s+looking|is\\s+on\\s+(my\\s+)?screen|does\\s+this\\s+(error|message)\\s+mean)\\b"),
        Regex("(?i)\\bfrom\\s+this\\s+page\\b"),
        Regex("(?i)\\b(look|looks?)\\s+at\\s+this\\b"),
    )

    fun hasReference(request: String): Boolean {
        val text = request.trim()
        if (text.isBlank()) return false
        return patterns.any { it.containsMatchIn(text) }
    }
}

/** Whether this device can offer Houston as a selectable assistant. */
object AssistantEligibility {
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 29
}

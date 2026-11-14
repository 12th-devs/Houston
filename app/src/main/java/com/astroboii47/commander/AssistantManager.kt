package com.astroboii47.commander

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Official default-assistant integration (user-controlled).
 *
 * Houston never changes the default silently: [requestRole] launches Android's
 * system selection flow and the user decides. Status is read through RoleManager
 * on API 29+ with a Settings.Secure fallback for vendor ROMs that lag the API.
 */
object AssistantManager {
    const val ROLE_ASSISTANT = "android.app.role.ASSISTANT"

    fun isSupported(): Boolean = AssistantEligibility.isSupported()

    fun isDefault(context: Context): Boolean {
        if (!isSupported()) {
            AssistantLog.d("isDefault=false (unsupported API ${Build.VERSION.SDK_INT})")
            return false
        }
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val manager = context.getSystemService(RoleManager::class.java) ?: return false
                if (manager.isRoleAvailable(ROLE_ASSISTANT) && manager.isRoleHeld(ROLE_ASSISTANT)) return true
            }
            // Fallback for ROMs where the role API lags: compare the secure assistant setting.
            val configured = Settings.Secure.getString(context.contentResolver, "assistant")
            configured != null && configured.contains(context.packageName)
        }.getOrDefault(false)
        AssistantLog.d("isDefault=$result")
        return result
    }

    fun isRoleAvailable(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java)?.isRoleAvailable(ROLE_ASSISTANT) == true
        } else false
    }.getOrDefault(false)

    /** Official system flow. No-ops (returns false) when the role API is unavailable. */
    fun requestRole(activity: Activity, launcher: ActivityResultLauncher<Intent>? = null): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            AssistantLog.w("requestRole refused: API ${Build.VERSION.SDK_INT} < 29")
            return false
        }
        val manager = activity.getSystemService(RoleManager::class.java)
        if (manager == null) {
            AssistantLog.w("requestRole refused: no RoleManager")
            return false
        }
        val intent = runCatching { manager.createRequestRoleIntent(ROLE_ASSISTANT) }.getOrNull()
        if (intent == null) {
            AssistantLog.w("requestRole refused: role intent is null (role unavailable?)")
            return false
        }
        AssistantLog.i("Launching system assistant-role request")
        return runCatching {
            if (launcher != null) launcher.launch(intent)
            else activity.startActivity(intent)
            true
        }.onFailure { AssistantLog.e("Role request launch failed", it) }
            .getOrDefault(false)
    }

    /** Best-effort system screen for manual selection. Tries default-apps, then voice input. */
    fun openAssistantSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                AssistantLog.d("Assistant settings target missing: ${intent.action}")
                continue
            }
            AssistantLog.i("Opening assistant settings: ${intent.action}")
            return runCatching {
                context.startActivity(intent)
                true
            }.onFailure { AssistantLog.e("Opening ${intent.action} failed", it) }
                .getOrDefault(false)
        }
        AssistantLog.e("No assistant settings screen available")
        return false
    }

    fun statusLabel(context: Context): String = when {
        !isSupported() -> "Assistant role needs Android 10+"
        isDefault(context) -> "Houston is your default assistant"
        else -> "Houston is not your default assistant"
    }

    /** Full on-device diagnosis of why the system may never call Houston. */
    data class Diagnosis(
        val apiLevel: Int,
        val packageName: String,
        val roleAvailable: Boolean,
        val roleHeld: Boolean,
        val secureAssistantSetting: String?,
        val activeVoiceService: String?,
        val serviceEnabled: Boolean,
        val serviceDiscoverable: Boolean,
        val sessionServiceEnabled: Boolean,
    ) {
        /** The system only binds the service named here. Role alone never triggers a bind. */
        val namesHouston: Boolean = activeVoiceService?.contains(packageName) == true

        /** Role held but no voice service set: the classic "gesture does nothing" state. */
        val roleWithoutService: Boolean = roleHeld && activeVoiceService.isNullOrBlank()

        fun summary(): String = when {
            namesHouston -> "System routes assistant invocations to Houston"
            roleWithoutService ->
                "Role held BUT no active voice service — open Digital assistant app and select Houston"
            else -> "System does NOT route to Houston · active=${activeVoiceService ?: "<unset>"}"
        }

        fun formatted(): String = listOf(
            "api=$apiLevel",
            "roleAvailable=$roleAvailable",
            "roleHeld=$roleHeld",
            "secureAssistant=${secureAssistantSetting ?: "<unset>"}",
            "activeVoiceService=${activeVoiceService ?: "<unset>"}",
            "voiceServiceEnabled=$serviceEnabled",
            "voiceServiceDiscoverable=$serviceDiscoverable",
            "sessionServiceEnabled=$sessionServiceEnabled",
            "verdict=${summary()}",
        ).joinToString("\n")
    }

    fun diagnose(context: Context): Diagnosis {
        val roleAvailable = isRoleAvailable(context)
        val roleHeld = isDefault(context)
        // The authoritative answer: which service the system treats as active.
        // Read-only; no special permission needed for these keys.
        val secureAssistant = runCatching {
            Settings.Secure.getString(context.contentResolver, "assistant")
        }.getOrNull()
        val activeVoiceService = runCatching {
            Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
        }.getOrNull()
        val serviceEnabled = runCatching {
            context.packageManager.getServiceInfo(
                android.content.ComponentName(context, HoustonVoiceInteractionService::class.java),
                0,
            ).enabled
        }.getOrDefault(false)
        val sessionServiceEnabled = runCatching {
            context.packageManager.getServiceInfo(
                android.content.ComponentName(context, HoustonVoiceInteractionSessionService::class.java),
                0,
            ).enabled
        }.getOrDefault(false)
        val serviceDiscoverable = runCatching {
            context.packageManager.queryIntentServices(
                Intent("android.service.voice.VoiceInteractionService"),
                0,
            ).any { it.serviceInfo.packageName == context.packageName }
        }.getOrDefault(false)
        return Diagnosis(
            apiLevel = Build.VERSION.SDK_INT,
            packageName = context.packageName,
            roleAvailable = roleAvailable,
            roleHeld = roleHeld,
            secureAssistantSetting = secureAssistant,
            activeVoiceService = activeVoiceService,
            serviceEnabled = serviceEnabled,
            serviceDiscoverable = serviceDiscoverable,
            sessionServiceEnabled = sessionServiceEnabled,
        ).also { diagnosis ->
            AssistantLog.i("Assistant diagnostics:\n${diagnosis.formatted()}")
        }
    }

    // --- Pure invocation parsing (unit-testable without Android). ---

    enum class InvocationKind { AssistGesture, VoiceCommand, Share, ProcessText, Launcher, Unknown }

    data class Invocation(
        val kind: InvocationKind,
        val sharedText: String?,
        val prefillQuery: String,
        val fromAssistant: Boolean,
    )

    fun parseInvocation(action: String?, sharedText: String?, fromAssistantExtra: Boolean): Invocation {
        val kind = when (action) {
            Intent.ACTION_ASSIST -> InvocationKind.AssistGesture
            "android.intent.action.VOICE_COMMAND" -> InvocationKind.VoiceCommand
            Intent.ACTION_SEND -> InvocationKind.Share
            Intent.ACTION_PROCESS_TEXT -> InvocationKind.ProcessText
            OverlayActivity.ACTION_OPEN_OVERLAY -> InvocationKind.Launcher
            else -> InvocationKind.Unknown
        }
        val clean = sharedText?.trim().orEmpty()
        return Invocation(
            kind = kind,
            sharedText = clean.takeIf(String::isNotBlank),
            prefillQuery = clean,
            fromAssistant = fromAssistantExtra || kind == InvocationKind.AssistGesture || kind == InvocationKind.VoiceCommand,
        )
    }

    /** Dismissal contract: overlay finish() returns to the previous app; nothing to restore. */
    fun dismissalReturnsToPriorApp(): Boolean = true
}

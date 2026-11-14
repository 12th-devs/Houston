package com.astroboii47.commander

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import java.io.FileOutputStream

/**
 * Native assistant entry point. The service itself does no background work:
 * Android only binds it while Houston is the selected assistant and an
 * invocation is active. All UI reuses the existing command overlay.
 */
class HoustonVoiceInteractionService : VoiceInteractionService() {
    override fun onBind(intent: Intent): android.os.IBinder? {
        AssistantLog.i("VoiceInteractionService bound action=${intent.action}")
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        AssistantLog.i("VoiceInteractionService unbound action=${intent.action}")
        return super.onUnbind(intent)
    }

    override fun onReady() {
        super.onReady()
        AssistantLog.i("VoiceInteractionService ready; selected=${AssistantManager.isDefault(this)}")
    }

    override fun onShutdown() {
        AssistantLog.i("VoiceInteractionService shutdown")
        super.onShutdown()
    }
}

class HoustonVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onCreate() {
        super.onCreate()
        AssistantLog.d("SessionService created")
    }

    override fun onNewSession(state: Bundle): VoiceInteractionSession {
        AssistantLog.i("New assistant session; stateKeys=${state.keySet().joinToString()}")
        return HoustonVoiceInteractionSession(this)
    }
}

class HoustonVoiceInteractionSession(private val host: HoustonVoiceInteractionSessionService) :
    VoiceInteractionSession(host) {

    override fun onCreate() {
        super.onCreate()
        AssistantLog.d("Session created")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        AssistantLog.d("Session shown showFlags=$showFlags")
    }

    override fun onHide() {
        super.onHide()
        AssistantLog.d("Session hidden")
    }

    override fun onHandleAssist(state: Bundle?, structure: android.app.assist.AssistStructure?, content: android.app.assist.AssistContent?) {
        super.onHandleAssist(state, structure, content)
        AssistantLog.i(
            "onHandleAssist stateKeys=${state?.keySet()?.joinToString()} " +
                "structureNull=${structure == null} contentNull=${content == null}",
        )
        val invocation = AssistantInvocationCapture.fromAssist(host, state, structure, content)
        openOverlay(invocation)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        AssistantLog.d("onHandleScreenshot null=${screenshot == null} consent=${AssistantConsent.isScreenSharingAllowed(host)}")
        // Screenshots are session-scoped and consent-gated. Without consent the
        // bitmap is dropped immediately; nothing is monitored continuously.
        val bitmap = screenshot ?: return
        if (!AssistantConsent.isScreenSharingAllowed(host)) return
        runCatching {
            val file = host.filesDir.resolve("assist_screen_${System.currentTimeMillis()}.jpg")
            host.filesDir.listFiles { _, name -> name.startsWith("assist_screen_") }
                ?.forEach { runCatching { it.delete() } }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
            }
            AssistScreenshotStore.latestPath = file.absolutePath
            AssistantLog.d("Assist screenshot staged at ${file.absolutePath}")
        }.onFailure { AssistantLog.w("Staging assist screenshot failed", it) }
    }

    private fun openOverlay(invocation: AssistantInvocationCapture.Invocation) {
        val screenshotPath = AssistScreenshotStore.latestPath
        AssistScreenshotStore.latestPath = null
        val intent = Intent(host, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(OverlayActivity.EXTRA_FROM_ASSISTANT, true)
            invocation.sourcePackage?.let { putExtra(AssistantContext.EXTRA_ASSIST_PACKAGE, it) }
            invocation.assistText?.let { putExtra(AssistantContext.EXTRA_ASSIST_TEXT, it) }
            invocation.sharedText?.let { putExtra(AssistantContext.EXTRA_SHARED_TEXT, it) }
            invocation.link?.let { putExtra(AssistantContext.EXTRA_ASSIST_LINK, it) }
            screenshotPath?.let { putExtra(OverlayActivity.EXTRA_ASSIST_SCREENSHOT, it) }
        }
        AssistantLog.i(
            "Opening overlay pkg=${invocation.sourcePackage} " +
                AssistantLog.preview("assistText", invocation.assistText) + " " +
                AssistantLog.preview("sharedText", invocation.sharedText) + " " +
                "link=${invocation.link != null} screenshot=${screenshotPath != null}",
        )
        // startVoiceActivity carries the voice-interaction launch privilege. A
        // plain service-context startActivity is blocked by background-start
        // restrictions on modern Android, which surfaces as "nothing happens".
        val voiceStarted = runCatching {
            startVoiceActivity(intent)
            true
        }.onFailure { AssistantLog.w("startVoiceActivity failed, trying service start", it) }
            .getOrDefault(false)
        AssistantLog.i("startVoiceActivity ok=$voiceStarted")
        if (!voiceStarted) {
            val fallbackOk = runCatching {
                host.startActivity(intent)
                true
            }.onFailure { AssistantLog.e("Service-context startActivity failed", it) }
                .getOrDefault(false)
            AssistantLog.i("service startActivity ok=$fallbackOk")
            if (!fallbackOk) {
                AssistantLog.e("No overlay launch path worked; assistant invocation is dead")
                return
            }
        }
        // The overlay activity is the UI now; this session window stays hidden.
        runCatching { hide() }.onFailure { AssistantLog.w("Session hide failed", it) }
    }
}

/** Session-scoped screenshot path handoff (cleared after one use). */
object AssistScreenshotStore {
    @Volatile var latestPath: String? = null
}

/** Pure-ish capture of assist data with all platform access guarded. */
object AssistantInvocationCapture {
    data class Invocation(
        val sourcePackage: String?,
        val assistText: String?,
        val sharedText: String?,
        val link: String?,
    )

    fun fromAssist(
        host: HoustonVoiceInteractionSessionService,
        state: Bundle?,
        structure: android.app.assist.AssistStructure?,
        content: android.app.assist.AssistContent?,
    ): Invocation {
        val sourcePackage = runCatching { structure?.activityComponent?.packageName }.getOrNull()
            ?: state?.getString("android.intent.extra.ASSIST_PACKAGE")
        var assistText: String? = null
        var sharedText: String? = null
        var link: String? = null
        runCatching {
            val clip = content?.clipData
            AssistantLog.d("AssistContent clipItems=${clip?.itemCount ?: 0} hasIntent=${content?.intent != null}")
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                assistText = (item.text ?: item.htmlText)?.toString()?.takeIf(String::isNotBlank)
                link = item.intent?.dataString
                    ?: item.uri?.toString()?.takeIf { it.startsWith("http") }
            }
        }.onFailure { AssistantLog.w("Reading AssistContent clip failed", it) }
        runCatching {
            content?.intent?.let { intent ->
                if (sharedText == null) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                }
                if (link == null) link = intent.dataString?.takeIf { it.startsWith("http") }
            }
        }.onFailure { AssistantLog.w("Reading AssistContent intent failed", it) }
        // Never attribute Houston's own package as the assisted app.
        val selfPackage = runCatching { host.packageName }.getOrNull()
        return Invocation(
            sourcePackage = sourcePackage?.takeIf { it != selfPackage },
            assistText = assistText?.take(2000),
            sharedText = sharedText?.take(4000),
            link = link?.take(2000),
        ).also {
            AssistantLog.d(
                "Captured pkg=${it.sourcePackage} " +
                    AssistantLog.preview("assistText", it.assistText) + " " +
                    AssistantLog.preview("sharedText", it.sharedText) + " " +
                    "link=${it.link != null}",
            )
        }
    }

    @Suppress("UNUSED")
    fun sourceComponentName(host: HoustonVoiceInteractionSessionService): ComponentName =
        ComponentName(host, HoustonVoiceInteractionSessionService::class.java)
}

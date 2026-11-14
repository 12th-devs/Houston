package com.astroboii47.commander

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.KeyEvent
import android.content.res.Configuration

class OverlayActivity : ComponentActivity() {
    private val askPreviewState = mutableStateOf<String?>(null)
    private val askPromptState = mutableStateOf<String?>(null)
    private val askWaitingState = mutableStateOf(false)
    private val switcherRequestState = mutableIntStateOf(0)
    private var currentQuery = ""
    private val assistContextState = mutableStateOf<AssistantContext?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.attributes = window.attributes.apply {
            dimAmount = if (AppearanceSettings.styleFor(darkMode) == BlurStyle.Inverted) 0.72f else 0.34f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 42 }
        }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
        )

        AssistantLog.i(
            "Overlay onCreate action=${intent.action} fromAssistant=${intent.getBooleanExtra(EXTRA_FROM_ASSISTANT, false)} " +
                "hasInitialQuery=${!intent.getStringExtra(EXTRA_INITIAL_QUERY).isNullOrBlank()}",
        )
        val engine = CommandEngine(this)
        val assistContext = AssistantContext.fromIntent(intent).let { parsed ->
            val screenshot = intent.getStringExtra(EXTRA_ASSIST_SCREENSHOT)
                ?.takeIf { it.isNotBlank() && java.io.File(it).isFile }
            if (screenshot != null) parsed.copy(screenshotPath = screenshot) else parsed
        }
        val assistPrefill = assistContext.sharedText?.takeIf { it.isNotBlank() }
            ?: assistContext.assistText?.takeIf { it.isNotBlank() && it.length < 200 }
        currentQuery = savedInstanceState?.getString(STATE_QUERY)
            ?: intent.getStringExtra(EXTRA_INITIAL_QUERY)?.takeIf(String::isNotBlank)
            ?: assistPrefill.orEmpty()
        AssistantLog.i(
            "Overlay query prefilled=${currentQuery.isNotBlank()} pkg=${assistContext.sourcePackage} " +
                AssistantLog.preview("sharedText", assistContext.sharedText),
        )
        assistContextState.value = assistContext.takeIf { it.hasContent() || it.sourcePackage != null }
        askPreviewState.value = intent.getStringExtra(EXTRA_ASK_PREVIEW)
            ?: ChatGptNotificationBridge.consumeResponse(this)
        askPromptState.value = intent.getStringExtra(EXTRA_ASK_PROMPT)
            ?: if (askPreviewState.value != null) ChatGptNotificationBridge.lastPrompt(this) else null
        askWaitingState.value = intent.getBooleanExtra(EXTRA_ASK_WAITING, false) && askPreviewState.value == null
        if (intent.getBooleanExtra(EXTRA_START_SWITCHER, false)) switcherRequestState.intValue++
        lifecycleScope.launch(Dispatchers.IO) { engine.warmAppIndex() }
        setContent {
            AccentSelectionProvider {
            CommandOverlayApp(
                engine = engine,
                onDismiss = ::finish,
                initialQuery = currentQuery,
                initialAssistContext = assistContextState.value,
                initialAskPreview = askPreviewState.value,
                initialAskPrompt = askPromptState.value,
                initialAskWaiting = askWaitingState.value,
                initialSwitcherMode = intent.getBooleanExtra(EXTRA_START_SWITCHER, false),
                switcherRequestToken = switcherRequestState.intValue,
            )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_QUERY, currentQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        inputActive = true
    }

    fun preserveQueryForMiniMode(value: String) {
        currentQuery = value
        intent.putExtra(EXTRA_INITIAL_QUERY, value)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AssistantLog.i("Overlay onNewIntent action=${intent.action}")
        val assist = AssistantContext.fromIntent(intent)
        if (assist.hasContent() || assist.sourcePackage != null) {
            assistContextState.value = assist
            if (currentQuery.isBlank()) {
                val prefill = assist.sharedText?.takeIf { it.isNotBlank() }
                if (!prefill.isNullOrBlank()) {
                    currentQuery = prefill
                    intent.putExtra(EXTRA_INITIAL_QUERY, prefill)
                }
            }
        }
        intent.getStringExtra(EXTRA_ASK_PREVIEW)?.let {
            askPreviewState.value = it
            askWaitingState.value = false
            askPromptState.value = intent.getStringExtra(EXTRA_ASK_PROMPT)
                ?: ChatGptNotificationBridge.lastPrompt(this)
        }
        if (intent.getBooleanExtra(EXTRA_ASK_WAITING, false)) {
            askPromptState.value = intent.getStringExtra(EXTRA_ASK_PROMPT)
            askWaitingState.value = askPreviewState.value == null
        }
        if (intent.getBooleanExtra(EXTRA_START_SWITCHER, false)) switcherRequestState.intValue++
    }

    override fun onStop() {
        // A startup handoff must never outlive its command window. Otherwise
        // the accessibility service would keep routing home keys to an editor
        // that is no longer visible instead of opening a fresh overlay.
        HomeTypingHandoff.finish()
        inputActive = false
        super.onStop()
    }

    override fun onDestroy() {
        inputActive = false
        super.onDestroy()
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (SwitcherKeyBridge.dispatch(event)) return true
        if (HomeTypingHandoff.isCollecting() && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_DEL && HomeTypingHandoff.backspace()) return true
            if (event.isCtrlPressed || event.isMetaPressed) return super.dispatchKeyEvent(event)
            val unicode = event.getUnicodeChar(event.metaState)
            if (unicode != 0 && !Character.isISOControl(unicode)) {
                HomeTypingHandoff.append(String(Character.toChars(unicode)))
                return true
            }
        }
        if (HomeTypingHandoff.isCollecting() && event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 &&
            HomeTypingSettings.holdFirstForAlt.value
        ) {
            // Home launches intentionally delay editor focus until the initial
            // typing burst is complete. Preserve the first-key hold shortcut
            // in that buffer so alternate command triggers still work.
            val alternateCodePoint = event.getUnicodeChar(
                event.metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
            )
            val alternate = if (alternateCodePoint != 0 && !Character.isISOControl(alternateCodePoint)) {
                String(Character.toChars(alternateCodePoint))
            } else ""
            val symbol = alternate.singleOrNull()
            val isTrigger = symbol != null && (
                symbol == TriggerSettings.noteSymbol() ||
                    symbol == FileSearchSettings.trigger.value ||
                    CommandKind.entries.any { it != CommandKind.Note && it != CommandKind.Files && it.symbol == symbol } ||
                    AliasSettings.usesPrefix(this, symbol)
                )
            if (isTrigger && HomeTypingHandoff.convertHeldFirstKey(event.keyCode, alternate)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        @Volatile private var inputActive = false
        fun isInputActive(): Boolean = inputActive
        const val ACTION_OPEN_OVERLAY = "com.astroboii47.commander.OPEN_OVERLAY"
        const val EXTRA_FROM_ASSISTANT = "houston_from_assistant"
        const val EXTRA_ASSIST_SCREENSHOT = "houston_assist_screenshot"
        const val EXTRA_INITIAL_QUERY = "initial_query"
        const val EXTRA_ASK_PREVIEW = "ask_preview"
        const val EXTRA_ASK_WAITING = "ask_waiting"
        const val EXTRA_ASK_PROMPT = "ask_prompt"
        const val EXTRA_START_SWITCHER = "commander_start_switcher"
        private const val STATE_QUERY = "commander_query"
    }
}

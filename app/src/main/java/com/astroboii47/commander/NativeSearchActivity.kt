package com.astroboii47.commander

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Touch-first search surface opened by the floating edge gesture. */
class NativeSearchActivity : ComponentActivity() {
    internal lateinit var engine: CommandEngine
    private var keys: NativeKeyBindings? = null
    internal var screenshotPending = false
    internal var edgeRestoreSent = false
    internal var mediaRevision by mutableIntStateOf(0)
    private var clipboardRevision by mutableIntStateOf(0)
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    internal val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { mediaRevision++ }
    internal val imagePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mediaRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (EdgeBarSettings.enabled(this)) hideEdgeHandle()
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0f }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !CommandBarPreferences.solid(this)) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) window.setBackgroundBlurRadius(80)
            }
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        engine = CommandEngine(this)
        lifecycleScope.launch(Dispatchers.IO) { engine.warmAppIndex() }
        CommandBarHistory.recordClipboard(this)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { CommandBarHistory.recordClipboard(this); clipboardRevision++ }.also { clipboard.addPrimaryClipChangedListener(it) }
        setContent {
            val colors = if (Build.VERSION.SDK_INT >= 31 && CommandBarPreferences.materialYouBar(this))
                androidx.compose.material3.dynamicDarkColorScheme(this)
            else androidx.compose.material3.darkColorScheme(primary = Color(0xFFA892F6))
            androidx.compose.material3.MaterialTheme(colorScheme = colors) {
                NativeSearchPanel(engine, mediaRevision, clipboardRevision, ::finish, ::openResult) { keys = it }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.postDelayed({
                if (hasWindowFocus()) { CommandBarHistory.recordClipboard(this); clipboardRevision++ }
            }, 160)
        }
    }

    override fun onStop() {
        // onDestroy is not guaranteed (process death, task removal), so restore
        // the handle here too. Guarded to run exactly once.
        restoreEdgeHandle()
        super.onStop()
    }

    override fun onDestroy() {
        clipboardListener?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(it) }
        restoreEdgeHandle()
        super.onDestroy()
    }

    internal fun hideEdgeHandle() {
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this, Intent(this, EdgeBarService::class.java).setAction(EdgeBarService.ACTION_HIDE))
        }
    }

    private fun restoreEdgeHandle() {
        if (edgeRestoreSent || screenshotPending) return
        if (!EdgeBarSettings.enabled(this)) return
        edgeRestoreSent = true
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this, Intent(this, EdgeBarService::class.java).setAction(EdgeBarService.ACTION_SHOW))
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed) {
            val binding = keys
            if (binding != null && !(binding.allowDigits && (event.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 || event.keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9))) when (event.keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    binding.activate(if (event.keyCode == KeyEvent.KEYCODE_0) 9 else event.keyCode - KeyEvent.KEYCODE_1)
                    return true
                }
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                    binding.activate(if (event.keyCode == KeyEvent.KEYCODE_NUMPAD_0) 9 else event.keyCode - KeyEvent.KEYCODE_NUMPAD_1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> { binding.move(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { binding.move(1); return true }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { binding.submit(); return true }
                KeyEvent.KEYCODE_DEL -> if (binding.backspace()) return true
                KeyEvent.KEYCODE_ESCAPE -> if (binding.backspace()) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Thin delegate; dispatch body lives in NativeResultDispatcher.kt. */
    private fun openResult(result: NativeResult, query: String) = performOpenResult(result, query)
}

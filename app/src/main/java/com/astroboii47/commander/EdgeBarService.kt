package com.astroboii47.commander

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs

/** Edge target with a narrow visible pill, swipe-to-open, and long-press repositioning. */
class EdgeBarService : Service() {
    private lateinit var windows: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var handle: FrameLayout? = null
    private var startX = 0f
    private var startY = 0f
    private var startWindowY = 0
    private var dragMode = false
    private var armed = false
    private var triggered = false
    private var lastX = 0f
    private var lastY = 0f
    private var pill: View? = null
    private var manuallyHidden = false
    private var pollTick = 0
    private val keyboardCheck = object : Runnable {
        override fun run() {
            // Self-heal: if the window manager dropped our view without killing
            // the service, re-attach. Throttled so a revoked overlay permission
            // can't turn this into a hot addView-failure loop.
            if (pollTick++ % 10 == 0 && EdgeBarSettings.enabled(this@EdgeBarService)) {
                runCatching { ensureHandle() }
            }
            handle?.let { view ->
                val insets = view.rootWindowInsets
                val imeVisible = if (android.os.Build.VERSION.SDK_INT >= 30)
                    insets?.isVisible(android.view.WindowInsets.Type.ime()) == true ||
                        (insets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0) > dp(110)
                    else false
                val frame = Rect()
                view.getWindowVisibleDisplayFrame(frame)
                val obscured = frame.height() > resources.displayMetrics.heightPixels * .5f &&
                    resources.displayMetrics.heightPixels - frame.bottom > dp(180)
                view.visibility = if (manuallyHidden || accessibilityKeyboardVisible || imeVisible || obscured) View.INVISIBLE else View.VISIBLE
            }
            handler.postDelayed(this, 150)
        }
    }
    private var longPressAction: Runnable? = null
    private var heldSwipeAction: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> { manuallyHidden = true; handle?.visibility = View.INVISIBLE }
            ACTION_SHOW -> {
                manuallyHidden = false
                // Restore immediately instead of waiting up to 150 ms for the
                // next keyboard poll; the poll still re-hides for real IME use.
                handle?.visibility = View.VISIBLE
                handler.removeCallbacks(keyboardCheck)
                handler.post(keyboardCheck)
            }
        }
        // System restart delivers a null intent: make sure the pill is back.
        if (!Settings.canDrawOverlays(this)) {
            if (intent == null || intent.action == null) stopSelf()
            return START_NOT_STICKY
        }
        if (EdgeBarSettings.enabled(this)) ensureHandle()
        else if (intent == null) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Houston away must not kill the handle. Re-launch it; the
        // null-intent path above re-attaches the overlay window.
        if (EdgeBarSettings.enabled(this)) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    this, Intent(this, EdgeBarService::class.java))
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / density changes can leave the pill off-screen or detached.
        handle?.let { target ->
            val params = target.layoutParams as? WindowManager.LayoutParams ?: return@let
            val sideRight = right()
            params.gravity = (if (sideRight) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
            params.y = (resources.displayMetrics.heightPixels *
                EdgeBarSettings.verticalPosition(this)).toInt()
            runCatching { windows.updateViewLayout(target, params) }
            target.post { target.systemGestureExclusionRects = listOf(Rect(0, 0, target.width, target.height)) }
        } ?: ensureHandle()
    }
    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this) || !EdgeBarSettings.enabled(this)) { stopSelf(); return }
        running = true
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Houston edge bar", NotificationManager.IMPORTANCE_LOW))
        val settingsIntent = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(42, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_commander_bar)
            .setContentTitle("Houston edge bar")
            .setContentText("Swipe inward to open, hold to move")
            .setContentIntent(settingsIntent)
            .setOngoing(true).build())
        showHandle()
        handler.post(keyboardCheck)
    }
    override fun onDestroy() {
        running = false
        clearTimers()
        handler.removeCallbacks(keyboardCheck)
        handle?.let { runCatching { windows.removeView(it) } }
        handle = null
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun right() = EdgeBarSettings.rightSide(this)
    private fun clearTimers() {
        longPressAction?.let(handler::removeCallbacks); longPressAction = null
        heldSwipeAction?.let(handler::removeCallbacks); heldSwipeAction = null
    }
    private fun showHandle() {
        if (handle != null) { ensureHandle(); return }
        createHandle()
    }

    /** Re-attaches the pill if the window manager dropped it without killing us. */
    private fun ensureHandle() {
        val current = handle
        if (current == null) { createHandle(); return }
        if (current.parent == null) {
            handle = null
            createHandle()
            return
        }
        if (!manuallyHidden) current.visibility = View.VISIBLE
    }

    private fun createHandle() {
        if (handle != null) return
        val size = EdgeBarSettings.handleSize(this)
        val hitWidth = intArrayOf(32, 38, 44)[size]
        val pillWidth = intArrayOf(5, 6, 8)[size]
        val height = intArrayOf(42, 56, 72)[size]
        val sideRight = right()
        val params = WindowManager.LayoutParams(dp(hitWidth), dp(height),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            gravity = (if (sideRight) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
            y = (resources.displayMetrics.heightPixels * EdgeBarSettings.verticalPosition(this@EdgeBarService)).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val target = FrameLayout(this).apply {
            clipChildren = false
            contentDescription = "Houston edge handle. Swipe inward to open. Hold to reposition."
            isHapticFeedbackEnabled = true
        }
        val shape = GradientDrawable().apply {
            setColor(if (android.os.Build.VERSION.SDK_INT >= 31) {
                resources.getColor(android.R.color.system_accent1_300, theme)
            } else Color.rgb(117, 109, 137))
            cornerRadius = dp(pillWidth).toFloat()
            setStroke(dp(1).coerceAtLeast(1), Color.argb(110, 255, 255, 255))
        }
        val visible = View(this).apply { background = shape; elevation = dp(3).toFloat() }
        val visibleParams = FrameLayout.LayoutParams(dp(pillWidth), FrameLayout.LayoutParams.MATCH_PARENT,
            if (sideRight) Gravity.RIGHT else Gravity.LEFT)
        target.addView(visible, visibleParams)
        pill = visible
        target.setOnTouchListener { _, event -> onHandleTouch(target, visible, params, event, sideRight, height) }
        handle = target
        // Can throw if the overlay permission was revoked mid-run; the
        // periodic self-heal retries instead of crashing the process.
        runCatching { windows.addView(target, params) }
        target.post { target.systemGestureExclusionRects = listOf(Rect(0, 0, target.width, target.height)) }
    }

    private fun onHandleTouch(target: FrameLayout, visible: View, params: WindowManager.LayoutParams,
                              event: MotionEvent, sideRight: Boolean, heightDp: Int): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearTimers()
                startX = event.rawX; startY = event.rawY
                lastX = startX; lastY = startY
                startWindowY = params.y
                dragMode = false; armed = false; triggered = false
                visible.animate().cancel()
                visible.animate().scaleX(.82f).scaleY(.94f).alpha(.85f).setDuration(100).start()
                longPressAction = Runnable {
                    if (!armed && !triggered) {
                        dragMode = true
                        startWindowY = params.y
                        startY = lastY
                        target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        visible.animate().scaleX(1.45f).scaleY(1.08f).alpha(1f).setDuration(150).start()
                    }
                }.also { handler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = event.rawX; lastY = event.rawY
                if (dragMode) {
                    params.y = (startWindowY + event.rawY - startY).toInt()
                        .coerceIn(dp(24), resources.displayMetrics.heightPixels - dp(heightDp + 72))
                    runCatching { windows.updateViewLayout(target, params) }
                    return true
                }
                if (triggered) return true
                val inward = if (sideRight) startX - event.rawX else event.rawX - startX
                val vertical = abs(event.rawY - startY)
                val threshold = dp(EdgeBarSettings.swipeDistance(this))
                if (!armed && inward >= threshold && inward > vertical * .75f) {
                    armed = true
                    longPressAction?.let(handler::removeCallbacks)
                    target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    visible.animate().scaleX(.68f).scaleY(.88f).alpha(1f).setDuration(140).start()
                    heldSwipeAction = Runnable { if (armed && !triggered) trigger(target) }
                        .also { handler.postDelayed(it, 170) }
                } else if (armed && inward < dp(5)) {
                    armed = false
                    heldSwipeAction?.let(handler::removeCallbacks)
                    visible.animate().scaleX(.82f).scaleY(.94f).alpha(.85f).setDuration(100).start()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                clearTimers()
                if (dragMode) {
                    EdgeBarSettings.setVerticalPosition(this, params.y.toFloat() / resources.displayMetrics.heightPixels)
                    val otherSide = if (sideRight) event.rawX < resources.displayMetrics.widthPixels * .35f
                        else event.rawX > resources.displayMetrics.widthPixels * .65f
                    if (otherSide) target.post { EdgeBarSettings.setRightSide(this, !sideRight) }
                } else if (!triggered && event.actionMasked == MotionEvent.ACTION_UP) {
                    if (armed || (abs(event.rawX - startX) < dp(10) && abs(event.rawY - startY) < dp(10))) trigger(target)
                }
                visible.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
                dragMode = false; armed = false
                return true
            }
        }
        return true
    }
    private fun trigger(view: View) {
        if (triggered) return
        triggered = true
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        startActivity(Intent(this, NativeSearchActivity::class.java)
            .putExtra("edge_side", if (right()) "right" else "left")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    companion object {
        @Volatile var accessibilityKeyboardVisible: Boolean = false
        /** True while the service instance is alive; surfaced in Settings status. */
        @Volatile var running: Boolean = false
            private set
        const val ACTION_HIDE = "com.astroboii47.commander.EDGE_HIDE"
        const val ACTION_SHOW = "com.astroboii47.commander.EDGE_SHOW"
        private const val CHANNEL = "commander_edge_bar"
    }
}

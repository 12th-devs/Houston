package com.astroboii47.commander

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Makes Houston grantable under system notification access and keeps a small
 * on-device snapshot of active notifications for player controls and live
 * search. Everything stays in private internal storage; nothing leaves the
 * phone. Best-effort and heavily guarded — a listener must never crash.
 */
class HoustonNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        refreshSnapshot()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        try {
            val all = try {
                activeNotifications?.toList()
            } catch (_: Exception) {
                return
            } ?: return
            val items = all
                .filter { it.packageName != packageName && !it.isOngoing }
                .sortedByDescending { it.postTime }
                .take(25)
                .mapNotNull { sbn ->
                    try {
                        val extras = sbn.notification.extras ?: return@mapNotNull null
                        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(160)
                        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())?.take(240)
                        if (title.isNullOrBlank() && text.isNullOrBlank()) return@mapNotNull null
                        LiveNotification(
                            pkg = sbn.packageName,
                            title = title ?: "",
                            text = text ?: "",
                            postedAt = sbn.postTime,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            NotificationSearchSettings.saveSnapshot(this, items)
        } catch (_: Exception) {
        }
    }
}

/** Minimal on-device notification snapshot entry. */
data class LiveNotification(
    val pkg: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)

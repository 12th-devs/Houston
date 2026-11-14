package com.astroboii47.commander

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * SMS/contact search and messaging. Holds only applicationContext.
 * Activity is passed per call for UI launches / permission requests.
 */
class ContactEngine(private val appContext: Context) {

    fun directSmsEnabled(): Boolean = SmsSettings.directEnabled(appContext)

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun requestContactsPermission(activity: Activity) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_CONTACTS), 4302)
    }

    fun searchContacts(
        query: String,
        limit: Int = 5,
        includeMessenger: Boolean = false,
        platform: MessagingPlatformOption? = null,
    ): List<ContactResult> {
        if (query.isBlank() && platform == null) return emptyList()
        val messenger = if (includeMessenger) CommanderMessagingContacts.search(appContext, query, limit, platform) else emptyList()
        if (platform != null && platform.key != "sms") return messenger
        val directNumber = if (!includeMessenger && query.trim().matches(Regex("^\\+?[0-9() .-]{3,}$")) &&
            query.count(Char::isDigit) >= 3
        ) {
            PhoneNumberUtils.normalizeNumber(query).takeIf(String::isNotBlank)?.let { number ->
                ContactResult(name = number, number = number, label = "call typed number")
            }
        } else null
        if (directNumber != null) return listOf(directNumber)
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return listOfNotNull(directNumber) + messenger
        }
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
        )
        val commanderContactsByName = if (includeMessenger) {
            CommanderMessagingContacts.contacts(appContext).associateBy { canonicalPersonName(it.name) }
        } else emptyMap()
        val messagingDiscoveries = if (includeMessenger) {
            CommanderMessagingContacts.discoveries(appContext).associateBy { it.identity }
        } else emptyMap()
        val phoneContacts = appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            buildList {
                while (cursor.moveToNext() && size < limit) {
                    val type = cursor.getInt(typeColumn)
                    val phoneBookName = cursor.getString(nameColumn)
                    val commanderContact = if (includeMessenger) {
                        commanderContactsByName[canonicalPersonName(phoneBookName)]
                    } else null
                    add(
                        ContactResult(
                            name = commanderContact?.name ?: phoneBookName,
                            number = cursor.getString(numberColumn),
                            label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(appContext.resources, type, "phone").toString()
                                .lowercase().let { typeLabel ->
                                    if (includeMessenger) listOf(
                                        "SMS",
                                        phoneBookName,
                                        typeLabel,
                                        if (directSmsEnabled()) "direct send" else "open composer",
                                    ).joinToString(" · ")
                                    else typeLabel
                                },
                            messagingPlatform = if (includeMessenger) "SMS" else null,
                            messagingPlatformIcon = null,
                            commanderMessagingContactId = commanderContact?.id,
                            messagingIsDefault = commanderContact?.defaultIdentity?.let { defaultIdentity ->
                                val route = messagingDiscoveries[defaultIdentity]
                                route?.packageName == "sms" || route?.platform.equals("SMS", true) ||
                                    route?.packageName in buildSet {
                                        android.provider.Telephony.Sms.getDefaultSmsPackage(appContext)?.let(::add)
                                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                                            .resolveActivity(appContext.packageManager)?.packageName?.let(::add)
                                    }
                            } == true,
                        ),
                    )
                }
            }.distinctBy { contact ->
                contact.name.trim().lowercase() to PhoneNumberUtils.normalizeNumber(contact.number)
            }
        } ?: emptyList()
        val directSmsNames = phoneContacts.mapTo(hashSetOf()) { canonicalPersonName(it.name) }
        val smsPackages = buildSet {
            android.provider.Telephony.Sms.getDefaultSmsPackage(appContext)?.let(::add)
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).resolveActivity(appContext.packageManager)?.packageName?.let(::add)
        }
        val distinctNotificationRoutes = messenger.filterNot { contact ->
            canonicalPersonName(contact.name) in directSmsNames && (
                contact.messagingPlatform.equals("SMS", true) ||
                    contact.messagingPlatform.equals("Messages", true) ||
                    contact.messagingPackageName in smsPackages
                )
        }
        return (listOfNotNull(directNumber) + phoneContacts + distinctNotificationRoutes).distinctBy { contact ->
            contact.messengerShortcutId
                ?: contact.messagingIdentity
                ?: "${contact.messagingPlatform.orEmpty().lowercase()}:${contact.name.trim().lowercase()}:${PhoneNumberUtils.normalizeNumber(contact.number)}"
        }.sortedWith(
            compareBy<ContactResult> { contactNameMatchRank(it.name, query) }
                .thenBy { it.name.length }
                .thenBy { it.name.lowercase() }
                .thenByDescending { it.messagingIsDefault }
        ).take(limit)
    }

    internal fun contactNameMatchRank(name: String, query: String): Int =
        ContactNameLogic.rank(name, query)

    internal fun canonicalPersonName(name: String): String =
        ContactNameLogic.canonicalize(name)

    fun sendCommanderMessage(contact: ContactResult, body: String): CommanderMessageDelivery {
        if (body.isBlank()) return CommanderMessageDelivery("Type a message first", status = "message not sent")
        val contactId = contact.commanderMessagingContactId
            ?: return CommanderMessageDelivery("Houston contact is unavailable", status = "message not sent")
        val route = CommanderMessagingContacts.bestRoute(appContext, contactId, contact.messagingIdentity)
        return CommanderMessageDelivery(
            "${route?.platform ?: "This conversation"} cannot be sent from Houston without notification access",
            status = "message not sent",
        )
    }

    fun messageContact(activity: Activity, contact: ContactResult, body: String = ""): String? {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.number)}")).apply {
            putExtra("sms_body", body)
        }
        return startOrFallback(activity, intent, null, "No messaging app is installed")
    }

    suspend fun sendMessageContact(contact: ContactResult, body: String): String? {
        if (contact.messengerShortcutId != null) {
            if (body.isBlank()) return "Type a message first"
            return MessengerChatStore.openDraft(appContext, contact, body)
        }
        if (!SmsSettings.directEnabled(appContext)) return messageContactInternal(contact, body)
        if (body.isBlank()) return "Type a message first"
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "SMS permission required · enable it in Command settings"
        }
        return suspendCancellableCoroutine { continuation ->
            val action = "${appContext.packageName}.SMS_SENT.${System.nanoTime()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    runCatching { appContext.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(
                        if (resultCode == Activity.RESULT_OK) null else "Message could not be sent · code $resultCode",
                    )
                }
            }
            ContextCompat.registerReceiver(appContext, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }
            val sentIntent = PendingIntent.getBroadcast(
                appContext,
                action.hashCode(),
                Intent(action).setPackage(appContext.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) appContext.getSystemService(SmsManager::class.java)
                    else @Suppress("DEPRECATION") SmsManager.getDefault()
                manager.sendTextMessage(contact.number, null, body, sentIntent, null)
            }.onFailure {
                runCatching { appContext.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resume("Message could not be sent")
            }
        }
    }

    private fun messageContactInternal(contact: ContactResult, body: String = ""): String? {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.number)}")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(appContext.packageManager) != null) {
            runCatching {
                appContext.startActivity(intent)
                null
            }.getOrElse { "No messaging app is installed" }
        } else "No messaging app is installed"
    }

    fun callContact(activity: Activity, contact: ContactResult): String? = startOrFallback(
        activity,
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(contact.number)}")),
        null,
        "No phone app is installed",
    )

    internal data class ContactMatch(val number: String, val matchedText: String)

    internal fun findContact(query: String): ContactMatch? = findContactPrefix(query, exactOnly = true)

    internal fun findContactPrefix(input: String, exactOnly: Boolean = false): ContactMatch? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var best: ContactMatch? = null
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)
                val matches = if (exactOnly) name.equals(input, true) else input.startsWith(name, true)
                if (matches && (best == null || name.length > best!!.matchedText.length)) {
                    best = ContactMatch(cursor.getString(numberColumn), name)
                }
            }
            best
        }
    }

    fun executeCall(activity: Activity, text: String): String? {
        if (text.isBlank()) return "Type a name or number after #"
        val number = findContact(text)?.number ?: text.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) return "Contact not found"
        return startOrFallback(activity, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")), null, "No phone app is installed")
    }

    fun executeMessage(activity: Activity, text: String): String? {
        if (text.isBlank()) return "Type a name or number after @"
        val contact = findContactPrefix(text)
        val directNumber = text.substringBefore(' ').filter { it.isDigit() || it == '+' }
        val number = contact?.number ?: directNumber
        if (number.isBlank()) return "Start with a contact name or phone number"
        val body = if (contact != null) text.removePrefix(contact.matchedText).trim() else text.substringAfter(' ', "")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", body)
        }
        return startOrFallback(activity, intent, null, "No messaging app is installed")
    }

    private fun startOrFallback(activity: Activity, primary: Intent, fallback: Intent?, unavailable: String): String? {
        val pm = appContext.packageManager
        val chosen = if (primary.resolveActivity(pm) != null) primary else fallback
        if (chosen == null || chosen.resolveActivity(pm) == null) return unavailable
        return runCatching {
            startActivityAndFinish(activity, chosen)
            null
        }.getOrElse { error ->
            if (error is SecurityException) "Permission denied by the target app" else unavailable
        }
    }

    private fun startActivityAndFinish(activity: Activity, intent: Intent) {
        val launchOptions = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        activity.startActivity(intent, launchOptions)
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}

/** Pure name helpers (no Android deps) for unit testing. */
internal object ContactNameLogic {
    fun canonicalize(name: String): String = name
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun rank(name: String, query: String): Int {
        val candidate = canonicalize(name)
        val needle = canonicalize(query)
        return when {
            candidate == needle -> 0
            candidate.startsWith(needle) -> 1
            candidate.split(' ').any { it.startsWith(needle) } -> 2
            else -> 3
        }
    }
}

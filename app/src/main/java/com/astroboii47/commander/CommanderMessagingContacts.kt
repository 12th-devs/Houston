package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MessagingDiscovery(
    val identity: String,
    val packageName: String,
    val platform: String,
    val title: String,
    val shortcutId: String?,
    val lastSeen: Long,
    val address: String? = null,
)

data class CommanderMessagingContact(
    val id: String,
    val name: String,
    val identities: List<String>,
    val defaultIdentity: String? = null,
)

data class MessagingPlatformOption(
    val key: String,
    val label: String,
    val packageNames: Set<String>,
    val aliases: Set<String> = emptySet(),
)

data class SmsContactCandidate(val name: String, val number: String, val label: String)

object CommanderMessagingContacts {
    private const val PREFS = "commander_messaging_contacts"
    private const val DISCOVERIES = "discoveries"
    private const val CONTACTS = "contacts"
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    val revision = mutableIntStateOf(0)
    private val _revisionFlow = MutableStateFlow(0)
    val revisionFlow: StateFlow<Int> = _revisionFlow.asStateFlow()
    private fun bumpRevision() {
        revision.intValue++
        _revisionFlow.value++
    }
    private val builtInPlatforms = listOf(
        MessagingPlatformOption("sms", "SMS", emptySet(), setOf("text", "messages")),
        MessagingPlatformOption("whatsapp", "WhatsApp", setOf("com.whatsapp"), setOf("wa")),
        MessagingPlatformOption("messenger", "Messenger", setOf("com.facebook.orca"), setOf("msg", "fb")),
        MessagingPlatformOption("signal", "Signal", setOf("org.thoughtcrime.securesms")),
        MessagingPlatformOption("telegram", "Telegram", setOf("org.telegram.messenger"), setOf("tg")),
        MessagingPlatformOption("discord", "Discord", setOf("com.discord")),
    )

    fun platformOptions(context: Context): List<MessagingPlatformOption> {
        val knownPackages = discoveries(context).map(MessagingDiscovery::packageName).toSet()
        val installed = builtInPlatforms.filter { option ->
            option.key == "sms" || option.packageNames.any { packageName ->
                packageName in knownPackages || runCatching {
                    context.packageManager.getApplicationInfo(packageName, 0)
                }.isSuccess
            }
        }
        val builtInPackages = builtInPlatforms.flatMap(MessagingPlatformOption::packageNames).toSet()
        val discovered = discoveries(context)
            .filterNot { it.packageName in builtInPackages }
            .distinctBy(MessagingDiscovery::packageName)
            .map { discovery ->
                MessagingPlatformOption(
                    discovery.platform.lowercase().replace(Regex("[^a-z0-9]+"), "").ifBlank { "messages" },
                    discovery.platform,
                    setOf(discovery.packageName),
                )
            }
        return (installed + discovered).distinctBy(MessagingPlatformOption::key)
    }

    fun platformForInput(context: Context, input: String): MessagingPlatformOption? {
        val token = input.trim().substringBefore(' ').lowercase()
        return platformOptions(context).firstOrNull { token == it.key || token in it.aliases }
    }

    fun platformSuggestions(context: Context, input: String): List<ContactResult> {
        if (input.contains(' ')) return emptyList()
        val needle = input.trim().lowercase()
        return platformOptions(context).filter { option ->
            needle.isBlank() || option.key.startsWith(needle) || option.label.startsWith(needle, true) ||
                option.aliases.any { it.startsWith(needle) }
        }.map { option ->
            ContactResult(
                name = option.label,
                number = "",
                label = "search ${option.label} conversations",
                messagingPlatform = option.label,
                messagingPlatformScope = option.key,
                messagingPlatformIcon = option.packageNames.firstNotNullOfOrNull { loadCachedPlatformIcon(context, it) },
            )
        }
    }

    fun platformIcon(context: Context, option: MessagingPlatformOption): android.graphics.Bitmap? {
        val packageName = option.packageNames.firstOrNull() ?: if (option.key == "sms") {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).resolveActivity(context.packageManager)?.packageName
        } else null
        return packageName?.let { platformIcon(context, it) }
    }

    private fun platformIcon(context: Context, packageName: String): android.graphics.Bitmap? = runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(72, 72)
    }.getOrNull()

    fun cachePlatformIcon(context: Context, packageName: String, bitmap: Bitmap?) {
        if (packageName.isBlank() || bitmap == null) return
        runCatching {
            FileOutputStream(platformIconFile(context, packageName)).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    fun loadCachedPlatformIcon(context: Context, packageName: String): Bitmap? = runCatching {
        val persistent = platformIconFile(context, packageName)
        val legacy = legacyPlatformIconFile(context, packageName)
        val source = persistent.takeIf(File::exists) ?: legacy.takeIf(File::exists)
        source?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }.getOrNull()

    private fun platformIconFile(context: Context, packageName: String) =
        File(context.filesDir, "message_platform_${packageName.hashCode().toUInt().toString(16)}.png")

    private fun legacyPlatformIconFile(context: Context, packageName: String) =
        File(context.cacheDir, "message_platform_${packageName.hashCode().toUInt().toString(16)}.png")

    fun isMessagingPackage(context: Context, packageName: String): Boolean =
        builtInPlatforms.any { packageName in it.packageNames } || discoveries(context).any { it.packageName == packageName }

    fun discoveries(context: Context): List<MessagingDiscovery> {
        val now = System.currentTimeMillis()
        val linked = contacts(context).flatMap(CommanderMessagingContact::identities).toSet()
        val stored = parseArray(context, DISCOVERIES)
        val parsed = stored.mapNotNull(::parseDiscovery)
            .filter { it.identity in linked || now - it.lastSeen <= RETENTION_MS }
        if (parsed.size != stored.size) saveDiscoveries(context, parsed)
        return parsed.sortedByDescending(MessagingDiscovery::lastSeen)
    }

    fun recent(context: Context): List<MessagingDiscovery> {
        val linked = contacts(context).flatMap(CommanderMessagingContact::identities).toSet()
        return discoveries(context).filterNot { it.identity in linked }
    }

    fun contacts(context: Context): List<CommanderMessagingContact> =
        parseArray(context, CONTACTS).mapNotNull(::parseContact)
            .sortedBy { it.name.lowercase() }

    fun create(context: Context, discovery: MessagingDiscovery, name: String = discovery.title): CommanderMessagingContact {
        val contact = CommanderMessagingContact(
            UUID.randomUUID().toString(),
            name.trim().ifBlank { discovery.title },
            listOf(discovery.identity),
            discovery.identity,
        )
        saveContacts(context, contacts(context) + contact)
        return contact
    }

    fun addTo(context: Context, contactId: String, identity: String) {
        saveContacts(context, contacts(context).map {
            if (it.id == contactId) it.copy(
                identities = (it.identities + identity).distinct(),
                defaultIdentity = it.defaultIdentity ?: identity,
            ) else it
        })
    }

    fun setDefault(context: Context, contactId: String, identity: String) {
        saveContacts(context, contacts(context).map { contact ->
            if (contact.id == contactId && identity in contact.identities) contact.copy(defaultIdentity = identity) else contact
        })
    }

    /** Pure SMS normalization for identity keys: keeps digits and a leading +. */
    fun normalizedSmsNumber(number: String): String {
        val digits = number.filter(Char::isDigit)
        return if (number.trimStart().startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
    }

    fun smsIdentity(normalized: String): String = "sms::$normalized"

    fun addSmsTo(context: Context, contactId: String, candidate: SmsContactCandidate) {
        val normalized = normalizedSmsNumber(candidate.number)
        if (normalized.isBlank()) return
        val identity = smsIdentity(normalized)
        val current = discoveries(context).associateBy(MessagingDiscovery::identity).toMutableMap()
        current[identity] = MessagingDiscovery(
            identity = identity,
            packageName = "sms",
            platform = "SMS",
            title = candidate.name,
            shortcutId = null,
            lastSeen = System.currentTimeMillis(),
            address = candidate.number,
        )
        saveDiscoveries(context, current.values.toList())
        addTo(context, contactId, identity)
    }

    fun smsContacts(context: Context, query: String, limit: Int = 30): List<SmsContactCandidate> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val needle = query.trim()
        val selection = if (needle.isBlank()) null else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = if (needle.isBlank()) null else arrayOf("%$needle%")
        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            ),
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            buildList {
                while (cursor.moveToNext() && size < limit) {
                    val number = cursor.getString(numberColumn)
                    add(SmsContactCandidate(
                        cursor.getString(nameColumn),
                        number,
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            cursor.getInt(typeColumn),
                            "phone",
                        ).toString().lowercase(),
                    ))
                }
            }.distinctBy { PhoneNumberUtils.normalizeNumber(it.number) }
        }.orEmpty()
    }

    fun removeFrom(context: Context, contactId: String, identity: String) {
        saveContacts(context, contacts(context).map { contact ->
            if (contact.id == contactId) {
                val remaining = contact.identities.filterNot { it == identity }
                contact.copy(
                    identities = remaining,
                    defaultIdentity = if (contact.defaultIdentity == identity) remaining.firstOrNull() else contact.defaultIdentity,
                )
            }
            else contact
        })
    }

    fun rename(context: Context, contactId: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        saveContacts(context, contacts(context).map { if (it.id == contactId) it.copy(name = clean) else it })
    }

    fun deleteContact(context: Context, contactId: String) {
        saveContacts(context, contacts(context).filterNot { it.id == contactId })
    }

    fun deleteDiscovery(context: Context, identity: String) {
        saveDiscoveries(context, discoveries(context).filterNot { it.identity == identity })
    }

    fun clearRecent(context: Context) {
        val linked = contacts(context).flatMap(CommanderMessagingContact::identities).toSet()
        saveDiscoveries(context, discoveries(context).filter { it.identity in linked })
    }

    fun search(
        context: Context,
        query: String,
        limit: Int,
        platform: MessagingPlatformOption? = null,
    ): List<ContactResult> {
        val needle = query.trim()
        val discoveries = discoveries(context).associateBy(MessagingDiscovery::identity)
        return contacts(context).asSequence()
            .filter { contact ->
                contact.identities.any { identity ->
                    val route = discoveries[identity]
                    route != null && (platform == null || route.packageName in platform.packageNames)
                } && (needle.isBlank() || contact.name.contains(needle, true) || contact.identities.any { identity ->
                    discoveries[identity]?.title?.contains(needle, true) == true
                })
            }
            .sortedWith(
                compareBy<CommanderMessagingContact> { rankContactName(it.name, needle) }
                    .thenBy { it.name.length }.thenBy { it.name.lowercase() },
            )
            .flatMap { contact ->
                contact.identities.sortedByDescending { it == contact.defaultIdentity }.asSequence().mapNotNull { identity ->
                    val route = discoveries[identity] ?: return@mapNotNull null
                    if (platform != null && route.packageName !in platform.packageNames) return@mapNotNull null
                    if (needle.isNotBlank() && !contact.name.contains(needle, true) && !route.title.contains(needle, true)) {
                        return@mapNotNull null
                    }
                    ContactResult(
                        name = contact.name,
                        number = route.address.orEmpty(),
                        label = staticRouteLabel(route),
                        commanderMessagingContactId = contact.id,
                        messagingIdentity = route.identity,
                        messagingPlatform = route.platform,
                        messagingPackageName = route.packageName,
                        messagingPlatformIcon = loadCachedPlatformIcon(context, route.packageName),
                        messagingIsDefault = identity == contact.defaultIdentity,
                    )
                }
            }
            .take(limit)
            .toList()
    }

    /**
     * Preferred messaging route for a Houston person. Notification-backed
     * live-reply lookup was removed: this returns the preferred stored
     * discovery, falling back to the default identity, with no Hub lookup.
     */
    fun selectBestRoute(
        available: List<MessagingDiscovery>,
        defaultIdentity: String?,
        preferredIdentity: String? = null,
    ): MessagingDiscovery? {
        if (available.isEmpty()) return null
        preferredIdentity?.let { preferred ->
            available.firstOrNull { it.identity == preferred }?.let { return it }
        }
        return available.firstOrNull { it.identity == defaultIdentity } ?: available.firstOrNull()
    }

    fun bestRoute(context: Context, contactId: String, preferredIdentity: String? = null): MessagingDiscovery? {
        val contact = contacts(context).firstOrNull { it.id == contactId } ?: return null
        val known = discoveries(context).associateBy(MessagingDiscovery::identity)
        val available = contact.identities.mapNotNull(known::get)
        return selectBestRoute(available, contact.defaultIdentity, preferredIdentity)
    }

    fun identity(packageName: String, shortcutId: String?, title: String): String {
        val stable = shortcutId?.takeIf(String::isNotBlank)
            ?: title.trim().lowercase().replace(Regex("\\s+"), " ")
        return "$packageName::$stable"
    }

    private fun parseArray(context: Context, key: String): List<JSONObject> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun parseDiscovery(value: JSONObject): MessagingDiscovery? = runCatching {
        MessagingDiscovery(
            value.getString("identity"),
            value.getString("package"),
            value.getString("platform"),
            value.getString("title"),
            value.optString("shortcut").takeIf(String::isNotBlank),
            value.getLong("seen"),
            value.optString("address").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    private fun parseContact(value: JSONObject): CommanderMessagingContact? = runCatching {
        val links = value.getJSONArray("identities")
        CommanderMessagingContact(
            value.getString("id"),
            value.getString("name"),
            List(links.length()) { links.getString(it) },
            value.optString("defaultIdentity").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    internal fun rankContactName(name: String, needle: String): Int {
        val candidate = name.trim().lowercase()
        val query = needle.trim().lowercase()
        return when {
            candidate == query -> 0
            candidate.startsWith(query) -> 1
            candidate.split(Regex("\\s+")).any { it.startsWith(query) } -> 2
            else -> 3
        }
    }

    private fun staticRouteLabel(route: MessagingDiscovery): String =
        if (route.packageName == "sms" || route.platform.equals("SMS", true)) "SMS · carrier message"
        else "${route.platform} · ${route.title}"

    private fun saveDiscoveries(context: Context, values: List<MessagingDiscovery>) {
        val array = JSONArray()
        values.forEach { value -> array.put(JSONObject().apply {
            put("identity", value.identity)
            put("package", value.packageName)
            put("platform", value.platform)
            put("title", value.title)
            put("shortcut", value.shortcutId.orEmpty())
            put("seen", value.lastSeen)
            put("address", value.address.orEmpty())
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DISCOVERIES, array.toString()).apply()
        bumpRevision()
    }

    private fun saveContacts(context: Context, values: List<CommanderMessagingContact>) {
        val array = JSONArray()
        values.forEach { value -> array.put(JSONObject().apply {
            put("id", value.id)
            put("name", value.name)
            put("identities", JSONArray(value.identities))
            put("defaultIdentity", value.defaultIdentity.orEmpty())
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CONTACTS, array.toString()).apply()
        bumpRevision()
    }
}

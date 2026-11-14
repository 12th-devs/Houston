package com.astroboii47.commander

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object SystemActionsComms {
    fun comms(): List<SystemAction> = listOf(
        SystemAction(
            id = "contacts.search", name = "Search contacts",
            description = "Search device contacts by name.",
            category = ActionCategory.READ_ONLY,
            permissions = listOf(Manifest.permission.READ_CONTACTS),
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    return@SystemAction ActionResult.fail(
                        "Contacts permission is needed",
                        mapOf("permission" to Manifest.permission.READ_CONTACTS),
                    )
                }
                val query = args["query"].orEmpty()
                val results = queryContacts(ctx, query, 5)
                if (results.isEmpty()) ActionResult.ok("No matching contacts")
                else ActionResult.ok(results.joinToString("\n") { "${it.first} · ${it.second}" })
            },
        ),
        SystemAction(
            id = "comms.call", name = "Call contact",
            description = "Open the dialer for a contact or number. Never places a call without the dialer.",
            category = ActionCategory.CONSEQUENTIAL,
            permissions = listOf(Manifest.permission.READ_CONTACTS),
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true, description = "Contact name or number")),
            execute = { ctx, args ->
                val query = args["query"].orEmpty()
                val number = queryContacts(ctx, query, 1).firstOrNull()?.second
                    ?: query.filter { it.isDigit() || it == '+' }.takeIf(String::isNotBlank)
                    ?: return@SystemAction ActionResult.fail("Contact not found")
                if (launchIntent(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))) {
                    ActionResult.ok("Opening call to $number")
                } else ActionResult.fail("No phone app is installed")
            },
        ),
        SystemAction(
            id = "comms.message.compose", name = "Compose message",
            description = "Open a message composer for a contact. Sending needs confirmation.",
            category = ActionCategory.CONSEQUENTIAL,
            permissions = listOf(Manifest.permission.READ_CONTACTS),
            params = listOf(
                ParamSpec("query", ParamType.TEXT, required = true, description = "Contact name or number"),
                ParamSpec("body", ParamType.TEXT, required = true, maxLength = 2000),
            ),
            execute = { ctx, args ->
                val query = args["query"].orEmpty()
                val body = args["body"].orEmpty()
                val number = queryContacts(ctx, query, 1).firstOrNull()?.second
                    ?: query.filter { it.isDigit() || it == '+' }.takeIf(String::isNotBlank)
                    ?: return@SystemAction ActionResult.fail("Contact not found")
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
                    putExtra("sms_body", body)
                }
                if (launchIntent(ctx, intent)) ActionResult.ok("Message composer opened")
                else ActionResult.fail("No messaging app is installed")
            },
        ),
    )

    internal fun queryContacts(ctx: Context, query: String, limit: Int): List<Pair<String, String>> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        if (query.isBlank()) return emptyList()
        return runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        add(cursor.getString(nameColumn).orEmpty() to cursor.getString(numberColumn).orEmpty())
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}

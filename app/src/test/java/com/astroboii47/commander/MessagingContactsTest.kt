package com.astroboii47.commander

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingContactsTest {

    @Test
    fun identity_prefersShortcutId() {
        assertEquals(
            "com.whatsapp::abc123",
            CommanderMessagingContacts.identity("com.whatsapp", "abc123", "Some Title"),
        )
    }

    @Test
    fun identity_fallsBackToNormalizedTitle() {
        assertEquals(
            "com.example::hello world",
            CommanderMessagingContacts.identity("com.example", null, "  Hello   World "),
        )
        assertEquals(
            "com.example::hello world",
            CommanderMessagingContacts.identity("com.example", "  ", "Hello World"),
        )
    }

    @Test
    fun rankContactName_tiers() {
        assertEquals(0, CommanderMessagingContacts.rankContactName("Al", "al"))
        assertEquals(1, CommanderMessagingContacts.rankContactName("Albert", "al"))
        assertEquals(2, CommanderMessagingContacts.rankContactName("Zed Al", "al"))
        assertEquals(3, CommanderMessagingContacts.rankContactName("Sal", "al"))
    }

    @Test
    fun rankContactName_drivesSearchOrder() {
        val names = listOf("Sal", "Zed Al", "Albert", "Al")
        val ordered = names.sortedWith(
            compareBy<String> { CommanderMessagingContacts.rankContactName(it, "al") }
                .thenBy { it.length }.thenBy { it.lowercase() },
        )
        assertEquals(listOf("Al", "Albert", "Zed Al", "Sal"), ordered)
    }

    private fun route(identity: String) = MessagingDiscovery(
        identity = identity,
        packageName = "sms",
        platform = "SMS",
        title = identity,
        shortcutId = null,
        lastSeen = System.currentTimeMillis(),
    )

    @Test
    fun selectBestRoute_prefersExplicitIdentity() {
        val sms = route("sms::111")
        val wa = route("wa::222")
        assertEquals(
            sms,
            CommanderMessagingContacts.selectBestRoute(listOf(wa, sms), defaultIdentity = "wa::222", preferredIdentity = "sms::111"),
        )
    }

    @Test
    fun selectBestRoute_fallsBackToDefaultThenFirst() {
        val first = route("sms::111")
        val second = route("sms::222")
        assertEquals(
            second,
            CommanderMessagingContacts.selectBestRoute(listOf(first, second), defaultIdentity = "sms::222"),
        )
        assertEquals(
            first,
            CommanderMessagingContacts.selectBestRoute(listOf(first, second), defaultIdentity = null),
        )
        // Unknown preferred identity never silently jumps to another app route.
        assertEquals(
            second,
            CommanderMessagingContacts.selectBestRoute(listOf(first, second), defaultIdentity = "sms::222", preferredIdentity = "unknown"),
        )
        assertNull(
            CommanderMessagingContacts.selectBestRoute(emptyList(), defaultIdentity = null),
        )
    }

    @Test
    fun smsNormalization_stripsSeparatorsKeepsLeadingPlus() {
        assertEquals("5551234567", CommanderMessagingContacts.normalizedSmsNumber("(555) 123-4567"))
        assertEquals("+15551234567", CommanderMessagingContacts.normalizedSmsNumber("+1 (555) 123-4567"))
        assertEquals("", CommanderMessagingContacts.normalizedSmsNumber("  "))
        assertEquals("sms::5551234567", CommanderMessagingContacts.smsIdentity("5551234567"))
    }

    @Test
    fun store_hasNoHubDependency() {
        val names = CommanderMessagingContacts::class.java.declaredMethods.map { it.name }
        assertTrue(names.none { it.contains("hub", true) || it.contains("reply", true) || it.contains("retained", true) })
        assertTrue(names.contains("selectBestRoute"))
        assertTrue(names.contains("getRevisionFlow"))
    }

    @Test
    fun hubClasses_areGone() {
        listOf(
            "com.astroboii47.commander.HubRepository",
            "com.astroboii47.commander.HubSettings",
            "com.astroboii47.commander.HubItem",
            "com.astroboii47.commander.HubReplyAction",
            "com.astroboii47.commander.BeeperProvider",
        ).forEach { name ->
            assertNull(name, runCatching { Class.forName(name) }.getOrNull())
        }
    }
}

package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SystemActionEngineTest {
    @Before fun register() {
        ActionRegistry.resetForTests()
        ActionRegistry.registerDefaults()
    }

    @Test fun defaultsRegisterCoreActions() {
        val ids = ActionRegistry.all().map { it.id }.toSet()
        listOf(
            "device.flashlight.toggle", "device.brightness.get", "device.brightness.set",
            "device.volume.get", "device.volume.set", "device.ringer.set",
            "device.rotation.toggle", "device.dnd.status", "device.battery.status",
            "device.connectivity.panel", "device.bluetooth.status", "device.bluetooth.devices",
            "device.bluetooth.set", "media.status", "media.control", "media.open",
            "app.launch", "app.shortcut.launch", "app.info", "settings.open",
            "share.text", "web.search", "contacts.search", "comms.call",
            "comms.message.compose", "timer.create", "alarm.create",
            "event.create",             "tasks.todo.add", "tasks.todo.list", "tasks.todo.complete",
            "files.search", "calc.evaluate", "tasker.run", "assistant.date",
            "assistant.weather",
        ).forEach { assertTrue("missing $it", it in ids) }
        assertTrue("hub actions must stay removed", ids.none { it.contains("hub", true) || it == "comms.recent" })
    }

    @Test fun legacyAliasesResolve() {
        assertEquals("device.volume.set", ActionRegistry.find("volume_set")?.id)
        assertEquals("device.brightness.set", ActionRegistry.find("brightness_set")?.id)
        assertEquals("media.control", ActionRegistry.find("music_control")?.id)
        assertEquals("files.search", ActionRegistry.find("search_files")?.id)
        assertNull(ActionRegistry.find("rm -rf"))
        assertNull(ActionRegistry.find("../../../etc/passwd"))
    }

    @Test fun futureActionsRegisterWithoutCoreChanges() {
        val custom = SystemAction(
            id = "test.future", name = "Future", description = "Registered later.",
            category = ActionCategory.READ_ONLY,
            execute = { _, _ -> ActionResult.ok("ok") },
        )
        ActionRegistry.register(custom)
        assertEquals(custom, ActionRegistry.find("test.future"))
    }

    @Test fun validationRequiresParams() {
        val action = ActionRegistry.find("device.volume.set")!!
        assertTrue(ActionValidation.validate(action, mapOf("percent" to "40")).isSuccess)
        assertTrue(ActionValidation.validate(action, emptyMap()).isFailure)
        assertTrue(ActionValidation.validate(action, mapOf("percent" to "loud")).isFailure)
        assertTrue(ActionValidation.validate(action, mapOf("percent" to "140")).isFailure)
    }

    @Test fun booleanCoercion() {
        val action = ActionRegistry.find("device.bluetooth.set")!!
        assertEquals("true", ActionValidation.validate(action, mapOf("enabled" to "on")).getOrThrow()["enabled"])
        assertEquals("false", ActionValidation.validate(action, mapOf("enabled" to "off")).getOrThrow()["enabled"])
        assertTrue(ActionValidation.validate(action, mapOf("enabled" to "maybe")).isFailure)
    }

    @Test fun confirmationPolicy() {
        assertFalse(requiresConfirmation(ActionRegistry.find("device.volume.get")!!))
        assertFalse(requiresConfirmation(ActionRegistry.find("device.brightness.set")!!))
        assertTrue(requiresConfirmation(ActionRegistry.find("comms.call")!!))
        assertTrue(requiresConfirmation(ActionRegistry.find("comms.message.compose")!!))
        assertTrue(requiresConfirmation(ActionRegistry.find("share.text")!!))
        assertTrue(requiresConfirmation(ActionRegistry.find("tasks.todo.add")!!))
        assertTrue(requiresConfirmation(ActionRegistry.find("event.create")!!))
    }

    @Test fun confirmationKeyDeterministic() {
        val a = ActionValidation.confirmationKey("comms.call", mapOf("query" to "Alex", "x" to "1"))
        val b = ActionValidation.confirmationKey("comms.call", mapOf("x" to "1", "query" to "Alex"))
        assertEquals(a, b)
        assertNotEquals(a, ActionValidation.confirmationKey("comms.call", mapOf("query" to "Sam")))
    }

    @Test fun calculatorIsPure() {
        assertEquals("360", CalcParser.evaluate("15 * 24"))
        assertNull(CalcParser.evaluate("hello world"))
        assertNull(CalcParser.evaluate("rm -rf /"))
    }
}

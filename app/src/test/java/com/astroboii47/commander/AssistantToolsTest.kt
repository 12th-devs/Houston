package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssistantToolsTest {
    @Before fun register() {
        ActionRegistry.resetForTests()
        ActionRegistry.registerDefaults()
    }

    @Test fun nativeSupportIsConservative() {
        assertTrue(AssistantTools.supportsNativeTools(AiProvider.OpenAI))
        assertTrue(AssistantTools.supportsNativeTools(AiProvider.OpenRouter))
        assertFalse(AssistantTools.supportsNativeTools(AiProvider.Gemini))
        assertFalse(AssistantTools.supportsNativeTools(AiProvider.Anthropic))
        assertFalse(AssistantTools.supportsNativeTools(AiProvider.Device))
        assertFalse(AssistantTools.supportsNativeTools(AiProvider.LiteRt))
    }

    @Test fun fallbackParsesSingleObject() {
        val calls = AssistantTools.parseFallbackCalls("""{"tool":"device.volume.set","args":{"percent":40}}""")
        assertEquals(1, calls.size)
        assertEquals("device.volume.set", calls.first().id)
        assertEquals("40", calls.first().args["percent"])
    }

    @Test fun fallbackParsesArrayAndCodeFence() {
        val calls = AssistantTools.parseFallbackCalls(
            "```json\n[{\"tool\":\"device.flashlight.toggle\",\"args\":{}},{\"tool\":\"media.control\",\"args\":{\"action\":\"play\"}}]\n```",
        )
        assertEquals(2, calls.size)
        assertEquals("device.flashlight.toggle", calls[0].id)
        assertEquals("media.control", calls[1].id)
    }

    @Test fun fallbackDropsUnknownTools() {
        val calls = AssistantTools.parseFallbackCalls("""[{"tool":"rm_rf","args":{}},{"tool":"device.volume.get","args":{}}]""")
        assertEquals(1, calls.size)
        assertEquals("device.volume.get", calls.first().id)
    }

    @Test fun fallbackResolvesLegacyAliases() {
        val calls = AssistantTools.parseFallbackCalls("""{"tool":"volume_set","args":{"percent":30}}""")
        assertEquals(1, calls.size)
        assertEquals("device.volume.set", calls.first().id)
    }

    @Test fun deterministicFlashlight() {
        val plan = AssistantTools.deterministicPlan("Turn on my flashlight.")
        assertEquals(listOf("device.flashlight.toggle"), plan.map { it.id })
    }

    @Test fun deterministicBrightness() {
        val plan = AssistantTools.deterministicPlan("Set my brightness to 40 percent.")
        assertEquals(1, plan.size)
        assertEquals("device.brightness.set", plan.first().id)
        assertEquals("40", plan.first().args["percent"])
    }

    @Test fun deterministicOpenAndPlayIsSequential() {
        val plan = AssistantTools.deterministicPlan("Open Spotify and play music.")
        assertEquals(listOf("app.launch", "media.control"), plan.map { it.id })
        assertEquals("Spotify", plan.first().args["query"])
        assertEquals("play", plan.last().args["action"])
    }

    @Test fun deterministicMessage() {
        val plan = AssistantTools.deterministicPlan("Text Alex that I'll be there in ten minutes.")
        assertEquals(1, plan.size)
        assertEquals("comms.message.compose", plan.first().id)
        assertEquals("Alex", plan.first().args["query"])
        assertTrue(plan.first().args["body"]!!.contains("ten minutes"))
    }

    @Test fun deterministicReminder() {
        val plan = AssistantTools.deterministicPlan("Remind me to finish my homework tomorrow.")
        assertEquals(1, plan.size)
        assertEquals("tasks.todo.add", plan.first().id)
        assertTrue(plan.first().args["text"]!!.contains("homework", true))
    }

    @Test fun deterministicTimer() {
        val plan = AssistantTools.deterministicPlan("Create a 20-minute timer.")
        assertEquals(1, plan.size)
        assertEquals("timer.create", plan.first().id)
        assertEquals("1200", plan.first().args["seconds"])
    }

    @Test fun deterministicRecentMessages() {
        val plan = AssistantTools.deterministicPlan("Show me my recent messages from Sarah.")
        assertEquals(1, plan.size)
        assertEquals("comms.recent", plan.first().id)
    }

    @Test fun arbitraryCodeNeverPlans() {
        assertTrue(AssistantTools.deterministicPlan("Run rm -rf / on the shell").isEmpty())
        assertTrue(AssistantTools.deterministicPlan("Execute javascript:alert(1)").isEmpty())
        assertTrue(AssistantTools.parseFallbackCalls("Just a normal answer.").isEmpty())
    }

    @Test fun schemaCoversRegistry() {
        val payload = AssistantTools.openAiToolsPayload()
        assertTrue(payload.size >= 30)
        @Suppress("UNCHECKED_CAST")
        val functions = payload.map { it["function"] as Map<String, Any?> }
        val names = functions.map { it["name"] as String }.toSet()
        assertTrue("device.flashlight.toggle" in names)
        assertTrue("comms.call" in names)
        val volume = functions.first { it["name"] == "device.volume.set" }
        @Suppress("UNCHECKED_CAST")
        val properties = (volume["parameters"] as Map<String, Any?>)["properties"] as Map<String, Any?>
        assertTrue(properties.containsKey("percent"))
        // Translators stay in sync.
        assertEquals(payload.size, AssistantTools.geminiFunctionDeclarations().size)
        assertEquals(payload.size, AssistantTools.anthropicTools().size)
    }
}

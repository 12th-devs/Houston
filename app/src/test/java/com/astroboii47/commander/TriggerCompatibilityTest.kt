package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class TriggerCompatibilityTest {
    @Test fun allCommandKindsStayExplicit() {
        // Every trigger symbol parseCommand understands must also be explicit in the
        // unified intent parser, so fast reliable filters never get reinterpreted.
        val cases = mapOf(
            "@mom" to CommandKind.Message,
            "#mom" to CommandKind.Call,
            "-buy milk" to CommandKind.Todo,
            "!note text" to CommandKind.Note,
            "*party tomorrow" to CommandKind.Event,
            "+10 minutes tea" to CommandKind.Timer,
            ",15 * 24" to CommandKind.Calculator,
            "/cats" to CommandKind.Web,
            "?meaning of life" to CommandKind.Ask,
            " vacation photo" to CommandKind.Files,
        )
        for ((input, kind) in cases) {
            assertEquals("parseCommand($input)", kind, parseCommand(input).kind)
            val intent = SearchIntentParser.parse(input)
            assertTrue(
                "SearchIntentParser($input) must be Trigger, was $intent",
                intent is SearchIntent.Trigger,
            )
        }
    }

    @Test fun plainTextStaysUnified() {
        val plain = listOf("spotify", "mom", "bluetooth", "downloads", "15 * 24", "5 minute timer", "youtube cats")
        for (query in plain) {
            assertEquals(CommandKind.Apps, parseCommand(query).kind)
            assertFalse(
                "plain '$query' must not be an explicit trigger",
                SearchIntentParser.isExplicitTrigger(query),
            )
        }
    }
}

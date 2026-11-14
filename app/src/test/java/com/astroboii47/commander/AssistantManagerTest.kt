package com.astroboii47.commander

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class AssistantManagerTest {
    @Test fun assistGestureIsFromAssistant() {
        val invocation = AssistantManager.parseInvocation(Intent.ACTION_ASSIST, null, false)
        assertEquals(AssistantManager.InvocationKind.AssistGesture, invocation.kind)
        assertTrue(invocation.fromAssistant)
    }

    @Test fun voiceCommandIsFromAssistant() {
        val invocation = AssistantManager.parseInvocation("android.intent.action.VOICE_COMMAND", null, false)
        assertEquals(AssistantManager.InvocationKind.VoiceCommand, invocation.kind)
        assertTrue(invocation.fromAssistant)
    }

    @Test fun sharePrefillsQuery() {
        val invocation = AssistantManager.parseInvocation(Intent.ACTION_SEND, "  interesting article  ", false)
        assertEquals(AssistantManager.InvocationKind.Share, invocation.kind)
        assertEquals("interesting article", invocation.sharedText)
        assertEquals("interesting article", invocation.prefillQuery)
        assertFalse(invocation.fromAssistant)
    }

    @Test fun launcherIsNotAssistant() {
        val invocation = AssistantManager.parseInvocation(OverlayActivity.ACTION_OPEN_OVERLAY, null, false)
        assertEquals(AssistantManager.InvocationKind.Launcher, invocation.kind)
        assertFalse(invocation.fromAssistant)
        assertTrue(invocation.prefillQuery.isEmpty())
    }

    @Test fun unknownActionHandled() {
        val invocation = AssistantManager.parseInvocation("com.example.WEIRD", null, false)
        assertEquals(AssistantManager.InvocationKind.Unknown, invocation.kind)
    }

    @Test fun dismissalReturnsToPriorApp() {
        assertTrue(AssistantManager.dismissalReturnsToPriorApp())
    }

    @Test fun screenReferenceDetection() {
        assertTrue(ScreenReferenceMentions.hasReference("Summarize this screen"))
        assertTrue(ScreenReferenceMentions.hasReference("What does this error mean?"))
        assertTrue(ScreenReferenceMentions.hasReference("Search for this product"))
        assertFalse(ScreenReferenceMentions.hasReference("Turn on my flashlight"))
        assertFalse(ScreenReferenceMentions.hasReference(""))
    }
}

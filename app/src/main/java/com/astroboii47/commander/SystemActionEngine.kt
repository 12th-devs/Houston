package com.astroboii47.commander

/**
 * Thin dispatcher preserving the original SystemActionEngine entry point.
 * Device / apps / comms / productivity actions live in SystemActions* files;
 * runtime (registry, validation, executor, parser) lives in SystemActionRuntime.
 */
object BuiltInActions {
    fun all(): List<SystemAction> =
        SystemActionsDevice.device() +
            SystemActionsApps.apps() +
            SystemActionsComms.comms() +
            SystemActionsProductivity.productivity() +
            contextOps()

    private fun contextOps(): List<SystemAction> = listOf(
        SystemAction(
            id = "assistant.screenshot.describe", name = "Describe screen",
            description = "Capture is handled by voice session context; this reports availability.",
            category = ActionCategory.READ_ONLY,
            execute = { ctx, _ ->
                if (HomeTypingAccessibilityService.isConnected()) ActionResult.ok("Screenshot capture is ready")
                else ActionResult.fail("Enable Houston accessibility service to take screenshots")
            },
        ),
    )
}

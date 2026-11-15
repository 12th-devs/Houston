package com.astroboii47.commander.secondbrain.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import com.astroboii47.commander.DashTab

/**
 * Second-brain destinations. Four primary destinations form the bottom bar
 * (Home / Plan / Memory / Ask); the four legacy domains stay reachable as
 * secondary routes so no working screen is deleted.
 */
enum class SbDestination(val label: String, val icon: ImageVector, val primary: Boolean) {
    HOME("Home", Icons.Outlined.Home, true),
    PLAN("Plan", Icons.Outlined.Schedule, true),
    MEMORY("Memory", Icons.Outlined.Psychology, true),
    ASK("Ask", Icons.Outlined.ChatBubbleOutline, true),
    SCHOOL("School", Icons.Outlined.School, false),
    SWIM("Swim", Icons.Outlined.Waves, false),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth, false),
    CAPTURES("Captures", Icons.Outlined.PhotoLibrary, false);

    companion object {
        val bar: List<SbDestination> get() = entries.filter { it.primary }

        /** Legacy tab mapping (DashTab.MEMORIES is the captures library). */
        internal fun fromDashTab(tab: DashTab): SbDestination = when (tab) {
            DashTab.HOME -> HOME
            DashTab.CALENDAR -> CALENDAR
            DashTab.SCHOOL -> SCHOOL
            DashTab.SWIM -> SWIM
            DashTab.MEMORIES -> CAPTURES
        }

        internal fun toDashTab(destination: SbDestination): DashTab = when (destination) {
            HOME -> DashTab.HOME
            CALENDAR -> DashTab.CALENDAR
            SCHOOL -> DashTab.SCHOOL
            SWIM -> DashTab.SWIM
            CAPTURES -> DashTab.MEMORIES
            PLAN, MEMORY, ASK -> DashTab.HOME
        }

        fun routeOf(destination: SbDestination): String = destination.name.lowercase()

        /**
         * Parses deep-link targets used by notifications and insights.
         * Legacy "school:<id>", "swim:…", "memory:<uuid>", "calendar:…"
         * targets keep working; "plan", "ask", "memory" (agent memory),
         * and "captures" are new.
         */
        fun parseTarget(target: String?): Pair<SbDestination, String?> {
            if (target.isNullOrBlank()) return HOME to null
            val head = target.substringBefore(':').trim().lowercase()
            val rest = target.substringAfter(':', missingDelimiterValue = "").trim().ifEmpty { null }
            val destination = when (head) {
                "home" -> HOME
                "plan" -> PLAN
                "memory", "memories", "captures" -> CAPTURES
                "agent-memory" -> MEMORY
                "ask" -> ASK
                "calendar" -> CALENDAR
                "school" -> SCHOOL
                "swim" -> SWIM
                else -> return HOME to null
            }
            return destination to rest
        }
    }
}

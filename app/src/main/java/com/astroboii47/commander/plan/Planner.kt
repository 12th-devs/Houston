package com.astroboii47.commander.plan

import java.time.LocalDate

/** Minutes since midnight. */
data class DayBlock(
    val startMin: Int,
    val endMin: Int,
    val title: String,
    val kind: String, // commitment|suggestion
    val source: String = "",
    val fixed: Boolean = true,
)

data class Conflict(val a: DayBlock, val b: DayBlock) {
    val overlapMin: Int get() = minOf(a.endMin, b.endMin) - maxOf(a.startMin, b.startMin)
}

data class PlannedAssignment(
    val id: String,
    val name: String,
    val course: String,
    val dueIso: String?,
    val estMin: Int?,
    val status: String,
)

data class StudySuggestion(
    val assignmentId: String,
    val assignmentName: String = "",
    val dateIso: String,
    val startMin: Int,
    val minutes: Int,
    val rationale: String,
)

/**
 * Deterministic scheduling intelligence. Estimates use recorded history
 * where available and are always labeled; nothing here writes to any
 * calendar without approval.
 */
object Planner {
    /** School-day window used for free-block math when no events bound the day. */
    const val DAY_START = 7 * 60
    const val DAY_END = 22 * 60

    fun detectConflicts(blocks: List<DayBlock>): List<Conflict> {
        val sorted = blocks.sortedBy { it.startMin }
        val out = mutableListOf<Conflict>()
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (b.startMin >= a.endMin) break
                if (overlaps(a, b)) out.add(Conflict(a, b))
            }
        }
        return out
    }

    private fun overlaps(a: DayBlock, b: DayBlock): Boolean =
        maxOf(a.startMin, b.startMin) < minOf(a.endMin, b.endMin)

    /** Free windows inside [dayStart, dayEnd] not covered by fixed blocks. */
    fun freeBlocks(
        events: List<DayBlock>,
        dayStart: Int = DAY_START,
        dayEnd: Int = DAY_END,
        minMin: Int = 25,
    ): List<DayBlock> {
        val fixed = events.filter { it.fixed }.sortedBy { it.startMin }
        val out = mutableListOf<DayBlock>()
        var cursor = dayStart
        for (e in fixed) {
            if (e.startMin - cursor >= minMin) {
                out.add(DayBlock(cursor, e.startMin, "Open", "suggestion", fixed = false))
            }
            if (e.endMin > cursor) cursor = e.endMin
        }
        if (dayEnd - cursor >= minMin) out.add(DayBlock(cursor, dayEnd, "Open", "suggestion", fixed = false))
        return out
    }

    /**
     * Assigns study sessions for due work into free blocks, earliest-due
     * first. Returns suggestions with rationales; callers gate persistence
     * behind approval.
     */
    fun recommendStudy(
        assignments: List<PlannedAssignment>,
        blocksByDate: Map<String, List<DayBlock>>,
        today: LocalDate = LocalDate.now(),
        historyAvgMin: Map<String, Int> = emptyMap(),
        acceptedIds: Set<String> = emptySet(),
    ): List<StudySuggestion> {
        val out = mutableListOf<StudySuggestion>()
        val dated = assignments
            .filter { it.status != "graded" && it.status != "submitted" && it.status != "exempt" }
            .filter { it.id !in acceptedIds }
            .sortedBy { it.dueIso ?: "9999" }
        for (a in dated) {
            val due = a.dueIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val est = a.estMin ?: historyAvgMin[a.course] ?: 45
            // Latest sensible study day: day before due (or today if due today/past).
            val latest = if (due == null || due.isAfter(today)) (due ?: today.plusDays(2)).minusDays(1) else today
            var remaining = est
            var day = today
            var guard = 0
            while (remaining > 0 && !day.isAfter(latest) && guard++ < 14) {
                val key = day.toString()
                val free = freeBlocks(blocksByDate[key] ?: emptyList())
                    .sortedByDescending { it.endMin - it.startMin }
                for (slot in free) {
                    if (remaining <= 0) break
                    val take = minOf(remaining, slot.endMin - slot.startMin, 90)
                    if (take < 20 && remaining > 20) continue
                    out.add(
                        StudySuggestion(
                            assignmentId = a.id,
                            assignmentName = a.name,
                            dateIso = key,
                            startMin = slot.startMin,
                            minutes = take,
                            rationale = "${a.name} is due ${a.dueIso ?: "soon"}; " +
                                "${dayName(day, today)} has a ${take}-min opening. " +
                                "Estimate ${est} min${if (a.estMin == null) " (no history — default 45 min)" else " (from history)"}.",
                        ),
                    )
                    remaining -= take
                }
                day = day.plusDays(1)
            }
            if (remaining > 0) {
                out.add(
                    StudySuggestion(
                        assignmentId = a.id,
                        assignmentName = a.name,
                        dateIso = latest.toString(),
                        startMin = -1,
                        minutes = remaining,
                        rationale = "${a.name} needs ${remaining} more min than open blocks allow before ${a.dueIso ?: "its deadline"} — overloaded day, consider re-planning.",
                    ),
                )
            }
        }
        return out
    }

    /** 0..1 load indicator for a day: committed minutes vs available window. */
    fun overloadScore(committedMin: Int, freeMin: Int): Double {
        val total = committedMin + freeMin
        if (total <= 0) return 0.0
        return (committedMin.toDouble() / total).coerceIn(0.0, 1.0)
    }

    fun overloadLabel(score: Double): String = when {
        score >= 0.85 -> "Overloaded — re-plan or defer something."
        score >= 0.65 -> "Heavy — protect the remaining openings."
        score >= 0.4 -> "Balanced."
        else -> "Light — good day for deep work."
    }

    private fun dayName(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

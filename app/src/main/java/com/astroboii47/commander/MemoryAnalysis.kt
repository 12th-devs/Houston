package com.astroboii47.commander

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Replaceable model-provider interface for memory AI tasks. The bundled
 * [LocalHeuristicProvider] works fully offline (keyword extraction,
 * summarization, collection suggestions); a network/LLM provider can be
 * plugged in later without touching UI or persistence.
 */

enum class MemoryTask {
    EXTRACT_MEMORY, SUMMARIZE_MEMORY, SUGGEST_COLLECTIONS,
    COLLECTION_INSIGHT, MEMORY_QUERY,
}

data class ExtractedMemory(
    val title: String = "",
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val category: String = "",
    val places: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val suggestedCollections: List<String> = emptyList(),
)

data class QueryAnswer(
    val answer: String,
    val referencedIds: List<String>,
    val actions: List<String> = emptyList(),
)

interface ModelProvider {
    val name: String
    suspend fun extract(body: String): ExtractedMemory
    suspend fun summarize(body: String): String
    suspend fun suggestCollections(body: String, available: List<String>): List<String>
    suspend fun collectionInsight(collectionName: String, bodies: List<Pair<String, String>>): Pair<String, List<String>>
    suspend fun query(question: String, candidates: List<MemoryRecord>): QueryAnswer
}

/** Offline heuristic: deterministic, private, always available. */
object LocalHeuristicProvider : ModelProvider {
    override val name: String = "on-device-heuristic"

    private val STOP = setOf(
        "the", "and", "for", "with", "this", "that", "from", "have", "were", "been",
        "about", "into", "what", "when", "where", "your", "you", "was", "are",
    )

    override suspend fun extract(body: String): ExtractedMemory {
        val words = body.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+"))
            .filter { it.length >= 4 && it !in STOP }
        val freq = words.groupingBy { it }.eachCount()
        val tags = freq.entries.sortedByDescending { it.value }.take(5).map { it.key }
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = firstLine.take(60).ifEmpty { body.take(60) }
        val category = when {
            body.contains("swim", true) || body.contains("breaststroke", true) || body.contains("practice", true) -> "Swimming"
            body.contains("homework", true) || body.contains("assignment", true) || body.contains("grade", true) -> "School"
            body.contains("houston", true) || body.contains("app", true) || body.contains("code", true) -> "Projects"
            else -> "Personal"
        }
        return ExtractedMemory(
            title = title,
            summary = body.take(280),
            tags = tags,
            category = category,
            suggestedCollections = listOf(category),
        )
    }

    override suspend fun summarize(body: String): String {
        val sentences = body.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return (sentences.take(2).joinToString(" ").ifEmpty { body }).take(400)
    }

    override suspend fun suggestCollections(body: String, available: List<String>): List<String> {
        val lower = body.lowercase()
        return available.filter { name -> lower.contains(name.lowercase()) }.take(3)
    }

    override suspend fun collectionInsight(collectionName: String, bodies: List<Pair<String, String>>): Pair<String, List<String>> {
        if (bodies.isEmpty()) return "No memories in $collectionName yet." to emptyList()
        val ids = bodies.take(5).map { it.first }
        return "${bodies.size} memor${if (bodies.size == 1) "y" else "ies"} in $collectionName. " +
            "Most recent: ${bodies.maxByOrNull { it.first }?.second?.take(140).orEmpty()}" to ids
    }

    override suspend fun query(question: String, candidates: List<MemoryRecord>): QueryAnswer {
        val terms = question.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP + setOf("what", "did", "find", "about") }
        if (terms.isEmpty() || candidates.isEmpty()) {
            return QueryAnswer("I couldn't find anything matching that in your memories.", emptyList())
        }
        val scored = candidates.mapNotNull { m ->
            val hay = "${m.title}\n${m.body}\n${m.note}\n${m.tags.joinToString(" ")}".lowercase()
            val hits = terms.count { it in hay }
            if (hits == 0) null else m to hits
        }.sortedByDescending { it.second }.take(3)
        if (scored.isEmpty()) {
            return QueryAnswer("Nothing in your memories matches “$question”.", emptyList())
        }
        val ids = validateReferencedIds(scored.map { it.first.uuid }, candidates.map { it.uuid }.toSet())
        val answer = buildString {
            append("Found ${ids.size} memor${if (ids.size == 1) "y" else "ies"}: ")
            append(scored.map { "“${it.first.title.ifEmpty { it.first.body.take(40) }}”" }.joinToString("; "))
            append(".")
        }
        return QueryAnswer(answer, ids, ids.map { "memory:$it" })
    }
}

object MemoryAnalysisRunner {
    private var provider: ModelProvider = LocalHeuristicProvider

    fun setProvider(p: ModelProvider) {
        provider = p
    }

    fun providerName(): String = provider.name

    /** Enqueues extraction for a saved memory; retry-safe and idempotent. */
    fun enqueueExtract(context: Context, uuid: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val mem = MemoryStore.loadMemories(context, includeTrashed = true).firstOrNull { it.uuid == uuid } ?: return@launch
            if (mem.body.isBlank()) {
                MemoryStore.upsert(context, mem.copy(analysisStatus = MemoryAnalysisStatus.COMPLETE))
                return@launch
            }
            try {
                val out = provider.extract(mem.body)
                val cols = MemoryStore.loadCollections(context)
                val colByName = cols.associateBy { it.name.lowercase() }
                val colIds = out.suggestedCollections.mapNotNull { colByName[it.lowercase()]?.uuid }.take(3)
                val updated = mem.copy(
                    summary = "AI summary (${provider.name}): ${out.summary}",
                    tags = (mem.tags + out.tags).distinct().take(20),
                    places = (mem.places + out.places).distinct().take(10),
                    actions = (mem.actions + out.actions).distinct().take(10),
                    collections = (mem.collections + colIds).distinct(),
                    analysisStatus = MemoryAnalysisStatus.COMPLETE,
                    analysisError = null,
                )
                MemoryStore.upsert(context, updated)
                val events = listOf(InsightEvent("memory.analysis_completed", refs = listOf("memory:$uuid")))
                InsightStore.ingest(
                    context,
                    listOf(
                        Insight(
                            id = "memory.analysis_completed:$uuid",
                            kind = InsightKind.MEMORY,
                            title = "Memory organized: ${out.title.take(50)}",
                            explanation = "Tags: ${out.tags.take(3).joinToString(", ").ifEmpty { "none" }}. " +
                                "Suggested in: ${out.suggestedCollections.joinToString(", ").ifEmpty { "—" }}.",
                            refs = listOf("memory:$uuid"),
                            score = 30,
                            actionLabel = "Open memory",
                            actionTarget = "memory:$uuid",
                        ),
                    ),
                )
            } catch (e: Exception) {
                MemoryStore.upsert(
                    context,
                    mem.copy(
                        analysisStatus = MemoryAnalysisStatus.FAILED,
                        analysisError = (e.message ?: "analysis failed").take(200),
                    ),
                )
            }
        }
    }
}

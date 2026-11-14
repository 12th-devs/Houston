package com.astroboii47.commander

import android.content.Context
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Houston Memories: local-first personal memory management inspired by the
 * Samsung Memory spec (`docs` reference: Memories/Samsung-Memory-App-Spec.md).
 *
 * Independent implementation on supported Android APIs: app-private files,
 * SharedPreferences-backed JSON, substring FTS over memory + tags +
 * collections + places/actions. No Samsung providers, no signature
 * permissions. Save-first-then-analyze; generated text never overwrites
 * source content.
 */

enum class MemoryAnalysisStatus { PENDING, COMPLETE, FAILED }

data class MemoryRecord(
    val uuid: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "", // user-authored source text, never overwritten by AI
    val note: String = "", // user note
    val summary: String = "", // generated, labeled
    val createdIso: String = Instant.now().toString(),
    val modifiedIso: String = Instant.now().toString(),
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val collections: List<String> = emptyList(), // collection uuids
    val places: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(), // app-private file names / content URIs
    val voiceUris: List<String> = emptyList(),
    val source: String = "manual", // manual|share|voice|photo|screenshot|link
    val originInfo: String = "",
    val analysisStatus: MemoryAnalysisStatus = MemoryAnalysisStatus.PENDING,
    val analysisError: String? = null,
    val trashed: Boolean = false,
)

data class MemoryCollection(
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val manual: Boolean = true,
    val customRank: Int = 0,
    val modifiedIso: String = Instant.now().toString(),
    val autoIncluded: Set<String> = emptySet(), // memory uuids
    val excluded: Set<String> = emptySet(),
)

object MemoryStore {
    private const val PREFS = "houston_memories"
    private const val KEY_MEMORIES = "memories_json"
    private const val KEY_COLLECTIONS = "collections_json"
    private const val KEY_HISTORY = "search_history"
    private const val MAX_MEMORIES = 2000

    fun defaultCollections(): List<MemoryCollection> = listOf(
        MemoryCollection(uuid = "col-swimming", name = "Swimming", manual = false),
        MemoryCollection(uuid = "col-school", name = "School", manual = false),
        MemoryCollection(uuid = "col-projects", name = "Projects", manual = false),
        MemoryCollection(uuid = "col-personal", name = "Personal", manual = false),
    )

    fun loadMemories(context: Context, includeTrashed: Boolean = false): List<MemoryRecord> {
        val raw = SettingsStore.getString(context, PREFS, KEY_MEMORIES) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching { memoriesFromJson(parseJson(raw)) }.getOrDefault(emptyList())
            .filter { includeTrashed || !it.trashed }
            .sortedByDescending { it.modifiedIso }
    }

    fun saveMemories(context: Context, memories: List<MemoryRecord>) {
        val trimmed = memories.sortedByDescending { it.modifiedIso }.take(MAX_MEMORIES)
        SettingsStore.edit(context, PREFS) { putString(KEY_MEMORIES, memoriesToJson(trimmed)) }
    }

    fun upsert(context: Context, memory: MemoryRecord): MemoryRecord {
        val all = loadMemories(context, includeTrashed = true).associateBy { it.uuid }.toMutableMap()
        val now = Instant.now().toString()
        all[memory.uuid] = memory.copy(modifiedIso = now)
        saveMemories(context, all.values.toList())
        return all[memory.uuid]!!
    }

    fun trash(context: Context, uuid: String) {
        saveMemories(context, loadMemories(context, true).map { if (it.uuid == uuid) it.copy(trashed = true) else it })
    }

    fun restore(context: Context, uuid: String) {
        saveMemories(context, loadMemories(context, true).map { if (it.uuid == uuid) it.copy(trashed = false) else it })
    }

    fun deletePermanently(context: Context, uuid: String) {
        saveMemories(context, loadMemories(context, true).filter { it.uuid != uuid })
    }

    fun emptyTrash(context: Context) {
        saveMemories(context, loadMemories(context, true).filter { !it.trashed })
    }

    fun loadCollections(context: Context): List<MemoryCollection> {
        val raw = SettingsStore.getString(context, PREFS, KEY_COLLECTIONS)
        if (raw.isNullOrBlank()) {
            val defaults = defaultCollections()
            saveCollections(context, defaults)
            return defaults
        }
        return runCatching { collectionsFromJson(parseJson(raw)) }.getOrDefault(defaultCollections())
    }

    fun saveCollections(context: Context, collections: List<MemoryCollection>) {
        SettingsStore.edit(context, PREFS) { putString(KEY_COLLECTIONS, collectionsToJson(collections)) }
    }

    fun ensureCollection(context: Context, name: String): MemoryCollection {
        val all = loadCollections(context).toMutableList()
        all.firstOrNull { it.name.equals(name.trim(), true) }?.let { return it }
        val c = MemoryCollection(name = name.trim())
        all.add(c)
        saveCollections(context, all)
        return c
    }

    /**
     * Local full-text search over title/body/note/summary + tags +
     * collections + places + actions. Returns newest-first matches.
     */
    fun search(context: Context, query: String, limit: Int = 50): List<MemoryRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        recordHistory(context, query.trim())
        val terms = q.split(Regex("\\s+")).filter { it.length >= 2 }
        if (terms.isEmpty()) return emptyList()
        val collections = loadCollections(context).associateBy { it.uuid }
        return loadMemories(context).mapNotNull { m ->
            val hay = buildString {
                append(m.title).append('\n').append(m.body).append('\n').append(m.note).append('\n')
                append(m.summary).append('\n').append(m.tags.joinToString(" ")).append('\n')
                append(m.collections.mapNotNull { collections[it]?.name }.joinToString(" ")).append('\n')
                append(m.places.joinToString(" ")).append('\n').append(m.actions.joinToString(" "))
            }.lowercase()
            val hits = terms.count { it in hay }
            if (hits == 0) null else m to hits
        }.sortedWith(compareByDescending<Pair<MemoryRecord, Int>> { it.second }.thenByDescending { it.first.modifiedIso })
            .take(limit).map { it.first }
    }

    fun recordHistory(context: Context, query: String) {
        val hist = searchHistory(context).toMutableList()
        hist.remove(query)
        hist.add(0, query)
        SettingsStore.edit(context, PREFS) { putString(KEY_HISTORY, hist.take(30).joinToString("\n")) }
    }

    fun searchHistory(context: Context): List<String> {
        val raw = SettingsStore.getString(context, PREFS, KEY_HISTORY) ?: return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(30)
    }

    fun clearHistory(context: Context) {
        SettingsStore.edit(context, PREFS) { remove(KEY_HISTORY) }
    }

    /** Export user memories (never session tokens) as JSON for backup. */
    fun exportJson(context: Context): String = memoriesToJson(loadMemories(context, includeTrashed = true))

    fun mediaDir(context: Context): File = File(context.filesDir, "memories").apply { mkdirs() }

    // ----- JSON -----

    internal fun memoriesToJson(list: List<MemoryRecord>): String = buildString {
        append('[')
        list.forEachIndexed { i, m ->
            if (i > 0) append(',')
            append("{\"u\":\"${escapeJson(m.uuid)}\",\"t\":\"${escapeJson(m.title)}\",\"b\":\"${escapeJson(m.body)}\",")
            append("\"n\":\"${escapeJson(m.note)}\",\"s\":\"${escapeJson(m.summary)}\",")
            append("\"c\":\"${escapeJson(m.createdIso)}\",\"m\":\"${escapeJson(m.modifiedIso)}\",")
            append("\"f\":${m.favorite},\"tr\":${m.trashed},\"src\":\"${escapeJson(m.source)}\",")
            append("\"org\":\"${escapeJson(m.originInfo)}\",\"st\":\"${m.analysisStatus}\",")
            append("\"err\":${m.analysisError?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            fun arr(v: List<String>) = v.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
            append("\"tags\":${arr(m.tags)},\"cols\":${arr(m.collections)},\"pl\":${arr(m.places)},")
            append("\"ac\":${arr(m.actions)},\"img\":${arr(m.imageUris)},\"vx\":${arr(m.voiceUris)}}")
        }
        append(']')
    }

    internal fun memoriesFromJson(v: JVal): List<MemoryRecord> {
        val arr = v.arr() ?: return emptyList()
        return arr.mapNotNull { e ->
            if (e !is JVal.Obj) return@mapNotNull null
            fun strs(k: String) = e.objArr(k)?.mapNotNull { (it as? JVal.Str)?.s } ?: emptyList()
            MemoryRecord(
                uuid = e.objStr("u") ?: return@mapNotNull null,
                title = e.objStr("t").orEmpty(),
                body = e.objStr("b").orEmpty(),
                note = e.objStr("n").orEmpty(),
                summary = e.objStr("s").orEmpty(),
                createdIso = e.objStr("c") ?: Instant.now().toString(),
                modifiedIso = e.objStr("m") ?: Instant.now().toString(),
                favorite = e.objBool("f") == true,
                tags = strs("tags"),
                collections = strs("cols"),
                places = strs("pl"),
                actions = strs("ac"),
                imageUris = strs("img"),
                voiceUris = strs("vx"),
                source = e.objStr("src") ?: "manual",
                originInfo = e.objStr("org").orEmpty(),
                analysisStatus = runCatching { MemoryAnalysisStatus.valueOf(e.objStr("st") ?: "PENDING") }.getOrDefault(MemoryAnalysisStatus.PENDING),
                analysisError = e.objStr("err"),
                trashed = e.objBool("tr") == true,
            )
        }
    }

    internal fun collectionsToJson(list: List<MemoryCollection>): String = buildString {
        append('[')
        list.forEachIndexed { i, c ->
            if (i > 0) append(',')
            append("{\"u\":\"${escapeJson(c.uuid)}\",\"n\":\"${escapeJson(c.name)}\",\"man\":${c.manual},")
            append("\"rank\":${c.customRank},\"m\":\"${escapeJson(c.modifiedIso)}\",")
            append("\"auto\":[${c.autoIncluded.joinToString(",") { "\"${escapeJson(it)}\"" }}],")
            append("\"exc\":[${c.excluded.joinToString(",") { "\"${escapeJson(it)}\"" }}]}")
        }
        append(']')
    }

    internal fun collectionsFromJson(v: JVal): List<MemoryCollection> {
        val arr = v.arr() ?: return emptyList()
        return arr.mapNotNull { e ->
            if (e !is JVal.Obj) return@mapNotNull null
            MemoryCollection(
                uuid = e.objStr("u") ?: return@mapNotNull null,
                name = e.objStr("n") ?: return@mapNotNull null,
                manual = e.objBool("man") != false,
                customRank = e.obj("rank")?.num()?.toInt() ?: 0,
                modifiedIso = e.objStr("m") ?: Instant.now().toString(),
                autoIncluded = e.objArr("auto")?.mapNotNull { (it as? JVal.Str)?.s }?.toSet() ?: emptySet(),
                excluded = e.objArr("exc")?.mapNotNull { (it as? JVal.Str)?.s }?.toSet() ?: emptySet(),
            )
        }
    }
}

/** Validates model-returned memory IDs against the candidate set (never link unknown). */
fun validateReferencedIds(referenced: List<String>, candidates: Set<String>): List<String> =
    referenced.filter { it in candidates }.distinct()

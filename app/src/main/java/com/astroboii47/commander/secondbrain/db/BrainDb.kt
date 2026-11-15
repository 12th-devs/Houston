package com.astroboii47.commander.secondbrain.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.ColumnInfo
import android.content.Context
import java.util.UUID

fun newBrainId(prefix: String): String =
    prefix + "-" + UUID.randomUUID() + "-" + System.currentTimeMillis().toString(36)

/** Verification states: user text is never silently upgraded. */
object Verification {
    const val USER_CONFIRMED = "user_confirmed"
    const val IMPORTED = "imported"
    const val INFERRED = "inferred"
    const val TEMPORARY = "temporary"
    const val SUPERSEDED = "superseded"
}

/** Fact kinds: canonical knowledge, not chat logs. */
object FactKind {
    const val FACT = "fact"
    const val GOAL = "goal"
    const val EXPERIENCE = "experience"
    const val PROJECT = "project"
    const val TEMPORARY = "temporary"
}

@Entity(tableName = "brain_facts")
data class BrainFact(
    @PrimaryKey val id: String = newBrainId("fact"),
    val kind: String = FactKind.FACT,
    val text: String = "",
    val confidence: Double = 0.5,
    val verification: String = Verification.INFERRED,
    val sourceRef: String = "",
    val sourceKind: String = "manual",
    /** CSV of L2-normalized floats (houston-hash-128/v1). Empty = not embedded. */
    val embedding: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val supersededBy: String? = null,
)

@Entity(tableName = "brain_links")
data class BrainLink(
    @PrimaryKey(autoGenerate = true) val row: Int = 0,
    val fromId: String = "",
    val toId: String = "",
    val relation: String = "related",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey val id: String = newBrainId("thread"),
    val title: String = "Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = newBrainId("msg"),
    val threadId: String = "",
    val role: String = "user", // user|assistant|tool|system-note
    val text: String = "",
    /** JSON array of executed tool ids for this turn. */
    val toolRefs: String = "[]",
    /** JSON array of cited source refs shown with the answer. */
    val sources: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "agent_runs")
data class AgentRun(
    @PrimaryKey val id: String = newBrainId("run"),
    val trigger: String = "ask", // ask|proactive|automation|plan
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String = "running", // running|done|failed|cancelled|awaiting_approval
    val stepsJson: String = "[]",
    val summary: String = "",
)

@Entity(tableName = "plan_blocks")
data class PlanBlock(
    @PrimaryKey val id: String = newBrainId("plan"),
    val dateIso: String = "",
    val startMin: Int = 0,
    val endMin: Int = 0,
    /** commitment = real event/deadline; suggestion = AI proposal needing approval. */
    val kind: String = "commitment",
    val title: String = "",
    /** assignment id this block was scheduled for, if any (dedupes re-suggesting). */
    val refId: String = "",
    /** calendar|school|swim|memory|agent */
    val source: String = "agent",
    /** proposed|accepted|done|dismissed */
    val status: String = "proposed",
    val rationale: String = "",
)

class BrainConverters {
    @TypeConverter
    fun fromStringList(v: List<String>): String = v.joinToString("\u0001")

    @TypeConverter
    fun toStringList(s: String): List<String> =
        if (s.isEmpty()) emptyList() else s.split("\u0001")
}

@Dao
interface BrainFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: BrainFact)

    @Query("SELECT * FROM brain_facts WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): BrainFact?

    @Query("SELECT * FROM brain_facts WHERE supersededBy IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BrainFact>

    @Query("SELECT * FROM brain_facts WHERE kind = :kind AND supersededBy IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun byKind(kind: String, limit: Int): List<BrainFact>

    @Query("SELECT * FROM brain_facts WHERE supersededBy IS NULL")
    suspend fun allLive(): List<BrainFact>

    @Query("SELECT * FROM brain_facts WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<BrainFact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: BrainFactFts)

    @Query("DELETE FROM brain_facts_fts WHERE factId = :factId")
    suspend fun deleteFts(factId: String)

    @Query("SELECT factId FROM brain_facts_fts WHERE brain_facts_fts MATCH :query LIMIT :limit")
    suspend fun matchIds(query: String, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM brain_facts WHERE supersededBy IS NULL")
    suspend fun liveCount(): Int

    @Query("DELETE FROM brain_facts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM brain_facts WHERE verification = 'temporary' AND (expiresAt IS NOT NULL AND expiresAt < :now)")
    suspend fun purgeExpiredTemporary(now: Long): Int
}

@Dao
interface BrainLinkDao {
    @Insert
    suspend fun insert(link: BrainLink)

    @Query("SELECT * FROM brain_links WHERE fromId = :id OR toId = :id ORDER BY createdAt DESC LIMIT :limit")
    suspend fun forFact(id: String, limit: Int): List<BrainLink>

    @Query("DELETE FROM brain_links WHERE fromId = :id OR toId = :id")
    suspend fun deleteFor(id: String)
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(thread: ChatThread)

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun threads(limit: Int): List<ChatThread>

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun thread(id: String): ChatThread?

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun deleteThread(id: String)

    @Insert
    suspend fun insertMessage(msg: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC LIMIT 200")
    suspend fun messages(threadId: String): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessages(threadId: String)

    @Query("UPDATE chat_threads SET updatedAt = :at WHERE id = :id")
    suspend fun touchThread(id: String, at: Long)
}

@Dao
interface AgentRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: AgentRun)

    @Query("SELECT * FROM agent_runs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AgentRun>

    @Query("SELECT * FROM agent_runs WHERE status = 'awaiting_approval' ORDER BY startedAt DESC LIMIT :limit")
    suspend fun awaitingApproval(limit: Int): List<AgentRun>
}

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(block: PlanBlock)

    @Query("SELECT * FROM plan_blocks WHERE dateIso = :date ORDER BY startMin ASC")
    suspend fun forDate(date: String): List<PlanBlock>

    @Query("SELECT * FROM plan_blocks WHERE dateIso >= :from AND dateIso <= :to ORDER BY dateIso ASC, startMin ASC")
    suspend fun forRange(from: String, to: String): List<PlanBlock>

    @Query("UPDATE plan_blocks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM plan_blocks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM plan_blocks WHERE kind = 'suggestion' AND status = 'dismissed'")
    suspend fun purgeDismissed(): Int
}

/**
 * Full-text companion for [BrainFact], synced manually on every write path
 * (remember/edit/delete). String ids can't back an external-content FTS
 * table, so this standalone index maps matches back to fact ids. All reads
 * go through [BrainFactDao.matchIds] with a sanitized query and fall back
 * to substring ranking when FTS yields nothing.
 */
@Fts4
@Entity(tableName = "brain_facts_fts")
data class BrainFactFts(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    @ColumnInfo(name = "factId") val factId: String = "",
    @ColumnInfo(name = "text") val text: String = "",
    @ColumnInfo(name = "kind") val kind: String = "",
)

@Database(
    entities = [BrainFact::class, BrainLink::class, ChatThread::class, ChatMessage::class, AgentRun::class, PlanBlock::class, BrainFactFts::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(BrainConverters::class)
abstract class SecondBrainDb : RoomDatabase() {
    abstract fun facts(): BrainFactDao
    abstract fun links(): BrainLinkDao
    abstract fun chat(): ChatDao
    abstract fun runs(): AgentRunDao
    abstract fun plans(): PlanDao

    companion object {
        @Volatile private var instance: SecondBrainDb? = null

        fun get(context: Context): SecondBrainDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SecondBrainDb::class.java,
                    "second_brain.db",
                ).build().also { instance = it }
            }
    }
}

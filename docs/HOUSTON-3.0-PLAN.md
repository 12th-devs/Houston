# Houston 3.0 — Implementation Plan

Source of truth for the 3.0 build. Preserves the existing Commander command bar,
Dashboard (Home/Calendar/School), iCal sync, PowerSchool auth + retrieval, and
single-goal swim log. Everything new is additive and file-based (no new Gradle
dependencies), matching the current `SettingsStore` + internal JSON architecture.

## 0. Baseline (verified before changes)

- App: `com.astroboii47.commander`, Kotlin 2.3.21, AGP 8.13.2, compile/target SDK 36,
  min SDK 29, Material 3 1.3.1 via Compose BOM 2025.12.01.
- Existing debug APK: `app/build/outputs/apk/debug/app-debug.apk` (rebuilt during 3.0).
- Working: command bar overlay, Dashboard Home (month grid + agenda + school summary
  + single swim card), Calendar (iCal feeds + PowerSchool due dates), School
  (guardian login, GPA, letter counts, movers, class detail), SwimStore
  (one event + baseline/best/goal + dated log entries), PowerSchoolClient
  (portal-only cookie jar, no stored password), ICalStore (https feeds).
- External evidence: `C:\Users\colej\Projects\Meet Mobile\MEET-MOBILE-REPORT.md` +
  `API-VERIFICATION.md` + `meetclient/api.py` (allowlisted
  `https://awmobile.active.com/rest/v4/mmo/` endpoints, `auth_key` SHA-256
  parameter digest, no-login public competition reads verified for meets
  5773706 and 5733104). Samsung reference:
  `C:\Users\colej\Projects\Memories\Samsung-Memory-App-Spec.md` (Room tables,
  FTS, collections, trash, ZoomableImageView 1–8x, WorkManager analysis workers,
  query-and-answer ID filtering).

## 1. Architecture (no new deps)

- `InsightEngine.kt` — event bus (in-memory + persisted), Insight model
  (stable ID, type, title, explanation, timestamp, source refs, score, expiry,
  read/acked), deduplication by stable ID, briefing + weekly-review generators,
  notification-channel prefs. Pure Kotlin core + thin Android store.
- `SchoolAlerts.kt` — grade/new-assignment/missing/upcoming thresholds, class
  alerts, event emission (`school.*`), honest "no changes" compression.
- `SwimPlatform.kt` — `SwimCourse` (SCY/SCM/LCM, never cross-compared),
  `SwimmerProfile`, multi-`SwimGoal` (event+course+target+dates+start/current
  best+history+state), rich `PracticeSession` (pool, distance, duration,
  strokes, sets, RPE, technique, coach, notes, links), `RaceResult`
  (meet/event/course/time/place/splits/provenance), deterministic result
  dedupe key, `MeetMobileClient` Kotlin port (digest + allowlist + stop-on-
  restriction, no login/purchase/favorites/mutation routes), analytics
  (progression, PBs, volume, split deltas).
- `SwimPlatformStore.kt` — JSON persistence, one-way migration from legacy
  `swim_log` prefs + `swim.json`, preserves old data.
- `MemoryStore.kt` — Memory/Collection/Tag/relations/places/actions/media refs,
  FTS-substring search + candidate-ID validation, search history, trash/restore,
  export JSON. Mirrors Samsung tables without Samsung providers.
- `MemoryAnalysis.kt` — `ModelProvider` interface with typed tasks
  (`extract_memory`, `summarize_memory`, `suggest_collections`,
  `collection_insight`, `memory_query`), local heuristic implementation
  (works offline), status/error model, retry-safe enqueue via coroutines.
- `AutomationEngine.kt` — triggers (schedule/calendar/grade/assignment/swim/
  memory/manual), conditions (date/school-day/threshold/event/improvement/
  connectivity), actions (briefing/notification/reminder/memory/screen/refresh/
  insight/launch), execution history, enable/disable. Scheduler: app-start +
  manual + BootReceiver hook; no exact-timing claims.
- UI: `BriefingUi.kt`, `SwimPlatformUi.kt`, `MemoriesUi.kt`, `AutomationsUi.kt`;
  Dashboard grows to Home / Calendar / School / Swim / Memories tabs, Home
  gains briefing + insights + memories-recap sections. Share receiver via
  `ACTION_SEND` intent filter on DashboardActivity (text + images into Memories).
- Notifications: `InsightNotifier.kt` — channels (briefing, school, swim,
  memories, automations), granular prefs, no repeat alerts for acked insights.

## 2. Staged milestones

1. Plan + inventory (this file). ✅
2. Core domain: InsightEngine + SchoolAlerts + Briefing. ✅/next
3. Swim platform + Meet Mobile adapter + store migration. ✅/next
4. Memories store + analysis + capture. ✅/next
5. Automation engine + notifier. ✅/next
6. UI integration + M3 Expressive polish + share receiver + manifest. ✅/next
7. Tests (pure-JVM) + assembleDebug + test run + doc update. ✅/next

## 3. Integration risks & mitigations

- Meet Mobile: only allowlisted GET/POST competition reads; 401/402/403/429 stop
  with honest "unavailable/restricted" states; manual result entry always works;
  swimmer identity confirmed before import; raw times preserved, never compared
  across courses.
- PowerSchool: keep portal client untouched; snapshots compared only within the
  same courseId|term; GPA labeled descriptive, never presented as official.
- Memories: save-first-then-analyze; generated text never overwrites source;
  trashed items excluded from search; export/delete supported.
- Background: no exact-timing promises; everything retry-safe and idempotent;
  repeated syncs never duplicate insights (stable IDs).
- Privacy: session cookies only, no passwords on disk, no tokens in logs;
  AI processing optional + labeled; media stays app-private.

## 4. Test plan (JVM unit, must actually run)

`HoustonIntelligenceTest`: swim time round-trip, multi-course isolation,
progress math, grade-mover thresholds, insight dedup/expiry, memory search +
candidate-ID validation, automation condition evaluation, Meet Mobile digest
vector (`a:1;b:2` → SHA-256), result dedupe keys.

## 5. Definition of done

Existing Home/Calendar/School/PowerSchool/iCal/swim-data flows intact;
new Swim/Memories/Briefing/Insights/Automations useful empty-states;
honest offline/restricted/error states; dark+light dynamic color; TalkBack
labels on interactive controls; `./gradlew :app:assembleDebug` green.

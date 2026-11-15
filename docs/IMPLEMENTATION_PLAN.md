# Implementation Plan — Houston Second Brain

Phases are sequential; each ends with a green build, green unit tests, and
working legacy features. New dependencies are limited to
Navigation-Compose, Room, and WorkManager.

## Phase 0 — Audit & baseline (this plan)

- [x] Full audit → `SECOND_BRAIN_AUDIT.md` (incl. subagent-drafted code review,
      15 defects fixed, no mock-only features accepted)
- [x] Vision → `PRODUCT_VISION.md`; target arch → `ARCHITECTURE.md`
- [x] Baseline: `./gradlew :app:testDebugUnitTest` green — 159 tests, 0 failures (22 files)
- [x] Baseline: `./gradlew :app:assembleDebug` green
- [x] Baseline record: `app-debug.apk` SHA-256
      `B73D21C59516C8BCFD25C865D2BF5BA1B0919FB0282E84219CBF82FDA19A1`
      (debug build, 2026-11-14)

Acceptance: docs merged; tests green; no source changes yet.

## Phase 1 — Navigation + design system — DONE

- `SbDestination` (4 primary Home/Plan/Memory/Ask + 4 secondary, labels,
  deep-link parsing incl. legacy `school:/swim:/memory:` targets) +
  `SbScaffold.SbBottomBar` (same floating geometry, now labeled) wired into
  `DashboardActivity`; full NavHost with all routes, edge-to-edge,
  predictive-back via NavController. Home deduped (agenda dump removed in
  favor of Up Next + Plan).
- Tests: `SbDestinationTest` (4 cases). Suite green, assembleDebug green.
- `SbTheme` (dynamic palette only), tokens (spacing/type/shape), `SbCard`,
  `SbSection`, shared dialog/form/empty/loading/error components.
- Existing tab bodies mounted as destinations unchanged; `DashTab` kept as
  fallback behind a settings toggle until parity, then removed.
- UI semantics tests for the four destinations.

Acceptance: app launches to the new scaffold; all five legacy tabs reachable
and identical in content; TalkBack + dark/light verified; tests green.

## Phase 2 — Unified context layer — DONE (`BrainSnapshot` + `BrainContext.promptBlock` feed Home/Ask/Plan/proactive; conflict math in `Planner`; tests in `SecondBrainTest`)

- Pure-Kotlin `Snapshot` builder over PowerSchool/iCal/Swim/Memories/tasks
  with `source/observedAt/verified|inferred` on every item.
- Provenance-preserving read adapters; no store rewrites.
- Conflict detection (overlaps, overloaded days) as pure functions.
- Tests: builder fixtures, conflict cases, dedup, term isolation.

Acceptance: briefing, Plan, and Ask consume the same snapshot in tests;
legacy screens untouched.

## Phase 3 — Persistent memory — DONE (`secondbrain.db` + FTS4 index with sanitized MATCH and substring fallback; dedup/contradiction/confirm/edit/supersede/delete; review queue + consolidation report; Memory UI with verification labels)

- `secondbrain.db` (Room): memories (+states/confidence/links), tasks,
  events, agent runs. FTS literal search; hash-128 embeddings retained.
- Consolidation worker (WorkManager, unique, backoff): dedupe,
  contradiction review queue, inference promotion.
- Memory UI: list/search/detail/edit/delete; capture box creates
  `imported` records pending extract.
- Tests: CRUD, contradiction, search parity, migration read-path, backup.

Acceptance: airplane-mode memory Q&A works offline; nothing inferred is
stored as fact; export round-trips.

## Phase 4 — Conversation + tools — DONE (Ask destination: streaming, history, approvals with correct levels, cancel, sources, usage; legacy auto-confirm deleted; UNTRUSTED-DATA wrapping on every tool path; maxSteps honored end to end)

- Ask destination: streaming chat, history, memory-grounded answers with
  citations, structured action previews, follow-ups, cancel, voice input.
- Delete legacy auto-confirm; all consequential actions gate through
  `NeedsConfirmation`; L2 standing permissions UI.
- Prompt hygiene: immutable safety prefix, untrusted-data wrapping,
  http(s) link allowlist, pinned model IDs.
- Tests: permission matrix, injection fixtures, schema validation,
  cancellation mid-run.

Acceptance: "what do I need to do this week?" answers from real data with
sources; no unconfirmed external action possible in tests.

## Phase 5 — Planning + bounded agent — DONE (Plan destination with timeline/groups/calendar, approval-gated suggestions with real names, no re-suggest of accepted blocks, AgentEngine bounded loop with budget/cancel/run history)

- Plan destination: Today timeline (commitments vs suggestions), Upcoming
  groups, Calendar view; conflict/open-block detection; study-session
  recommendations with stated reasons.
- Agent executor: budgets (5 steps, time-boxed, 1 retry), verification,
  `agent_runs` history UI.
- Daily/weekly plan generation with explanation.
- Tests: overlap detection, prioritization, duplicate-execution guards,
  plan reproducibility on fixtures.

Acceptance: Friday-deadline scenario produces a cited Wednesday study
block; suggestions never self-apply.

## Phase 6 — Proactive intelligence — DONE (WorkManager 6h pass with quiet-hours + notify-level + master toggle, deduped stable-id insights, store-not-notify during quiet hours, run history)

- Event triggers wired to the engine (grades, assignments, calendar,
  goals, memory, schedules); relevance scoring + stable-ID dedup.
- Quiet hours, per-category frequency caps, per-item mute; disable switch.
- Morning + weekly briefings move onto the new pipeline (alarms kept,
  content upgraded).
- Tests: ranking order, dedup across restarts, quiet-hour suppression,
  disable coverage.

Acceptance: a schedule change produces one actionable notice with what /
why / action — never a badge farm.

## Phase 7 — Polish, reliability, docs — DONE (186 unit tests green; lint + assemble verified; release notes + signed build; manual on-device QA checklist in ALPHA_3_2_RELEASE_NOTES.md)

- Perf pass (snapshot caching, model-call budgets, smooth scrolling),
  motion/transition review, accessibility sweep, background-reliability
  tests, permission-boundary tests, data-migration verification, full
  end-to-end test run, docs refresh, release notes.

Acceptance: brief §12 quality bar met; release checklist in repo.

## Working rules

One phase at a time; smallest complete vertical slice per change; legacy
features keep working (verified by the untouched suite); no mock-only
features; parallel subagents only for research, never concurrent edits to
the same files.

## Build record � second-brain foundation (2026-11-14)

Shipped in the working tree (uncommitted):

- Deps: Room 2.8.5 (KSP 2.3.14), WorkManager 2.10.0, Navigation-Compose 2.8.5, lifecycle-viewmodel-compose + runtime-compose 2.8.7, DataStore Preferences 1.1.1. KAPT dropped (cannot read Kotlin 2.3 metadata).
- secondbrain.db (Room, v1, no auto-migration yet): brain_facts (+verification/confidence/embeddings), brain_links, chat_threads/messages, agent_runs, plan_blocks.
- secondbrain.context.BrainContext: unified snapshot + budgeted grounded prompt block with UNTRUSTED-DATA tagging.
- memory.BrainMemory: user_confirmed/imported/inferred/temporary/superseded, dedup at cosine 0.92, heuristic contradiction flags, consolidation.
- plan.Planner: pure conflict/free-block/study-recommendation/overload functions.
- gent.AgentEngine (L1/L2/3 over ActionRegistry tiers, bounded run, cancel, history, usage accounting) + gent.AgentStream (SSE streaming on Gemini, progressive elsewhere).
- UI: BrainHome / PlanScreen (Today/Upcoming/Calendar + approval-gated suggestions + Domains) / MemoryScreen (verification states, confirm/edit, consolidation, captures link) / AskScreen + AskViewModel (threads, streaming, approvals, cancel, usage).
- NavHost with 8 routes (home/plan/memory/ask primary + school/swim/calendar/captures legacy); DashboardActivity state hoisting preserved; SbDestination extended to 4+4 (tests updated).
- BrainWorker (6h WorkManager, constraints, quiet-hours + notify-level gating, stable-id dedup) scheduled alongside existing alarms; BrainPrefs quiet hours + usage in DataStore; agent activity history surfaced in Automations UI.
- HomePage.kt retired from navigation (still compiles); legacy SwimCard unused. Nothing deleted.

Deltas from this plan: bottom bar is icon-only per direct user instruction (overrides the labeled-bar sketch); HomePage kept as fallback source, not behind a settings toggle.

Verification: 181 unit tests green (18 new SecondBrainTest + updated SbDestinationTest), :app:assembleDebug green.

Deferred / known gaps: Room migration tests need instrumentation (no Robolectric); legacy prefs stay on SharedPreferences (new prefs use DataStore); no vector DB � hash-128 embeddings retained; Gemini model auto-upgrade + editable system-prompt floor unresolved; keystore/backup hardening per audit not yet done.

# Architecture — Houston Second Brain

Target architecture. Everything here coexists with the current code until
its replacement is tested; nothing is deleted in the dark.

## 1. Layering

```
Compose UI (Home / Plan / Memory / Ask)
  └─ ViewModels (one per destination, Flow state, one-shot events)
       └─ SecondBrain orchestrator (context → plan → tools → verify → learn)
            ├─ Context Engine   (unified snapshot from all domains)
            ├─ Memory Engine    (Room + embeddings + provenance)
            ├─ Tool Runtime     (ActionRegistry, permissions, confirmation)
            ├─ Model Router     (provider abstraction, budgets, accounting)
            └─ Proactive Engine (event triggers, ranking, dedup, quiet hours)
                 └─ Domain stores (PowerSchool, iCal, Swim, Memories legacy,
                    Tasks) — unchanged files, new read adapters
```

New code lives in feature packages (`…/secondbrain/…`, `…/memory/…`,
`…/agent/…`, `…/plan/…`) — the flat legacy package is frozen for new
files. A tiny manual `AppContainer` (no DI framework, consistent with the
existing codebase) wires singletons; ViewModels take constructor deps.

## 2. UI

- One `NavHost` (Navigation-Compose — the one sanctioned new dependency
  alongside Room/WorkManager) with four labeled destinations; the old
  `DashTab` switch becomes the fallback until parity, then is removed.
- M3 Expressive from the dynamic palette only. One `SbTheme` replaces the
  three theming paths; one `SbCard`/`SbSection` replaces ~20 `SectionCard`
  copies; one dialog/form style; shared empty/loading/error states.
- Home renders: greeting → briefing → Up Next → ≤3 radar items → input.
  Everything else moves behind taps. Existing detail screens (ClassDetail,
  MemoryDetailSheet, import dialogs) are reused as destinations/sheets.

## 3. Unified context model (`Context Engine`)

`Snapshot(day, nextEvent, dueSoon[assignments, deadlines], gradeDeltas,
trainingLoad, openGoals, unackedInsights, recentMemories, conflicts)` —
built by pure functions over the existing stores plus the new Room tables.
Every item carries `source, observedAt, verified|inferred`. The briefing,
Plan, and Ask all read the same snapshot: one grounding pipeline, three
renderings. Unit-testable without Android.

## 4. Memory Engine (hybrid)

- **Room database `secondbrain.db`** (new; legacy JSON stores untouched):
  `memories` (fact/inference states, confidence, supersedes links),
  `memory_links` (typed relations), `tasks`, `events`, `agent_runs`,
  `insights` mirror. FTS4 for literal search; embeddings stay in the
  proven hash-128 columnar sidecar until a cloud embedding provider is
  configured.
- Consolidation worker: dedupe, contradiction detection (same subject,
  conflicting values → keep both, mark `needsReview`, never silently
  overwrite), promotion of repeated inferences to review queue.
- Every stored item: provenance URI (e.g. `school:course|term`,
  `memory:<uuid>`), timestamps, confidence, state machine
  `confirmed|imported|inferred|superseded|deleted`.

## 5. Agent runtime

Built on the existing `ActionRegistry` tiers, hardened:

- Tool schemas become the single contract (native payloads for
  OpenRouter/OpenAI/LiteRT, translators for Gemini/Anthropic, validated
  fallback JSON). `MAX_STEPS=5`, per-run time budget, retry cap 1,
  cancellation token plumbed from UI to executor.
- Permission gates: L1 auto, L2 `NeedsConfirmation` card (standing
  permissions are per-tool, revocable, listed in Settings), L3 always
  confirm. Legacy `runLegacy` pre-confirm shim is deleted; all paths go
  through `AssistantTools.runConversation` gating.
- Prompt hygiene: immutable safety prefix (facts vs inferences, cite-or-
  refuse, never claim unverified success) + editable user suffix; tool
  results wrapped as untrusted data with provenance tags; Markwon links
  allowlisted to http(s); model ID pinned and logged per answer.
- `agent_runs` table records plan, tool calls, results, verification —
  the activity history UI reads this, not logcat.

## 6. Model routing

`ModelRouter` selects per workload: heuristics/on-device for
classification and extraction; cheap cloud for briefings and summaries;
strongest configured model for multi-step plans. Context budgets per call
(snapshot slice, top-k memories with IDs ⊆ candidates, recent turns),
token accounting with per-day caps, and a usage screen. No provider SDKs
beyond what exists; HTTP stays `HttpURLConnection`.

## 7. Background

- WorkManager (new): periodic sync (replacing the chained sync alarm),
  memory consolidation, weekly review build. Constraints: unmetered,
  charging, backoff, unique work — idempotent by stable IDs.
- Kept: inexact `AlarmManager` morning briefing (brief allows alarms for
  this), foreground `dataSync` for user-initiated Sync-now, `BootReceiver`
  rescheduling. No exact alarms, no `SCHEDULE_EXACT_ALARM` request.
- If near-real-time push is ever needed, it goes through a server design
  doc first — never silent background hacks, never credentials on device
  beyond sessions.

## 8. Data & privacy

- Secrets: Keystore stays; add EncryptedSharedPreferences for the few new
  tokens (automation standing permissions reference, not secrets).
- At-rest: new Room DB uses SQLCipher only if the measured cost is
  acceptable; otherwise app-private + `allowBackup=false` for the new DB
  with user-controlled export, and plaintext legacy files documented as
  sandbox-protected. No cookies, keys, or tokens in prompts, logs, or
  backups (`SettingsBackup` allowlist extended to the new tables).
- Prompt-injection defense: retrieved content is data, never instructions;
  tool schemas validated; consequential previews show exact payloads.

## 9. Testing strategy

Keep all 22 unit-test files green; add: memory CRUD/contradiction/search,
context-builder conflicts, scheduler overlap detection, permission matrix
(L1/L2/L3 + standing grants + legacy-path denial), cancellation and
idempotency (duplicate execution), background worker retry/recovery,
migration (legacy JSON → Room read path, no data loss), injection fixtures
(malicious calendar/file names must not steer the planner), output-schema
validation. UI: Compose semantics assertions for the four destinations
(empty/loading/error/content). No network/LLM in unit tests — fakes only.

## 10. As-built package map (2026-11-14)

- secondbrain.db � SecondBrainDb + BrainFact/BrainLink/ChatThread/ChatMessage/AgentRun/PlanBlock + DAOs.
- secondbrain.di � BrainGraph manual singletons + BrainPrefs (DataStore: proactive, quiet window, notify level, usage).
- secondbrain.context � BrainSnapshot + BrainContext builder + prompt block.
- secondbrain.work � BrainWorker (proactive pass) wired next to HoustonSyncService/alarms.
- secondbrain.ui � SbDestination (4 primary + 4 legacy), SbScaffold (icon-only bar), BrainBar/BrainHome/PlanScreen/MemoryScreen/AskScreen/AskViewModel.
- memory � BrainMemory. gent � AgentEngine + AgentStream (+SSE in GeminiApi). plan � Planner.
- Navigation-Compose NavHost lives in DashboardActivity; all screen state stays hoisted there. AskViewModel is the only ViewModel (activity-scoped).

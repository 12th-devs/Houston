# Second Brain — Application Audit

Date: 2026-11-14. Scope: `C:\Users\colej\Projects\Houston\Commander`
(package `com.astroboii47.commander`), 143 Kotlin files in one flat package,
single Gradle module. Read-only audit; nothing was changed.

## 1. Baseline facts

- App: Houston / Commander, `versionName 3.1.0-alpha.1`, `versionCode 16`.
- Kotlin 2.3.21, AGP 8.13.2, compile/target SDK 36, min SDK 29.
- UI: Jetpack Compose (BOM 2025.12.01), Material 3 1.3.1. No NavController;
  navigation is manual tab state. No Hilt/Dagger/Koin (manual singletons).
- Persistence: SharedPreferences + app-private JSON files. No Room,
  DataStore, EncryptedSharedPreferences, Retrofit, OkHttp, Coil, or
  WorkManager/JobScheduler.
- AI deps: `mlkit-genai-prompt` (AICore), `litertlm-android` 0.18.0
  (user-imported Gemma), `markwon-core` (markdown render).
- Privileged deps: Shizuku API + provider (task switching, snapshots).
- Tests: 22 unit-test files under `app/src/test`, pure JVM (JUnit +
  kotlin.test). Flagship: `HoustonIntelligenceTest` (229 lines).
- Pre-existing docs: `HOUSTON-3.0-PLAN.md` (3.0 build plan, still accurate),
  `SETUP.md` (older Houston Bar launcher product), `PERMISSIONS_AUDIT.md`,
  `EXTRACTION_PLAN.md`, per-alpha release notes, `AI_TOOL_CALLING_RESEARCH.md`.

## 2. Entry points and navigation

Activities (from `AndroidManifest.xml`):

| Activity | Role |
|---|---|
| `DashboardActivity` (launcher alias `Houston`) | Main 5-tab hub: Home / Calendar / School / Swim / Memories. Owns all shared state. |
| `MainActivity` | Legacy command-panel launcher; warms engine, finishes on dismiss. |
| `OverlayActivity` | Voice/Assist overlay (`CommandOverlayApp` → 3600-line `OverlayController`). |
| `NativeSearchActivity` | Touch edge-gesture search, forced-dark. |
| `SettingsActivity` | ~640-line settings + local-model importer. |
| `AiAnswerActivity` | Focused AI chat (`AiConversation`). |
| `AssistantLogActivity` | Debug log viewer. |
| `SwitcherActivity`, `KeyControlActivity`, `CommandBarActionActivity`, shortcut stubs | Task switcher (Shizuku), key control, shortcut trampolines. |

Navigation is an enum `DashTab` + `AnimatedContent` in `DashboardActivity`
(≈1000 lines). Five icon-only items in a floating translucent bar; every tab
pads 108dp so content scrolls behind it. Cross-tab jumps are callbacks
(`onOpenCalendar`, `navigateToTarget("school:…")`, notification extras,
`ACTION_SEND` share → Memories).

## 3. Screen inventory (what the user sees today)

- **Home** (`HomePage.kt`): month grid + selected-day agenda + AI briefing
  card + up to 5 insights + school summary + legacy swim widget + evening
  reflection CTA + weekly review card — one long scroll, ~6 section cards.
- **Calendar** (`CalendarPage.kt`): same month grid (duplicated), selected
  day, 30-day upcoming list, feed list + add-iCal dialog.
- **School** (`SchoolPage.kt` + alerts/automation cards in same scroll):
  auth card, GPA/movers/classes, per-class detail (missing/upcoming/graded),
  thresholds, background-sync prefs, automation rules/runs/channels.
- **Swim** (`SwimPlatformUi.kt`): profile, goals with bars, practices,
  results, training volume; import/link dialogs. Home still shows the
  legacy single-event `SwimCard` — two models coexist.
- **Memories** (`MemoriesUi.kt`): search + capture, 4 collection chips,
  library (capped at 20), trash, detail sheet, zoomable image viewer.
- **Ask surfaces**: `AiAnswerActivity` chat; overlay Ask mode
  (`GeminiConversation`); edge-bar search panel. None receive grounded
  app data (see §6).

## 4. Data layer

| Store | Contents | Format |
|---|---|---|
| `PowerSchoolStore` | session cookies (`files/powerschool/session.json`), cached courses, grade history (60/day rolling) | prefs + JSON, atomic tmp+rename |
| `ICalStore` | feed URLs, raw `.ics` bodies (8 MB cap) | prefs + files |
| `SwimPlatformStore` (+ legacy `SwimStore`) | profile, goals (≤50), practices (≤300), results (≤500) | prefs + JSON, one-way legacy migration |
| `MemoryStore` | records, collections, tags, search history; media as private files | prefs JSON (cap 2000) + `files/memories/` |
| `InsightEngine/InsightStore` | insights w/ stable IDs, ack state | prefs JSON |
| `SchoolAlerts` | thresholds, muted courses | prefs |
| `AutomationEngine` | rules + last 100 runs | prefs JSON |
| Misc | AI keys (Keystore-encrypted), chats, contacts, Todoist token (Keystore), logs | prefs |

PowerSchool: pure-Kotlin `PowerSchoolProtocol` (login form incl.
`pw/db-pw/ldap/translator` copies, home-table parse keyed by `fg` term
param, scores metadata `data-sectionid` + `studentFRN`, XTE
`/ws/xte/assignment/lookup`) + `PowerSchoolClient` (HttpURLConnection,
manual redirects, portal-only cookie jar, 3-way concurrency, one retry,
transparent re-login). Read-only; password never stored. Default host
`pschool.gcs.k12.in.us`, overridable.

iCal: `ICalParser` handles DATE/DATETIME, DTEND/DURATION, EXDATE, and
DAILY/WEEKLY RRULE with UNTIL/COUNT (monthly/yearly/RDATE/VTIMEZONE
ignored by design, disclosed in UI). Merge with PowerSchool due dates in
calendar UI.

Meet Mobile: allowlisted `awmobile.active.com/rest/v4/mmo/` competition
reads with SHA-256 `auth_key` digest, explicit identity confirmation,
idempotent import by result dedupe key, honest unavailable states.

Memory search: substring over title/body/note/summary/tags/places/actions
+ `houston-hash-128/v1` deterministic hash embeddings blended
0.65 cosine + 0.35 literal, threshold 0.42; candidate-ID validation
prevents invented references.

## 5. Background and notifications

No WorkManager. `AlarmManager.setAndAllowWhileIdle` (explicitly inexact):
07:30 morning briefing (deduped by day), chained 6-hour sync alarm with
0–3h failure backoff, `BootReceiver` rescheduling, `SyncReceiver`, manual
Sync-now. Foreground `dataSync` service (calendars → PowerSchool session
refresh → automations). Five notification channels
(briefing/school/swim/memory/automation), capped at 5 per batch, ack-aware,
POST_NOTIFICATIONS-gated. Insights carry stable IDs, scores, expiry, deep
links; school/missing/due-today score highest.

Default automations: morning briefing, grade-watch, due-soon, personal-best
notices. `requiresConfirm`/`gradeThreshold` are stored but not enforced in
`evaluate()` — actions today only ever produce notifications/insights, so
the gap is latent, not live.

## 6. AI layer

Providers (keys Keystore-encrypted, user-supplied): Gemini (auto-discovered
Flash-Lite, JSON tool loop), OpenRouter/OpenAI (native tools), Anthropic
(fallback JSON), on-device AICore (ML Kit, last-8-turns), local Gemma via
LiteRT (manual 6-round tool loop, 22 tools). `AssistantTools` adds
schema translators + `MAX_STEPS=5` + `NeedsConfirmation` for consequential
actions — but the legacy `AiApi.generate` loop pre-confirms everything, so
the overlay Ask path can run calls/messages/shares without a tap.

Briefing, insights, weekly review, and memory extraction are deterministic
(no LLM): fixed-order sections, scored insights, honest empty states. The
only live model is `LocalHeuristicProvider` (keyword tags, extractive
summaries). Chat prompts never include grades, assignments, swim, or memory
content — the assistant must invent or deflect personal questions.

`ActionRegistry` already tiers `READ_ONLY / REVERSIBLE / CONSEQUENTIAL`
with typed params — the right skeleton for agent tool permissions.

## 7. Privacy and security posture

Good: Keystore for AI/Todoist keys; PowerSchool password never stored;
cookies-only sessions; redacted logs (codes/durations, no bodies/tokens);
share-into-Memories stays app-private; backup excludes credentials.

Gaps to close: session cookies, grade/swim/memory JSON, and iCal URLs are
plaintext in app-private storage with `allowBackup:true`; `requiresConfirm`
unenforced; tool results are concatenated raw into prompts (injection
surface); safety prompt is user-editable with no floor; Markwon renders
model links without an allowlist; Gemini model auto-upgrades silently;
ChatGPT bridge response lacks round-trip binding.

Flag: repo-root `houston-release.keystore` + `keystore.properties` exist in
the working tree — release signing material must not ship or leak.

## 8. Tests

22 files, all offline JVM: intelligence (swim math, movers, dedup, briefing
order, embeddings, automation eval), PowerSchool protocol parsing, iCal
parsing, assistant tools gating + schema parity, action confirm matrix,
contacts, search/ranking, Todoist NL, updates, converters. No LLM, network,
screenshot, or migration tests.

## 9. Reusable foundations (keep)

`PowerSchoolProtocol/Client/Store`, `ICalStore/Parser`, `SwimPlatform`
model + Meet Mobile client, `MemoryStore` + hash embeddings + ID
validation, `InsightEngine` (stable IDs, dedup, expiry, ranking),
`SchoolAlerts` thresholds, `AutomationEngine` evaluate/history,
`ActionRegistry` tiers, `SettingsBackup` (extend, don't replace), the full
unit-test suite, release-notes discipline.

## 10. Top risks for the rebuild

1. Flat 143-file package — new code must introduce package structure
   without breaking existing imports all at once.
2. `OverlayController` god-file and `DashboardActivity` state tangle make
   UI refactors high-blast-radius; slice vertically, keep old paths working.
3. No Room — second-brain memory needs relational storage; add Room for
   *new* tables only, leave legacy stores untouched.
4. No WorkManager — add for deferrable work; keep inexact alarms where the
   brief allows (morning briefing).
5. Ungrounded chat + legacy auto-confirm are the two trust blockers; fix
   before any autonomy claims.
6. Keystore file in tree; plaintext app data + backup scope need a policy.

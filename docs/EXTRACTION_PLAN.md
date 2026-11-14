# Houston Extraction Plan — updated after Hub/Beeper removal + god-file splits

Source: 108 Kotlin files (was 99, -10 Hub/Beeper deleted, +19 split modules). Largest remaining:

- `OverlayController.kt` 3708 lines — still god-file, needs phase 2b split
- `CommandOverlayUi.kt` 326 lines facade — DONE
- `NativePanelNavigation.kt` 620 lines — moderate, acceptable for panel
- `OverlayTodoistUi.kt` 448, `OverlayContactUi.kt` 420, `OverlayFileUi.kt` 401 — DONE
- `AppSearchEngine.kt` 338, `ContactEngine.kt` 294, `SystemActionsDevice.kt` 275, `FileEngine.kt` 271 — DONE
- `NativeSearchActivity.kt` 127 lines shell, `CommandEngine.kt` 91 lines facade, `SystemActionEngine.kt` 27 lines dispatcher — DONE

## Phase 1 — done (safe, no behavior change)

- `AdaptiveLayout.kt`: shared compact-screen guardrails.
- `SettingsStore.kt`: central SharedPreferences entry point.
- `AccentSettings.kt`: migrated to `SettingsStore` as the reference.
- `TodoistNaturalLanguageTest.kt`, `UnitConverterTest.kt`: pure-JVM regression coverage.
- `gradle/libs.versions.toml`, `app/lint.xml`, `.editorconfig`, `proguard-rules.pro`: build hygiene.
- CI now runs `testDebugUnitTest` before `lintDebug assembleDebug`.

## Phase 2 — done (mechanical splits, keep call sites)

1. `CommandOverlayUi.kt` (4724 → 326 facade) → `OverlayTodoistUi.kt`, `OverlayFileUi.kt`, `OverlayContactUi.kt` (+`OverlayContactFilter`), `OverlayController.kt`. Verbatim moves, wrapper `CommandOverlayApp → OverlayControllerRoot`.
2. `NativeSearchActivity.kt` (1098 → 127 shell) → `NativeResultDispatcher.kt` (`performOpenResult`), `NativePanelNavigation.kt` (`activate`/`move`), `NativeRanking.kt` (+`dedupe`/`music` helpers), `NativeResultRowUi.kt`. +`NativeRankingTest` 8/8.
3. `CommandEngine.kt` (1035 → 80 facade) → `FileEngine.kt`, `ContactEngine.kt` (+`ContactNameLogic`), `AppSearchEngine.kt` (+`AppSearchShortcuts`), `TimerCalcEngine.kt` (+`TimerCalcLogic`). New engines hold `appContext` only (Activity leak fixed).
4. `SystemActionEngine.kt` (981 → 25 dispatcher) → `SystemActionsDevice/Apps/Comms/Productivity.kt` + `SystemActionRuntime.kt`.
5. Hub/Beeper deleted: `HubNotificationService`, `HubSettings`, `HubActivity`, `HubHistory*`, `HubFollowUpScheduler`, `HubKeyBridge`, `MinimalHubUi`, `BeeperProvider`, `BeeperConversationActivity`.

## Phase 2b — next (OverlayController still 3708 lines)

Each split: move composable/function verbatim, keep original as `typealias` or thin wrapper for one release, verify `./gradlew lintDebug testDebugUnitTest`.

## Phase 3 — DataStore

- Finish migrating remaining `*Settings` to `SettingsStore`.
- Swap `SettingsStore` internals to DataStore with SharedPreferences fallback. No call-site changes.

## Rules

- No behavior change in extraction PRs. One file per PR.
- Every extraction adds or moves at least one unit test for pure logic.
- Run `./gradlew testDebugUnitTest lintDebug` before push.

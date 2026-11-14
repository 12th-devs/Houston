# Houston — Product context

Minimal context for design tooling. Source of truth is `README.md`, `PRIVACY.md`, `docs/SETUP.md`.

## What it is

Keyboard-first Android command bar. Overlay opened from hardware key, launcher gesture, shortcut, or edge pill. Typing filters apps, contacts, files, settings, Todoist, Tasker, AI, Quick Controls.

## Who

Physical-keyboard phone users first (Unihertz Titan 2, Zinwa Q25 tested). Touch works, but shortest paths assume typing + Enter / 1–0 / arrows.

## Modes (Impeccable)

- Command bar, App Switcher, Key Control: **Operate**. Scanability, focus visibility, keyboard parity, small-screen safety beat expression.
- Settings, Setup dashboard: **Operate**. Live permission state, deep links, no dead ends.
- README / SETUP / release notes: **Read**.

## Constraints

- No ads, analytics, telemetry, crash SDKs (`PRIVACY.md`).
- Permissions are feature-gated; every sensitive capability needs an in-app explanation + fallback.
- 360dp width / 680dp height / 1.3x font scale must not clip (see `AdaptiveLayout.kt`).
- Focus must always be visible; Enter / arrows / 1–0 must work without touch.
- Content descriptions on interactive icons; no color-only status.

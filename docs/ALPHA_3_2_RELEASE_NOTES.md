# Houston 3.2.0-alpha.1 — Second-brain foundation

This alpha reorganizes Houston around four destinations — **Home, Plan,
Memory, Ask** — with a persistent memory store, a grounded agent runtime,
and a proactive background pass. Everything from 3.1 keeps working:
School, Swim, Calendar, and Captures remain one tap away as secondary
routes, and all local data migrates untouched.

## What's new

- **Home is now personal intelligence**: greeting, an AI briefing grounded
  in verified data, the single next thing, up to three ranked priorities,
  and a universal input. No more dashboard card dump.
- **Plan**: Today timeline (commitments vs. AI suggestions, visually
  distinct), Upcoming groups, and the full calendar. Study recommendations
  explain themselves and never schedule anything without your tap.
- **Memory**: facts, goals, experiences, and projects with explicit
  verification states (confirmed / imported / inference / superseded),
  natural-language recall with cited sources, one-tap confirm, edit, and
  delete — plus a review queue for confident inferences and a health pass
  that dedupes and expires.
- **Ask**: streaming answers with tool-step progress, approval cards for
  anything consequential, cancel, per-answer sources, and a usage readout
  so model costs stay visible.
- **Proactive, with an off switch**: a twice-daily background check for
  conflicts, overload, and due-tomorrow pile-ups. Quiet hours, notification
  levels (all / important / none), and a master toggle live next to the
  other background controls. Silence is one tap away.

## Trust and safety

- The legacy auto-confirm path is gone: no consequential action runs
  without your explicit approval in any entry point.
- Retrieved content (calendar, files, web, memories) is tagged as
  untrusted data inside prompts, truncated, and control-stripped.
- Ask only answers from your verified snapshot plus cited sources; guesses
  are labeled as inferences, never facts.
- PowerSchool stays read-only; passwords are never stored; session cookies
  never leave the device. New credentials use the Android Keystore or the
  OS-backed DataStore; nothing sensitive ships in logs or backups.

## Verification

- 186+ JVM unit tests green (memory, planner, permissions, injection
  fixtures, prompt wrapping, session expiry, dedup, scheduling math).
- `./gradlew testDebugUnitTest lintDebug assembleDebug` green in CI.
- Manual QA still recommended on-device: navigation back-stack, approval
  cards, notification channels, and the first-run migration of legacy
  stores (no data loss expected — legacy files are never modified).

## Known limitations (alpha)

- Memory full-text search uses an on-device FTS index with a
  substring-similarity fallback; very large libraries rank by recency.
- Model answers require a configured provider key; without one, Ask and
  briefings fall back to deterministic summaries of verified data.
- Background work follows Android deferral rules: exact timing is not
  promised, and battery saver may delay the proactive pass.

# Product Vision — Houston Second Brain

## One-line standard

Houston already knows what is happening in your life. The second brain
decides what matters right now, explains why, and — only with your
permission — acts on it. The best interface shows almost nothing; the
intelligence lives underneath.

## The four destinations

- **Home — Personal Intelligence.** A greeting, one AI-written briefing
  grounded in verified data (calendar, school, swim, memories, tasks),
  the single next thing, at most three ranked priorities, and a universal
  input. No month grid, no duplicated summaries, no card dump. Facts and
  inferences are visually distinct.
- **Plan — Intelligent Scheduling.** Today (timeline of commitments +
  suggested work blocks, visually distinct), Upcoming (grouped
  responsibilities), Calendar (secondary, for navigation). The planner
  detects conflicts, finds open blocks, estimates effort from history,
  recommends study sessions, and explains its reasoning. Suggestions never
  silently become commitments.
- **Memory — Persistent Knowledge.** Facts, goals, projects, journal
  captures, milestones, and the connections between them. Conversational
  capture ("remember I want to work on breaststroke turns"), natural-language
  search with cited sources, user editing/deletion, and explicit states:
  confirmed fact, imported fact, inference, superseded.
- **Ask — Universal Interface.** Streaming conversation with history,
  memory-aware answers, typed tool calls with previews, follow-up handling,
  source references, and cancel. Voice in, voice-friendly out.

## The agent, in one paragraph

Events, schedules, and requests flow into a context engine; the planner
decides whether action is warranted and drafts a bounded plan; typed tools
execute it; the memory engine records outcomes; the proactive engine
surfaces only what clears a relevance bar; the reflection engine checks
results and learns from corrections. Every run is budgeted (steps, time,
retries), cancellable, and logged as: what it did, why, which tools it
used, and what changed.

## Permission promise

- **Level 1** reads, analyzes, suggests, summarizes — freely.
- **Level 2** touches the outside world (calendar edits, synced tasks,
  rescheduling) — asks first; narrow standing permissions possible.
- **Level 3** (messages, purchases, deletions, external sharing, security)
  — explicit confirmation every time, no exceptions, no silent escalation.
  The legacy auto-confirm path is removed, not worked around.

## Proactive contract

Notify only when there is something to do, decide, or know — with what
changed, why it matters, and the action. Dedupe by stable ID, respect
quiet hours and per-category frequency caps, and make "stop telling me
about this" one tap. Daily and weekly briefings are the heartbeat; the
phone stays quiet otherwise.

## Design contract

Calm, minimal, native Material 3 Expressive. One primary surface per
destination; progressive disclosure everywhere; dark and light themes from
the dynamic palette (no parallel custom themes); labeled navigation;
consistent empty/loading/error states; TalkBack labels; edge-to-edge with
predictive back. Duplicate month grids, mirrored summaries, and the legacy
swim widget go away — the data stays, the clutter does not.

## Domain promises (nothing lost)

- **School:** grades, assignments, sync, and alerts keep working exactly as
  today; the agent reasons over them instead of just displaying them.
- **Swimming:** records, goals, practices, Meet Mobile import, and
  course-isolated analytics are preserved and connected to scheduling.
- **Calendar:** existing feeds keep syncing; due dates stay merged in.
- **Journaling:** conversational capture replaces forms; the runner-up is
  silence, not a nagging prompt.
- **Weekly reviews:** short, cited, expandable — never a wall of text.

## Non-goals

No unrestricted background execution (Android forbids it; the design
respects that). No credentials or cookies in prompts. No invented
achievements, grades, or events — every claim traces to a source. No
feature that only works as a mock.

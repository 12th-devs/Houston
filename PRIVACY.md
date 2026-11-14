# Privacy

Houston's own application code does not contain advertising, analytics, telemetry, or crash-reporting SDKs.

## Data stored on the device

Houston stores preferences, aliases, selected integrations, saved messaging people and routes, and optional API credentials in its private app storage. Gemini and Todoist credentials are encrypted using a key held by Android Keystore.

Uninstalling Houston removes its private app data. Android backup behaviour can depend on device settings and firmware.

## Data accessed by optional features

- **Accessibility:** receives physical key events for optional launcher home-screen typing and performs narrowly scoped automation for supported handoff features.
- **Contacts:** searches local contacts for calling and messaging commands.
- **SMS:** sends an SMS only after the user invokes the corresponding messaging workflow.
- **Files:** searches shared storage when full-file access is granted.

## External services

Houston makes network requests only when the user invokes a network-backed feature:

- Gemini prompts and conversation history are sent to Google’s Gemini API using the user’s API key.
- Direct Todoist tasks are sent to Todoist using the user’s personal token.
- Search aliases and web search open the selected app or service with the typed query.
- ChatGPT prompts are handed to the installed ChatGPT app when that command is used.

Those services process data under their own terms and privacy policies. Houston does not operate a server or receive a copy of these requests.

## Logs

Release builds do not intentionally log typed command text, API keys, or message bodies. Non-sensitive integration failures may be recorded in Android system logs for troubleshooting.

## Questions

For a privacy concern, open a GitHub issue without including private content. Security-sensitive reports should follow [SECURITY.md](SECURITY.md).

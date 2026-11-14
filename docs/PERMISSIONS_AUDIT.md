# Permissions audit — Houston alpha 6

Generated from `app/src/main/AndroidManifest.xml`. Keep this in sync when adding/removing permissions.

## Always-on (core)

| Permission | Why | User control |
|---|---|---|
| `INTERNET` | Todoist, Gemini/OpenRouter/OpenAI/Anthropic, web search hand-off | Only on use; keys user-supplied |
| `RECEIVE_BOOT_COMPLETED` | Restore edge handle after reboot | Edge handle toggle |
| `POST_NOTIFICATIONS` | Confirmations, timer/alarm feedback | System toggle |
| `SYSTEM_ALERT_WINDOW` | Edge pill + command bar overlay | Setup → Swipe to open Houston |

## Optional (feature-gated)

| Permission | Feature | Notes |
|---|---|---|
| Accessibility (`BIND_ACCESSIBILITY_SERVICE`) | Home typing, Key Control, scoped hand-off (Gmail/1Password) | Optional; narrow actions only |
| `READ_CONTACTS` | Call/message contact search | Requested with context |
| `SEND_SMS` | SMS send after explicit invoke | Per-message user action |
| `READ_EXTERNAL_STORAGE` (≤32) / `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` (14+) | Screenshot browsing | Photo picker where possible |
| `MANAGE_EXTERNAL_STORAGE` | Full file search | Optional; explain before request |
| `PACKAGE_USAGE_STATS` | App ranking by foreground time | Optional; fallback to launch history |
| `RECORD_AUDIO` | Voice input | Only while listening |
| `CAMERA` | Flashlight Quick Control, QR scan | Per-action |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` | Bluetooth Quick Controls | Per-action |
| `WRITE_SETTINGS` | Brightness/volume Quick Controls | Direct to system settings on denial |
| `SET_ALARM` | Timer/alarm creation | System confirms |
| Shizuku (`INTERACT_ACROSS_USERS_FULL`, provider) | System task switching/snapshots | Explicit Shizuku approval |

## Watch-outs

- Play Protect warns on sideloaded builds with Accessibility/SMS. Do not disable Play Protect globally; install only trusted builds. Long-term: Play release + Data Safety form.
- `WRITE_SETTINGS` + `MANAGE_EXTERNAL_STORAGE` are high-friction; keep the in-bar fallback (open settings screen) when denied.
- Never add `READ_SMS`, `CALL_LOG`, background location, or ad/crash SDKs without a full privacy review.

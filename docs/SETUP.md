# Houston setup

## Back up or migrate settings

Open **Houston Settings**, then use **Export settings** to save a versioned Houston JSON backup with Android's document picker. On another installation, choose **Import settings** and select that file.

The backup contains preferences, aliases, app mappings, Tasker mappings, appearance, sounds, custom icons, and feature toggles. It intentionally excludes Gemini and Todoist credentials and drafts. Enter API credentials again after importing.

Houston is modular. Enable only the access required by the features you use.

## 1. Install

Download the APK from GitHub Releases and install it. Android may ask you to allow installations from your browser or file manager.

### Play Protect warning

Play Protect may describe Houston as unknown or block installation because the alpha is distributed directly through GitHub rather than Google Play. Houston also provides optional features that use sensitive Android access, including Accessibility, contacts, SMS, and files.

This warning should not be bypassed blindly. Download the APK only from the official Houston Releases page and compare it with the SHA-256 checksum attached to that release. Do not disable Play Protect globally. If Android does not provide an informed install-anyway option, wait for a verified distribution route or build the app from the public source.

Updates must be signed with the same key as the installed build. If Android reports an incompatible signature, uninstall the previous development build before installing the public alpha. Uninstalling clears Houston settings.

## 2. Open Houston Bar

Houston Bar can be launched from:

- its Android shortcut;
- a launcher gesture;
- a hardware-key mapping app such as Key Mapper;
- optional typing from the launcher home screen.

The normal Android launcher entry opens Houston Settings. Houston Home is experimental and hidden in the alpha build.

## 3. Home-screen typing

1. Open **Houston Settings**.
2. Enable **Type from home screen**.
3. Tap **Open Command app info**.
4. If Android shows the option, open the three-dot menu and choose **Allow restricted settings**.
5. Return to Houston Settings and tap **Open Accessibility settings**.
6. Enable **Houston home typing**.

This service listens for physical key events while a launcher home screen is active. It does not enable Houston over every app; use the normal Houston Bar shortcut outside the launcher.

## 4. Houston Contacts and messaging

Grant Contacts permission to search names after `@` or `#`. SMS sending requires the SMS permission and confirmation flow provided by Houston.

Open **Houston Contacts** from the top of Houston Settings to organise messaging destinations:

1. Open the **Unassigned** page and create a Houston person or assign the route to an existing person.
2. Add an SMS contact or additional app routes to that person as needed.
3. Select **Make default** (★) beside the route that should appear first and be preferred.

Type `@` followed by a person's Houston name to see their assigned routes. Type an app scope such as `@Messenger ` or `@WhatsApp ` to search only that app's saved routes.

Some messaging apps open an app-provided recipient screen for final confirmation. Always verify the displayed person and app before sending.

## 5. Files and folders

File search requires Android’s full-file access on supported versions. The default trigger is `Space` and can be changed in Settings.

- `f ` filters to files with the default configuration.
- `fo ` filters to folders with the default configuration.
- Tap the sort label to switch between relevance and modification-time order.

Opening a folder is delegated to an installed file manager. Solid Explorer is the primary tested file manager.

## 6. Todoist

Houston can either open Todoist Quick Add or send a task directly through Todoist’s API.

For direct add:

1. Obtain a personal API token from Todoist.
2. Enter it in Houston Settings.
3. Enable direct Todoist add.

The token is encrypted with Android Keystore. Natural-language recognition is an indicator before submission; Todoist remains responsible for final parsing.

Search for **Tasks** or type `tasks ` to open the task interface. Use Left and Right to switch between Tasks, Upcoming, and Calendar. Up and Down select tasks, Enter completes the selection, and Shift+Enter opens it in Todoist. The list refreshes when opened so changes made in Todoist or on another device can appear.

## 7. Gemini and ChatGPT

- `?` uses the Gemini API inside Houston and requires your own Google AI Studio key.
- `??` hands the prompt to the installed ChatGPT app.

The Gemini key is encrypted with Android Keystore. Queries sent to Gemini are governed by Google’s service terms and privacy policy. ChatGPT handoff behaviour can vary between app versions.

## 8. Search aliases and Tasker

Settings can assign one or more aliases to supported search targets. Enter the alias, a space, then the query. Tasker aliases use Tasker’s task-selection and execution interfaces and require Tasker to be installed and configured.

## 9. Quick Controls

Search for **Quick Controls** in Houston Bar or use its configured alias. Type to filter controls, then press Enter to activate the selected item. Controls that need a value prepare the bar for input. Examples include a brightness percentage or `auto`, a volume percentage, and a ringer mode.

Settings can also mix matching Quick Controls into normal app search. Support varies by Android version and device firmware. Privileged and manufacturer-specific controls may require Shizuku or may remain unavailable on another device.

## 10. App Switcher

Open **App Switcher** from Houston search, its Android shortcut, or a configured Houston hardware shortcut. Left and Right move between recent apps, Enter opens the selected card, typing filters by app name, and swiping a card upward closes that task where Android permits it.

Hold-to-switch mode works like an Alt-Tab shortcut: hold the chosen modifier, press the forward or reverse key to select an app, then release the modifier to open it. Sticky mode keeps App Switcher open for filtering and normal navigation. Faster task previews and some device controls require Shizuku.

## 11. Key Control

Key Control overlays compact letter hints on accessibility actions exposed by the current screen. Type a visible hint to activate it. Press Shift to search exposed control labels, then use Up and Down to select a match and Enter to activate it. Space and the configured reverse combination scroll supported accessibility containers.

Key Control depends on the target app exposing meaningful accessibility nodes and actions. Web pages, custom canvases, games, and some app screens may expose incomplete or stale information.

## 12. Keyboard navigation

Houston Bar supports arrow-key selection and Enter to activate the selected result. Backspace on an empty field moves back one command stage or closes the overlay.

## Troubleshooting

When reporting a problem, include:

- device and Android version;
- launcher and keyboard app;
- whether a physical or on-screen keyboard was used;
- the relevant Houston setting;
- exact steps to reproduce;
- a screen recording with private content removed, when practical.

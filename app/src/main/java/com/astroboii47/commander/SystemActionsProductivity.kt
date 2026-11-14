package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SystemActionsProductivity {
    fun productivity(): List<SystemAction> = listOf(
        SystemAction(
            id = "timer.create", name = "Create timer",
            description = "Start a timer for a duration.",
            category = ActionCategory.READ_ONLY,
            params = listOf(
                ParamSpec("seconds", ParamType.INT, required = true, min = 1.0, max = 86400.0),
                ParamSpec("label", ParamType.TEXT, description = "Timer label"),
            ),
            execute = { ctx, args ->
                val seconds = args["seconds"]?.toIntOrNull() ?: return@SystemAction ActionResult.fail("Provide seconds")
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, args["label"]?.takeIf(String::isNotBlank) ?: "Houston timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                if (intent.resolveActivity(ctx.packageManager) == null) ActionResult.fail("No timer app is installed")
                else if (launchIntent(ctx, intent)) ActionResult.ok("Timer started")
                else ActionResult.fail("Timer could not be started")
            },
        ),
        SystemAction(
            id = "alarm.create", name = "Create alarm",
            description = "Set an alarm for a time of day.",
            category = ActionCategory.READ_ONLY,
            params = listOf(
                ParamSpec("hour", ParamType.INT, required = true, min = 0.0, max = 23.0),
                ParamSpec("minute", ParamType.INT, required = true, min = 0.0, max = 59.0),
            ),
            execute = { ctx, args ->
                val hour = args["hour"]?.toIntOrNull() ?: return@SystemAction ActionResult.fail("Provide hour")
                val minute = args["minute"]?.toIntOrNull() ?: return@SystemAction ActionResult.fail("Provide minute")
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                if (intent.resolveActivity(ctx.packageManager) == null) ActionResult.fail("No alarm app is installed")
                else if (launchIntent(ctx, intent)) ActionResult.ok("Alarm set for %02d:%02d".format(hour, minute))
                else ActionResult.fail("Alarm could not be set")
            },
        ),
        SystemAction(
            id = "event.create", name = "Create calendar event",
            description = "Open the calendar app to create an event.",
            category = ActionCategory.CONSEQUENTIAL,
            params = listOf(ParamSpec("title", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, args["title"].orEmpty())
                }
                if (launchIntent(ctx, intent)) ActionResult.ok("Calendar event draft opened")
                else ActionResult.fail("No calendar app is installed")
            },
        ),
        SystemAction(
            id = "tasks.todo.add", name = "Add Todoist task",
            description = "Add a task to Todoist.",
            category = ActionCategory.CONSEQUENTIAL,
            params = listOf(ParamSpec("text", ParamType.TEXT, required = true, maxLength = 500)),
            execute = { ctx, args ->
                val error = withContext(Dispatchers.IO) {
                    TodoistApi.quickAdd(ctx, TodoistNaturalLanguage.expandCompactTimes(args["text"].orEmpty()))
                }
                if (error == null) ActionResult.ok("Task added to Todoist")
                else ActionResult.fail(error)
            },
        ),
        SystemAction(
            id = "tasks.todo.list", name = "List Todoist tasks",
            description = "List current Todoist tasks.",
            category = ActionCategory.READ_ONLY,
            execute = { ctx, _ ->
                val tasks = withContext(Dispatchers.IO) { TodoistTasks.load(ctx) }.getOrNull().orEmpty()
                if (tasks.isEmpty()) ActionResult.ok("No Todoist tasks")
                else ActionResult.ok(tasks.take(10).joinToString("\n") { it.content })
            },
        ),
        SystemAction(
            id = "tasks.todo.complete", name = "Complete Todoist task",
            description = "Complete a Todoist task by id or matching text.",
            category = ActionCategory.CONSEQUENTIAL,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                val query = args["query"].orEmpty()
                val tasks = withContext(Dispatchers.IO) { TodoistTasks.load(ctx) }.getOrNull().orEmpty()
                val task = tasks.firstOrNull { it.id == query }
                    ?: tasks.firstOrNull { it.content.contains(query, true) }
                    ?: return@SystemAction ActionResult.fail("No matching task")
                val error = withContext(Dispatchers.IO) { TodoistTasks.complete(ctx, task.id) }
                if (error == null) ActionResult.ok("Completed: ${task.content}")
                else ActionResult.fail(error)
            },
        ),
        SystemAction(
            id = "files.search", name = "Search files",
            description = "Search device files by name.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            aliases = setOf("search_files"),
            execute = { ctx, args ->
                val query = args["query"].orEmpty()
                val results = queryFiles(ctx, query, 10)
                if (results.isEmpty()) ActionResult.ok("No matching files found or file access is unavailable")
                else ActionResult.ok(results.joinToString("\n"))
            },
        ),
        SystemAction(
            id = "calc.evaluate", name = "Calculate",
            description = "Evaluate an arithmetic expression or convert units.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("expression", ParamType.TEXT, required = true, maxLength = 200)),
            execute = { _, args ->
                val expression = args["expression"].orEmpty()
                val converted = UnitConverter.convert(expression)
                if (converted != null) return@SystemAction ActionResult.ok("$expression = $converted")
                val value = CalcParser.evaluate(expression)
                    ?: return@SystemAction ActionResult.fail("Invalid calculation")
                ActionResult.ok("$expression = $value")
            },
        ),
        SystemAction(
            id = "tasker.run", name = "Run Tasker task",
            description = "Run a configured Tasker task by name.",
            category = ActionCategory.REVERSIBLE,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                val task = TaskerAliases.load(ctx).firstOrNull { it.taskName.equals(args["query"], true) }
                    ?: TaskerAliases.load(ctx).firstOrNull { it.label.contains(args["query"].orEmpty(), true) }
                    ?: return@SystemAction ActionResult.fail("No matching Tasker task")
                val error = TaskerAliases.run(ctx, task)
                if (error == null) ActionResult.ok("Task started: ${task.label}")
                else ActionResult.fail(error)
            },
        ),
        SystemAction(
            id = "assistant.date", name = "Current date",
            description = "Get the current local date and time.",
            category = ActionCategory.READ_ONLY,
            aliases = setOf("date"),
            execute = { _, _ ->
                ActionResult.ok(
                    java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.FULL, java.text.DateFormat.SHORT,
                    ).format(java.util.Date()),
                )
            },
        ),
        SystemAction(
            id = "assistant.weather", name = "Weather",
            description = "Get current weather for a place.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("query", ParamType.TEXT, description = "City or place")),
            aliases = setOf("weather"),
            execute = { _, args ->
                ActionResult.ok(AiTools.weather(args["query"].orEmpty().ifBlank { "current location" }))
            },
        ),
    )

    internal fun queryFiles(ctx: Context, query: String, limit: Int): List<String> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val collection = MediaStore.Files.getContentUri("external")
            ctx.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.RELATIVE_PATH),
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        add("${cursor.getString(nameColumn).orEmpty()} · ${cursor.getString(pathColumn).orEmpty().trimEnd('/')}")
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}

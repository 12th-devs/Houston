package com.astroboii47.commander

import android.app.Activity
import android.app.ActivityOptions
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Size
import java.io.File

/**
 * File/folder search, preview, sort and file actions.
 * Holds only applicationContext to avoid leaking an Activity.
 * Methods that launch UI take an Activity explicitly.
 */
class FileEngine(private val appContext: Context) {

    fun searchFiles(
        query: String,
        limit: Int = 7,
        mode: FileSearchMode = FileSearchMode.Default,
        sortMode: FileSortMode = FileSortMode.Relevance,
    ): List<FileResult> {
        if (query.isBlank()) return emptyList()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val fileResults = if (mode == FileSearchMode.FoldersOnly) emptyList() else runCatching {
            appContext.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} ${if (sortMode == FileSortMode.Oldest) "ASC" else "DESC"}",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val id = cursor.getLong(idColumn)
                        val mimeType = cursor.getString(mimeColumn)
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                            mimeType == "resource/folder"
                        ) continue
                        add(
                            FileResult(
                                name = cursor.getString(nameColumn).orEmpty(),
                                uri = ContentUris.withAppendedId(collection, id),
                                mimeType = mimeType,
                                location = cursor.getString(pathColumn).orEmpty().trimEnd('/'),
                                modifiedAtMillis = cursor.getLong(modifiedColumn) * 1000L,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        val includeFolders = mode == FileSearchMode.FoldersOnly ||
            (mode == FileSearchMode.Default && FileSearchSettings.showFolders.value)
        if (!includeFolders) return fileResults
        val combined = searchFolders(query, limit) + fileResults
        return when (sortMode) {
            FileSortMode.Relevance -> combined.sortedWith(compareBy<FileResult>({ !it.name.startsWith(query, ignoreCase = true) }, { it.name.length }))
            FileSortMode.Newest -> combined.sortedByDescending { it.modifiedAtMillis }
            FileSortMode.Oldest -> combined.sortedBy { it.modifiedAtMillis }
        }.take(limit)
    }

    private fun searchFolders(query: String, limit: Int): List<FileResult> {
        val folders = FolderIndex.get(appContext)
        return folders.asSequence()
            .filter { it.path.substringAfterLast('/').contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ !it.path.substringAfterLast('/').startsWith(query, ignoreCase = true) }, { it.path.length }))
            .take(limit)
            .map { entry ->
                val path = entry.path
                val cleanPath = path.trim('/')
                FileResult(
                    name = cleanPath.substringAfterLast('/'),
                    uri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:$cleanPath",
                    ),
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    location = cleanPath.substringBeforeLast('/', "Internal storage"),
                    isDirectory = true,
                    modifiedAtMillis = entry.modifiedAtMillis,
                )
            }
            .toList()
    }

    fun hasFileSearchAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun openFileSearchAccess(activity: Activity): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        return startOrFallback(activity, intent, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), "File access settings are unavailable")
    }

    fun openFile(activity: Activity, file: FileResult): String? {
        if (file.isDirectory) return openFolder(activity, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startOrFallback(activity, intent, null, "No app can open this file")
    }

    fun openFileWith(activity: Activity, file: FileResult): String? {
        if (file.isDirectory) return openFolder(activity, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startOrFallback(activity, Intent.createChooser(view, "Open ${file.name} with"), null, "No app can open this file")
    }

    fun revealFile(activity: Activity, file: FileResult): String? {
        val path = file.location.trim('/')
        if (path.isBlank()) return "Containing folder is unavailable"
        val folder = FileResult(
            name = path.substringAfterLast('/'),
            uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$path"),
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            location = path.substringBeforeLast('/', "Internal storage"),
            isDirectory = true,
        )
        return openFolder(activity, folder)
    }

    fun fileInfo(file: FileResult): String {
        var size: Long? = null
        if (!file.isDirectory) runCatching {
            appContext.contentResolver.query(file.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) size = cursor.getLong(0)
            }
        }
        val details = buildList {
            add(if (file.isDirectory) "Folder" else file.mimeType ?: "File")
            size?.let { add(formatFileSize(it)) }
            if (file.location.isNotBlank()) add(file.location)
            if (file.modifiedAtMillis > 0L) add(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(file.modifiedAtMillis)))
        }
        return details.joinToString("\n")
    }

    fun filePreview(file: FileResult): android.graphics.Bitmap? {
        if (file.isDirectory || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { appContext.contentResolver.loadThumbnail(file.uri, Size(720, 480), null) }.getOrNull()
    }

    fun deleteFile(file: FileResult): String? {
        if (file.isDirectory) return "Folder deletion is not supported"
        return runCatching {
            if (appContext.contentResolver.delete(file.uri, null, null) > 0) null
            else "File could not be deleted"
        }.getOrElse { "File could not be deleted" }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    private fun openFolder(activity: Activity, folder: FileResult): String? {
        val relativePath = if (folder.location == "Internal storage") folder.name
        else "${folder.location.trimEnd('/')}/${folder.name}"
        val solidIntent = Intent(Intent.ACTION_VIEW).apply {
            setClassName("pl.solidexplorer2", "pl.solidexplorer.SolidExplorer")
            setDataAndType(Uri.fromFile(File(Environment.getExternalStorageDirectory(), relativePath)), "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (solidIntent.resolveActivity(appContext.packageManager) != null) {
            val opened = runCatching {
                StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                startActivityAndFinish(activity, solidIntent)
            }.isSuccess
            if (opened) return null
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folder.uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val packageCandidates = listOf("com.google.android.documentsui")
        for (packageName in packageCandidates) {
            val targeted = Intent(view).setPackage(packageName)
            if (targeted.resolveActivity(appContext.packageManager) != null) {
                return runCatching { startActivityAndFinish(activity, targeted); null }
                    .getOrElse { "Folder could not be opened" }
            }
        }
        if (view.resolveActivity(appContext.packageManager) != null) {
            return runCatching { startActivityAndFinish(activity, view); null }
                .getOrElse { "Folder could not be opened" }
        }
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startOrFallback(activity, picker, null, "No file manager can open this folder")
    }

    private data class FolderEntry(val path: String, val modifiedAtMillis: Long)

    private object FolderIndex {
        @Volatile private var cached: List<FolderEntry>? = null

        fun get(context: Context): List<FolderEntry> = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }

        private fun load(context: Context): List<FolderEntry> {
            val paths = linkedMapOf<String, Long>()
            val collection = MediaStore.Files.getContentUri("external")
            runCatching {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH, MediaStore.Files.FileColumns.DATE_MODIFIED),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val column = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val relative = cursor.getString(column).orEmpty().trim('/')
                        if (relative.isBlank()) continue
                        val segments = relative.split('/')
                        val modified = cursor.getLong(modifiedColumn) * 1000L
                        for (end in 1..segments.size) {
                            val path = segments.take(end).joinToString("/")
                            paths[path] = maxOf(paths[path] ?: 0L, modified)
                        }
                    }
                }
            }
            return paths.map { FolderEntry(it.key, it.value) }
        }
    }

    fun loadFilePreviews(files: List<FileResult>): List<FileResult> = files.map { file ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (file.mimeType?.startsWith("image/") == true || file.mimeType?.startsWith("video/") == true)
        ) {
            file.copy(thumbnail = runCatching { appContext.contentResolver.loadThumbnail(file.uri, Size(96, 96), null) }.getOrNull())
        } else file
    }

    private fun startOrFallback(activity: Activity, primary: Intent, fallback: Intent?, unavailable: String): String? {
        val pm = appContext.packageManager
        val chosen = if (primary.resolveActivity(pm) != null) primary else fallback
        if (chosen == null || chosen.resolveActivity(pm) == null) return unavailable
        return runCatching {
            startActivityAndFinish(activity, chosen)
            null
        }.getOrElse { error ->
            if (error is SecurityException) "Permission denied by the target app" else unavailable
        }
    }

    private fun startActivityAndFinish(activity: Activity, intent: Intent) {
        val launchOptions = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        activity.startActivity(intent, launchOptions)
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}

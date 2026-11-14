package com.astroboii47.commander

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.graphics.Bitmap
import android.content.ContentUris
import android.provider.DocumentsContract
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenshotEntry(val name: String, val uri: Uri)

object CommandBarDeviceActions {
    fun imagePermissionGranted(context: Context): Boolean {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun imagePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    suspend fun screenshots(context: Context): List<ScreenshotEntry> = withContext(Dispatchers.IO) {
        if (!imagePermissionGranted(context)) return@withContext emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        runCatching {
            context.contentResolver.query(collection, columns, selection, arrayOf("%Screenshots%", "%Screenshot%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext() && size < 40) {
                        val id = cursor.getLong(idColumn)
                        add(ScreenshotEntry(cursor.getString(nameColumn) ?: "Screenshot",
                            android.content.ContentUris.withAppendedId(collection, id)))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun downloads(context: Context, query: String = "", limit: Int = 60): List<FileResult> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val columns = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DATE_MODIFIED)
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?" +
            if (query.isBlank()) "" else " AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = if (query.isBlank()) arrayOf("Download/%") else arrayOf("Download/%", "%$query%")
        runCatching { context.contentResolver.query(collection, columns, selection, args,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
            buildList {
                val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val path = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val modified = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (cursor.moveToNext() && size < limit) add(FileResult(
                    cursor.getString(name).orEmpty(), ContentUris.withAppendedId(collection, cursor.getLong(id)),
                    cursor.getString(mime), cursor.getString(path).orEmpty().trimEnd('/'),
                    modifiedAtMillis = cursor.getLong(modified) * 1000L))
            }
        }.orEmpty() }.getOrDefault(emptyList())
    }

    // Generic media-session lookup with no notification-listener dependency.
    // getActiveSessions(null) is guarded: SecurityException or empty list
    // falls back to null and media-key dispatch in mediaAction().
    private fun controller(context: Context): MediaController? = runCatching {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return@runCatching null
        val sessions = try {
            manager.getActiveSessions(null)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    }.getOrNull()

    data class Player(val title: String, val artist: String, val packageName: String, val playing: Boolean, val artwork: Bitmap?)

    fun player(context: Context): Player? = controller(context)?.let { session ->
        val metadata = session.metadata
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val art = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
        Player(title, artist, session.packageName, session.playbackState?.state == PlaybackState.STATE_PLAYING, art)
    }

    fun openPlayer(context: Context): Boolean {
        val packageName = controller(context)?.packageName ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun nowPlaying(context: Context): String? = controller(context)?.metadata?.let { metadata ->
        val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
        listOfNotNull(title, artist).filter(String::isNotBlank).joinToString(" · ").ifBlank { null }
    }

    fun mediaAction(context: Context, action: String) {
        val session = controller(context)
        if (session != null) {
            when (action) {
                "previous" -> session.transportControls.skipToPrevious()
                "next" -> session.transportControls.skipToNext()
                else -> if (session.playbackState?.state == PlaybackState.STATE_PLAYING) session.transportControls.pause()
                    else session.transportControls.play()
            }
            return
        }
        val code = when (action) {
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

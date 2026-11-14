package com.astroboii47.commander

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * App-private media attachments for Memories (and practice voice notes).
 * Bytes live in internal storage; the database holds only file names.
 * No media permissions needed for app-private capture; the system picker
 * supplies read access for shared/imported images.
 */
object MemoryMedia {
    fun imageFile(context: Context, name: String): File =
        File(MemoryStore.mediaDir(context), "img_$name.jpg")

    fun voiceFile(context: Context, name: String): File =
        File(MemoryStore.mediaDir(context), "voice_$name.m4a")

    /** Copies a picked/shared image into app-private storage. Returns the stored name. */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val name = "${UUID.randomUUID()}.jpg"
        context.contentResolver.openInputStream(uri)?.use { input ->
            imageFile(context, name).outputStream().use { input.copyTo(it) }
        } ?: return null
        name
    }.getOrNull()

    fun resolve(context: Context, storedName: String): File? = resolveAny(context, storedName)

    fun resolveAny(context: Context, ref: String): File? {
        if (ref.contains("..")) return null
        if (ref.contains('/') || ref.contains(':')) {
            // Content URI reference (best effort; not guaranteed across reboots).
            return runCatching { File(Uri.parse(ref).path ?: return null) }.getOrNull()?.takeIf { it.exists() }
        }
        val dir = MemoryStore.mediaDir(context)
        val base = ref.removeSuffix(".jpg").removeSuffix(".m4a")
        val candidates = linkedSetOf(ref, "img_$ref", "voice_$ref", "$base.jpg", "$base.m4a", "img_$base.jpg", "voice_$base.m4a")
        for (c in candidates) {
            if (c.contains("..")) continue
            val f = File(dir, c)
            if (f.exists()) return f
        }
        return null
    }
}

/** Simple start/stop voice recorder backed by MediaRecorder. One at a time. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outName: String? = null

    val recording: Boolean get() = recorder != null
    val outputName: String? get() = outName

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (recorder != null) return false
        val name = "${UUID.randomUUID()}.m4a"
        val rec = (if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(MemoryMedia.voiceFile(context, name).absolutePath)
        }
        return runCatching {
            rec.prepare()
            rec.start()
            recorder = rec
            outName = name
            true
        }.getOrElse {
            runCatching { rec.release() }
            false
        }
    }

    fun stop(): String? {
        val rec = recorder ?: return null
        recorder = null
        return runCatching {
            rec.stop()
            rec.release()
            outName
        }.getOrElse {
            runCatching { rec.release() }
            null
        }
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
        outName?.let { File(MemoryStore.mediaDir(context), "voice_$it").delete() }
        outName = null
    }
}

/** One-shot voice playback. Callers should stop() when leaving the screen. */
class VoicePlayer {
    private var player: MediaPlayer? = null
    var playingFile: String? = null
        private set

    fun toggle(file: File): Boolean {
        if (player != null && playingFile == file.absolutePath) {
            stop()
            return false
        }
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { stop() }
            }
        }.getOrNull() ?: return false
        playingFile = file.absolutePath
        return true
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingFile = null
    }
}

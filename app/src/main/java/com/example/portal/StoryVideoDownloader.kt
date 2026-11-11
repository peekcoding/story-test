package com.example.portal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.portal.model.StoryVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads the story videos uploaded in the portal into the model folders (D38).
 *
 * For each video the portal lists for this phone: when the folder already holds the same file (same name and
 * checksum) it is only reported; otherwise the file is downloaded to the app's cache (resuming a part already
 * downloaded), checked against the portal's size and SHA-256, then saved as `Movies/PageShare/<CODE>/<date>.mp4`
 * and reported. Files this app downloaded are remembered so they can be replaced when the portal's video
 * changes, and deleted once their day is before the portal's `keep_from`. Files copied by hand are never deleted.
 */
class StoryVideoDownloader(
    private val context: Context,
    private val repository: PortalRepository,
    /** True while the phone is posting: downloads stop and continue later. */
    private val isBusy: () -> Boolean,
) {

    private val prefs by lazy { context.getSharedPreferences("story_video_downloads", Context.MODE_PRIVATE) }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private class Paused : Exception("Paused while posting")

    /** Download what is missing and delete old downloads. Returns how many videos were saved. */
    suspend fun syncOnce(): Int = withContext(Dispatchers.IO) {
        val list = repository.getStoryVideos() ?: return@withContext 0
        var saved = 0

        for (video in list.videos) {
            if (isBusy()) break
            val known = remembered(video.uuid)
            // Already reported and still in the folder: nothing to do (cheap check, no checksum).
            if (video.downloaded && stillThere(video)) continue

            try {
                val existing = findInFolder(video)
                if (existing != null) {
                    repository.reportStoryVideoDownloaded(video.uuid, video.sha256.lowercase(), null)
                    continue
                }
                if (known != null) {
                    // The portal's video changed: remove the old download before saving the new one.
                    deleteUri(Uri.parse(known.optString("uri")))
                    forget(video.uuid)
                }
                log("Downloading ${video.folder}${video.fileName} (${video.sizeBytes / 1024} KB)")
                val file = download(video)
                val uri = saveToFolder(video, file)
                file.delete()
                remember(video, uri)
                repository.reportStoryVideoDownloaded(video.uuid, video.sha256.lowercase(), null)
                log("Saved ${video.folder}${video.fileName}")
                saved++
            } catch (e: Paused) {
                log("Story video download paused while posting")
                break
            } catch (e: Exception) {
                log("Story video ${video.fileName} for ${video.modelCode} failed: ${e.message}")
                repository.reportStoryVideoDownloaded(video.uuid, null, e.message ?: "Download failed")
            }
        }

        list.keepFrom?.let { deleteOlderThan(it) }
        saved
    }

    /** The file in the app's cache, complete and checked. A partial file from an earlier try is resumed. */
    private fun download(video: StoryVideoItem): File {
        val dir = File(context.cacheDir, "story-videos").apply { mkdirs() }
        val part = File(dir, "${video.uuid}.part")
        if (part.length() > video.sizeBytes) part.delete()

        if (part.length() < video.sizeBytes) {
            val token = repository.getSavedToken() ?: throw IllegalStateException("This phone is not registered")
            val request = Request.Builder()
                .url(video.downloadUrl)
                .header("Authorization", "Bearer $token")
                .apply { if (part.length() > 0) header("Range", "bytes=${part.length()}-") }
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Portal answered ${response.code}")
                // 200 means the server sent the whole file again: start the part over.
                val append = response.code == 206
                val body = response.body ?: throw IllegalStateException("Empty download")
                FileOutputStream(part, append).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var sinceCheck = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            sinceCheck += read
                            if (sinceCheck >= 1024 * 1024) {
                                sinceCheck = 0
                                if (isBusy()) throw Paused()
                            }
                        }
                    }
                }
            }
        }

        if (part.length() != video.sizeBytes) {
            throw IllegalStateException("Downloaded ${part.length()} of ${video.sizeBytes} bytes")
        }
        val sha = part.inputStream().use { ModelVideoScanner.computeSha256(it) }
        if (!sha.equals(video.sha256, ignoreCase = true)) {
            part.delete()
            throw IllegalStateException("The downloaded file was damaged (checksum differs), it will be downloaded again")
        }
        return part
    }

    /** Copy the checked file into Movies/PageShare/<CODE>/ under the portal's file name. */
    private fun saveToFolder(video: StoryVideoItem, file: File): Uri {
        val mime = mimeOf(video.fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, video.fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
                put(MediaStore.Video.Media.RELATIVE_PATH, video.folder.trimEnd('/') + "/")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values) ?: throw IllegalStateException("Could not create the video file")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("Could not write the video file")
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                deleteUri(uri)
                throw e
            }
            return uri
        }

        // Android 9: write the file directly and let the media scanner add it.
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStorageDirectory(), video.folder.trimEnd('/')).apply { mkdirs() }
        val target = File(dir, video.fileName)
        file.copyTo(target, overwrite = true)
        android.media.MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(target)
    }

    /** Whether a file with the portal's name and size is in the model folder. */
    private fun stillThere(video: StoryVideoItem): Boolean = queryFolder(video) { true } ?: false

    /** A file with the portal's name in the model folder whose checksum matches (e.g. copied by hand). */
    private fun findInFolder(video: StoryVideoItem): Uri? = queryFolder(video) { uri ->
        val sha = try {
            context.contentResolver.openInputStream(uri)?.use { ModelVideoScanner.computeSha256(it) }
        } catch (e: Exception) {
            null
        }
        if (sha.equals(video.sha256, ignoreCase = true)) uri else null
    }

    /** Runs [check] on each file with the portal's name and size in the model folder; the first non-null answer. */
    private fun <T> queryFolder(video: StoryVideoItem, check: (Uri) -> T?): T? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val folder = video.folder.trimEnd('/') + "/"
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.SIZE} = ?" to
                arrayOf(video.fileName, folder, video.sizeBytes.toString())
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Video.Media.DATA} LIKE ? AND ${MediaStore.Video.Media.SIZE} = ?" to
                arrayOf("%/$folder${video.fileName}", video.sizeBytes.toString())
        }
        context.contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID), selection, args, null)?.use { c ->
            while (c.moveToNext()) {
                check(ContentUris.withAppendedId(collection, c.getLong(0)))?.let { return it }
            }
        }
        return null
    }

    /** Delete the videos this app downloaded whose day is before [keepFrom] (yyyy-MM-dd). */
    private fun deleteOlderThan(keepFrom: String) {
        for (uuid in prefs.all.keys) {
            val entry = remembered(uuid) ?: continue
            val day = entry.optString("story_date")
            if (day.isNotBlank() && day < keepFrom) {
                deleteUri(Uri.parse(entry.optString("uri")))
                forget(uuid)
                log("Deleted old story video ${entry.optString("folder")}${entry.optString("file_name")}")
            }
        }
    }

    private fun deleteUri(uri: Uri) {
        try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            log("Could not delete $uri: ${e.message}")
        }
    }

    private fun remembered(uuid: String): JSONObject? =
        prefs.getString(uuid, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun remember(video: StoryVideoItem, uri: Uri) {
        val entry = JSONObject()
            .put("uri", uri.toString())
            .put("sha256", video.sha256.lowercase())
            .put("story_date", video.storyDate)
            .put("folder", video.folder)
            .put("file_name", video.fileName)
        prefs.edit().putString(video.uuid, entry.toString()).apply()
    }

    private fun forget(uuid: String) {
        prefs.edit().remove(uuid).apply()
    }

    private fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }

    private fun log(message: String) {
        android.util.Log.i("StoryBot", message)
    }
}

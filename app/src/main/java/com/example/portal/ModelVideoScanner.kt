package com.example.portal

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.portal.model.ModelVideoInfo
import com.example.portal.model.ModelVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class ModelVideoScanner(private val context: Context) {

    companion object {
        const val RELATIVE_PATH_PREFIX = "Movies/PageShare/"

        /** File names that start with a date, e.g. 2026-11-12.mp4 or 2026-11-12_leonor.mp4. */
        val DATED_NAME = Regex("""^\d{4}-\d{2}-\d{2}""")

        /** Today on the phone, yyyy-MM-dd. */
        fun todayDate(): String = java.time.LocalDate.now().toString()

        // Cache sha256 by key "$contentUriString-$sizeBytes-$dateModifiedSeconds"
        private val sha256Cache = ConcurrentHashMap<String, String>()

        /**
         * Extracts the model code from RELATIVE_PATH.
         * For example:
         * "Movies/PageShare/ANGLINA/" -> "ANGLINA"
         * "Movies/PageShare/ANGLINA/old/" -> "ANGLINA"
         * "Movies/PageShare/SCARLETT_01/" -> "SCARLETT_01"
         *
         * Returns null if path does not start with "Movies/PageShare/" or if code is invalid.
         */
        fun extractModelCode(relativePath: String?): String? {
            if (relativePath.isNullOrBlank()) return null
            val normalized = relativePath.replace('\\', '/')
            if (!normalized.startsWith(RELATIVE_PATH_PREFIX, ignoreCase = true)) {
                return null
            }

            val remainder = normalized.substring(RELATIVE_PATH_PREFIX.length)
            val segments = remainder.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return null

            val rawCode = segments[0].trim().uppercase()
            // Folder name is model code: upper case letters, digits and _
            val isValid = rawCode.isNotEmpty() && rawCode.all { it.isLetterOrDigit() || it == '_' }
            return if (isValid) rawCode else null
        }

        fun formatIsoDate(dateModifiedSeconds: Long): String {
            val millis = if (dateModifiedSeconds > 0) dateModifiedSeconds * 1000L else System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(Date(millis))
        }

        fun computeSha256(inputStream: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private data class RawVideoEntry(
        val id: Long,
        val modelCode: String,
        val displayName: String,
        val sizeBytes: Long,
        val dateModifiedSeconds: Long,
        val dateAddedSeconds: Long,
        val contentUri: Uri
    )

    /**
     * Scans MediaStore.Video for files under "Movies/PageShare/".
     * Keeps only the newest video per modelCode (largest dateModifiedSeconds, then dateAddedSeconds).
     * Computes sha256 (cached) and returns a list of ModelVideoInfo.
     */
    suspend fun scanNewestModelVideos(): List<ModelVideoInfo> = scanModelVideos(todayDate())

    /**
     * The video per model for [storyDate] (yyyy-MM-dd). A folder that holds dated files (names starting with a
     * date, e.g. 2026-11-12.mp4) gives the file for that day, or nothing when that day has no file. A folder
     * without dated files gives its newest video, as before.
     */
    suspend fun scanModelVideos(storyDate: String?): List<ModelVideoInfo> = withContext(Dispatchers.IO) {
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.RELATIVE_PATH else MediaStore.Video.Media.DATA
        )

        val rawVideosByModel = mutableMapOf<String, MutableList<RawVideoEntry>>()

        try {
            val cursor = context.contentResolver.query(
                collectionUri,
                projection,
                null,
                null,
                null
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateModCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    c.getColumnIndex(MediaStore.Video.Media.DATA)
                }

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val displayName = c.getString(nameCol) ?: "video_$id.mp4"
                    val sizeBytes = c.getLong(sizeCol)
                    val dateModified = c.getLong(dateModCol)
                    val dateAdded = c.getLong(dateAddCol)

                    val pathString = if (pathCol != -1) c.getString(pathCol) else null
                    val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        pathString
                    } else {
                        // On older Android, convert DATA absolute path to relative if it contains Movies/PageShare/
                        if (pathString != null && pathString.contains("Movies/PageShare/")) {
                            val idx = pathString.indexOf("Movies/PageShare/")
                            pathString.substring(idx)
                        } else null
                    }

                    val modelCode = extractModelCode(relativePath)
                    if (modelCode != null) {
                        val contentUri = ContentUris.withAppendedId(collectionUri, id)
                        val list = rawVideosByModel.getOrPut(modelCode) { mutableListOf() }
                        list.add(
                            RawVideoEntry(
                                id = id,
                                modelCode = modelCode,
                                displayName = displayName,
                                sizeBytes = sizeBytes,
                                dateModifiedSeconds = dateModified,
                                dateAddedSeconds = dateAdded,
                                contentUri = contentUri
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val resultList = mutableListOf<ModelVideoInfo>()

        for ((modelCode, videoList) in rawVideosByModel) {
            val dated = videoList.filter { DATED_NAME.containsMatchIn(it.displayName) }
            val newest = if (dated.isNotEmpty()) {
                // Dated folder: only the file for this day counts (newest copy if there are several).
                if (storyDate.isNullOrBlank()) continue
                dated.filter { it.displayName.startsWith(storyDate) }
                    .maxWithOrNull(compareBy<RawVideoEntry> { it.dateModifiedSeconds }.thenBy { it.dateAddedSeconds })
                    ?: continue
            } else {
                // Sort by largest DATE_MODIFIED, then largest DATE_ADDED
                videoList.maxWithOrNull(
                    compareBy<RawVideoEntry> { it.dateModifiedSeconds }
                        .thenBy { it.dateAddedSeconds }
                ) ?: continue
            }

            val uriStr = newest.contentUri.toString()
            val cacheKey = "$uriStr-${newest.sizeBytes}-${newest.dateModifiedSeconds}"
            val sha256 = sha256Cache.getOrPut(cacheKey) {
                try {
                    context.contentResolver.openInputStream(newest.contentUri)?.use { input ->
                        computeSha256(input)
                    } ?: ""
                } catch (e: Exception) {
                    ""
                }
            }

            val fileDateIso = formatIsoDate(newest.dateModifiedSeconds)

            resultList.add(
                ModelVideoInfo(
                    modelCode = modelCode,
                    fileName = newest.displayName,
                    sizeBytes = newest.sizeBytes,
                    sha256 = sha256,
                    fileDateIso = fileDateIso,
                    contentUriString = uriStr,
                    dateModifiedSeconds = newest.dateModifiedSeconds
                )
            )
        }

        // Return sorted alphabetically by modelCode
        resultList.sortedBy { it.modelCode }
    }

    /**
     * Takes the NEWEST video in Movies/PageShare/{{MODEL_CODE}}/ and makes a fresh copy
     * with MediaStore in DCIM/PageShareStory/ named story_<yyyyMMdd_HHmmss>.mp4,
     * with DATE_ADDED, DATE_MODIFIED and DATE_TAKEN = now (IS_PENDING=1 while copying, then 0),
     * so it is the newest video in the gallery and Facebook's picker shows it first.
     * Deletes the previous copies in DCIM/PageShareStory/ first.
     * Waits 2 seconds after the copy.
     * If the folder has no video, returns false (fails step with "No video in Movies/PageShare/<CODE>/").
     */
    suspend fun prepareNewestModelVideoForStory(modelCode: String): Boolean =
        prepareModelVideoForStory(modelCode, null) == null

    /**
     * Copy the model's video for [storyDate] to the top of the gallery (see [scanModelVideos] for how it is
     * chosen). Returns null when it worked, or the reason it could not.
     */
    suspend fun prepareModelVideoForStory(modelCode: String, storyDate: String?): String? = withContext(Dispatchers.IO) {
        val cleanCode = modelCode.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext "This page has no model"

        val chosen = scanModelVideos(storyDate)
        val targetVideo = chosen.find { it.modelCode.equals(cleanCode, ignoreCase = true) }
            ?: return@withContext if (storyDate.isNullOrBlank()) "No video in Movies/PageShare/$cleanCode/"
            else "No $cleanCode video for $storyDate (expected Movies/PageShare/$cleanCode/$storyDate.mp4)"

        val sourceUri = Uri.parse(targetVideo.contentUriString)

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // 1. Delete previous copies in DCIM/PageShareStory/
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(
                    collectionUri,
                    "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf("DCIM/PageShareStory/%")
                )
            } else {
                context.contentResolver.delete(
                    collectionUri,
                    "${MediaStore.Video.Media.DATA} LIKE ?",
                    arrayOf("%DCIM/PageShareStory/%")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Prepare new MediaStore item
        val now = System.currentTimeMillis()
        val nowSeconds = now / 1000L
        val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val targetFileName = "story_$timeStampStr.mp4"

        val values = android.content.ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, targetFileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, nowSeconds)
            put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.DATE_TAKEN, now)
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/PageShareStory/")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val newUri = context.contentResolver.insert(collectionUri, values) ?: return@withContext "Could not copy the video to the gallery"

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = android.content.ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                    put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds)
                }
                context.contentResolver.update(newUri, completeValues, null, null)
            }

            // Wait 2 seconds after the copy
            kotlinx.coroutines.delay(2000L)
            android.util.Log.i("StoryBot", "Prepared ${targetVideo.fileName} for $cleanCode${if (storyDate != null) " ($storyDate)" else ""}")
            null
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                context.contentResolver.delete(newUri, null, null)
            } catch (ignored: Exception) {}
            "Could not copy ${targetVideo.fileName}: ${e.message}"
        }
    }
}

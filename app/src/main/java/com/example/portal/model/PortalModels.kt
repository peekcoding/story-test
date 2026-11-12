package com.example.portal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EnrollRequest(
    @Json(name = "enrollment_code") val enrollmentCode: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "android_version") val androidVersion: String,
    @Json(name = "app_version") val appVersion: String
)

@JsonClass(generateAdapter = true)
data class EnrollSuccessResponse(
    @Json(name = "device_uuid") val deviceUuid: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class EnrollErrorResponse(
    @Json(name = "message") val message: String? = null,
    @Json(name = "errors") val errors: Map<String, List<String>>? = null
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "battery") val battery: Int,
    @Json(name = "charging") val charging: Boolean,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "current_task_uuid") val currentTaskUuid: String? = null,
    @Json(name = "current_step") val currentStep: String? = null
)

@JsonClass(generateAdapter = true)
data class HeartbeatSuccessResponse(
    @Json(name = "ok") val ok: Boolean = true,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "server_time") val serverTime: String? = null,
    @Json(name = "current_task_status") val currentTaskStatus: String? = null,
    @Json(name = "stop_current_task") val stopCurrentTask: Boolean = false,
    /** Commands from the portal to run now, e.g. a test from the Phones page. */
    @Json(name = "commands") val commands: List<DeviceCommandItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DeviceCommandItem(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class DeviceCommandResultRequest(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "checks") val checks: Map<String, Any?> = emptyMap()
)

sealed class EnrollResult {
    data class Success(val response: EnrollSuccessResponse) : EnrollResult()
    data class ValidationError(val message: String?, val codeError: String?) : EnrollResult()
    object RateLimited : EnrollResult()
    data class NetworkError(val message: String) : EnrollResult()
}

sealed class HeartbeatResult {
    data class Success(val response: HeartbeatSuccessResponse) : HeartbeatResult()
    object Unauthorized : HeartbeatResult()
    data class Error(val message: String) : HeartbeatResult()
}

@JsonClass(generateAdapter = true)
data class ModelVideoItem(
    @Json(name = "model_code") val modelCode: String,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "size_bytes") val sizeBytes: Long,
    @Json(name = "sha256") val sha256: String,
    @Json(name = "file_date") val fileDate: String
)

@JsonClass(generateAdapter = true)
data class ReportVideosRequest(
    @Json(name = "videos") val videos: List<ModelVideoItem>
)

@JsonClass(generateAdapter = true)
data class ReportVideosResponse(
    @Json(name = "ok") val ok: Boolean = true,
    @Json(name = "saved") val saved: Int = 0,
    @Json(name = "unknown_codes") val unknownCodes: List<String> = emptyList()
)

data class ModelVideoInfo(
    val modelCode: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val fileDateIso: String,
    val contentUriString: String,
    val dateModifiedSeconds: Long
)

sealed class ReportVideosResult {
    data class Success(val response: ReportVideosResponse) : ReportVideosResult()
    object Unauthorized : ReportVideosResult()
    data class ValidationError(val message: String) : ReportVideosResult()
    data class Error(val message: String) : ReportVideosResult()
}

// =============================================================================
// Portal Work & Task Models
// =============================================================================

@JsonClass(generateAdapter = true)
data class PortalTask(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "campaign_uuid") val campaignUuid: String? = null,
    @Json(name = "status") val status: String = "queued",
    @Json(name = "slot_number") val slotNumber: Int = 1,
    @Json(name = "account_label") val accountLabel: String? = null,
    @Json(name = "page_name") val pageName: String = "",
    @Json(name = "model_code") val modelCode: String = "",
    @Json(name = "link") val link: String = "",
    @Json(name = "starts_at") val startsAt: String? = null,
    /** The day whose video to post (yyyy-MM-dd): the file Movies/PageShare/<MODEL>/<date>.mp4. */
    @Json(name = "story_date") val storyDate: String? = null,
    @Json(name = "attempt") val attempt: Int = 0,
    // Local execution UI state
    val localStatus: String? = null,
    val localError: String? = null,
    val localCompletedTime: String? = null
)

@JsonClass(generateAdapter = true)
data class PortalSlot(
    @Json(name = "slot_number") val slotNumber: Int,
    @Json(name = "account_label") val accountLabel: String? = null,
    @Json(name = "tasks") val tasks: List<PortalTask> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PortalScriptPayload(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "name") val name: String = "",
    @Json(name = "steps") val steps: List<Map<String, Any?>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GetWorkResponse(
    @Json(name = "server_time") val serverTime: String? = null,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "script") val script: PortalScriptPayload? = null,
    @Json(name = "slots") val slots: List<PortalSlot> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TaskEvent(
    @Json(name = "step") val step: String,
    @Json(name = "message") val message: String? = null,
    @Json(name = "at") val at: String
)

@JsonClass(generateAdapter = true)
data class ReportEventsRequest(
    @Json(name = "events") val events: List<TaskEvent>
)

@JsonClass(generateAdapter = true)
data class ReportEventsResponse(
    @Json(name = "ok") val ok: Boolean = true,
    @Json(name = "stop") val stop: Boolean = false
)

@JsonClass(generateAdapter = true)
data class FinishTaskRequest(
    @Json(name = "result") val result: String, // "shared" | "failed" | "needs_person"
    @Json(name = "step") val step: String,
    @Json(name = "error") val error: String? = null,
    @Json(name = "seen_texts") val seenTexts: List<String> = emptyList(),
    @Json(name = "at") val at: String
)

@JsonClass(generateAdapter = true)
data class StartTaskErrorResponse(
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "message") val message: String? = null
)

sealed class GetWorkResult {
    data class Success(val response: GetWorkResponse) : GetWorkResult()
    object Unauthorized : GetWorkResult()
    data class Error(val message: String) : GetWorkResult()
}



/** One story video the portal wants on this phone (D38). */
@JsonClass(generateAdapter = true)
data class StoryVideoItem(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "model_code") val modelCode: String,
    @Json(name = "story_date") val storyDate: String,
    @Json(name = "folder") val folder: String,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "size_bytes") val sizeBytes: Long,
    @Json(name = "sha256") val sha256: String,
    @Json(name = "download_url") val downloadUrl: String,
    @Json(name = "downloaded") val downloaded: Boolean = false
)

@JsonClass(generateAdapter = true)
data class StoryVideosResponse(
    @Json(name = "server_time") val serverTime: String? = null,
    @Json(name = "base_folder") val baseFolder: String? = null,
    @Json(name = "keep_from") val keepFrom: String? = null,
    @Json(name = "videos") val videos: List<StoryVideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StoryVideoDownloadedRequest(
    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "error") val error: String? = null
)

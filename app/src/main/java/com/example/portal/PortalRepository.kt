package com.example.portal

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.BuildConfig
import com.example.portal.model.EnrollErrorResponse
import com.example.portal.model.EnrollRequest
import com.example.portal.model.EnrollResult
import com.example.portal.model.HeartbeatRequest
import com.example.portal.model.HeartbeatResult
import com.example.portal.model.HeartbeatSuccessResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class PortalDeviceState(
    val portalAddress: String = "https://manage.menumeet.com",
    val isRegistered: Boolean = false,
    val deviceUuid: String? = null,
    val deviceName: String? = null,
    val registeredSinceTime: String? = null,
    val lastHeartbeatTime: String? = null,
    val lastError: String? = null,
    val scannedVideos: List<com.example.portal.model.ModelVideoInfo> = emptyList(),
    val lastVideosReportTime: String? = null,
    val unknownModelCodes: List<String> = emptyList(),
    val isReportingVideos: Boolean = false,
    val scriptVersion: Int? = null,
    val scriptName: String? = null,
    val workSlots: List<com.example.portal.model.PortalSlot> = emptyList(),
    val lastWorkCheckTime: String? = null,
    val isLoadingWork: Boolean = false,
    val isPostingWork: Boolean = false,
    val postingStatusMessage: String? = null,
    val autoPostEnabled: Boolean = true,
    val pendingReports: Int = 0,
    val useMultiSpace: Boolean = true,
    /** Which app holds the Facebook accounts when [useMultiSpace] is on: "slotbox" or "multispace". */
    val accountsApp: String = "multispace"
)

class PortalRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "portal_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for environments where KeyStore has issues
            context.getSharedPreferences("portal_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val generalPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("portal_general_prefs", Context.MODE_PRIVATE)
    }

    private val _deviceState = MutableStateFlow(loadInitialState())
    val deviceState: StateFlow<PortalDeviceState> = _deviceState.asStateFlow()

    private val _stopCurrentTaskRequested = MutableStateFlow(false)
    val stopCurrentTaskRequested: StateFlow<Boolean> = _stopCurrentTaskRequested.asStateFlow()

    fun requestStopCurrentTask() {
        _stopCurrentTaskRequested.value = true
    }

    fun clearStopCurrentTask() {
        _stopCurrentTaskRequested.value = false
    }

    fun isStopRequested(): Boolean = _stopCurrentTaskRequested.value

    private var currentApi: PortalApi? = null
    private var currentPortalUrl: String? = null

    private fun loadInitialState(): PortalDeviceState = loadBaseState().copy(
        autoPostEnabled = generalPrefs.getBoolean("auto_post_enabled", true),
        useMultiSpace = generalPrefs.getBoolean("use_multi_space", true),
        accountsApp = generalPrefs.getString("accounts_app", "multispace") ?: "multispace",
        pendingReports = readOutbox().length(),
    )

    private fun loadBaseState(): PortalDeviceState {
        val portalAddress = generalPrefs.getString("portal_address", "https://manage.menumeet.com")
            ?: "https://manage.menumeet.com"
        val token = securePrefs.getString("token", null)
        val deviceUuid = securePrefs.getString("device_uuid", null)
        val deviceName = securePrefs.getString("device_name", null)
        val registeredSince = generalPrefs.getString("registered_since", null)
        val lastHeartbeat = generalPrefs.getString("last_heartbeat", null)
        val lastVideosReport = generalPrefs.getString("last_videos_report", null)

        val isRegistered = !token.isNullOrBlank() && !deviceUuid.isNullOrBlank()
        val cachedVersion = if (generalPrefs.contains("cached_script_version")) generalPrefs.getInt("cached_script_version", 1) else null
        val cachedName = generalPrefs.getString("cached_script_name", null)

        return PortalDeviceState(
            portalAddress = portalAddress,
            isRegistered = isRegistered,
            deviceUuid = deviceUuid,
            deviceName = deviceName,
            registeredSinceTime = registeredSince,
            lastHeartbeatTime = lastHeartbeat,
            lastVideosReportTime = lastVideosReport,
            scriptVersion = cachedVersion,
            scriptName = cachedName,
            lastError = null
        )
    }

    @Synchronized
    private fun getApi(baseUrl: String): PortalApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (currentApi == null || currentPortalUrl != cleanUrl) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(cleanUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            currentApi = retrofit.create(PortalApi::class.java)
            currentPortalUrl = cleanUrl
        }
        return currentApi!!
    }

    fun updatePortalAddress(newAddress: String) {
        val trimmed = newAddress.trim()
        if (trimmed.isEmpty()) return
        generalPrefs.edit().putString("portal_address", trimmed).apply()
        _deviceState.value = _deviceState.value.copy(portalAddress = trimmed)
    }

    fun getSavedToken(): String? {
        return securePrefs.getString("token", null)
    }

    /** Send the result of a command from the portal (e.g. a phone test). */
    suspend fun reportCommandResult(uuid: String, ok: Boolean, message: String?, checks: Map<String, Any?>): Boolean {
        val token = getSavedToken() ?: return false
        return try {
            getApi(_deviceState.value.portalAddress)
                .reportCommandResult("Bearer $token", uuid, com.example.portal.model.DeviceCommandResultRequest(ok, message?.take(1900), checks))
                .isSuccessful
        } catch (e: Exception) {
            android.util.Log.w("StoryBot", "Command result failed: ${e.message}")
            false
        }
    }

    /** The story videos the portal wants on this phone (D38), or null when the portal could not be reached. */
    suspend fun getStoryVideos(): com.example.portal.model.StoryVideosResponse? {
        val token = getSavedToken() ?: return null
        return try {
            val response = getApi(_deviceState.value.portalAddress).getStoryVideos("Bearer $token")
            if (response.code() == 401) {
                signOut("This phone is not registered. Register it again with a new code.")
                null
            } else {
                response.body()
            }
        } catch (e: Exception) {
            android.util.Log.w("StoryBot", "Story videos list failed: ${e.message}")
            null
        }
    }

    /** Tell the portal a story video was saved (its checksum) or could not be downloaded (the error). */
    suspend fun reportStoryVideoDownloaded(uuid: String, sha256: String?, error: String?): Boolean {
        val token = getSavedToken() ?: return false
        return try {
            getApi(_deviceState.value.portalAddress)
                .reportStoryVideoDownloaded("Bearer $token", uuid, com.example.portal.model.StoryVideoDownloadedRequest(sha256, error?.take(1900)))
                .isSuccessful
        } catch (e: Exception) {
            android.util.Log.w("StoryBot", "Story video report failed: ${e.message}")
            false
        }
    }

    fun isRegistered(): Boolean {
        return !getSavedToken().isNullOrBlank()
    }

    suspend fun enroll(code: String): EnrollResult {
        val address = _deviceState.value.portalAddress
        val api = try {
            getApi(address)
        } catch (e: Exception) {
            val errMsg = "Invalid portal address: ${e.message}"
            _deviceState.value = _deviceState.value.copy(lastError = errMsg)
            return EnrollResult.NetworkError(errMsg)
        }

        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = Build.VERSION.RELEASE ?: "unknown"
        val appVersion = BuildConfig.VERSION_NAME

        val request = EnrollRequest(
            enrollmentCode = code.trim().uppercase(),
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            appVersion = appVersion
        )

        return try {
            val response = api.enrollDevice(request)
            when (response.code()) {
                201 -> {
                    val body = response.body()
                    if (body != null) {
                        saveEnrollment(body.token, body.deviceUuid, body.deviceName)
                        EnrollResult.Success(body)
                    } else {
                        val err = "Empty response from server"
                        _deviceState.value = _deviceState.value.copy(lastError = err)
                        EnrollResult.NetworkError(err)
                    }
                }
                422 -> {
                    val errorBodyStr = response.errorBody()?.string()
                    val errorObj = parseEnrollError(errorBodyStr)
                    val codeError = errorObj?.errors?.get("enrollment_code")?.firstOrNull()
                    val msg = errorObj?.message ?: "Validation error"
                    _deviceState.value = _deviceState.value.copy(lastError = codeError ?: msg)
                    EnrollResult.ValidationError(msg, codeError)
                }
                429 -> {
                    val err = "Too many tries, wait a minute."
                    _deviceState.value = _deviceState.value.copy(lastError = err)
                    EnrollResult.RateLimited
                }
                else -> {
                    val err = "Registration failed (${response.code()}): ${response.message()}"
                    _deviceState.value = _deviceState.value.copy(lastError = err)
                    EnrollResult.NetworkError(err)
                }
            }
        } catch (e: Exception) {
            val err = "Network error: ${e.message ?: "Unknown error"}"
            _deviceState.value = _deviceState.value.copy(lastError = err)
            EnrollResult.NetworkError(err)
        }
    }

    private fun parseEnrollError(json: String?): EnrollErrorResponse? {
        if (json.isNullOrBlank()) return null
        return try {
            moshi.adapter(EnrollErrorResponse::class.java).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveEnrollment(token: String, deviceUuid: String, deviceName: String) {
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        securePrefs.edit()
            .putString("token", token)
            .putString("device_uuid", deviceUuid)
            .putString("device_name", deviceName)
            .apply()

        generalPrefs.edit()
            .putString("registered_since", nowIso)
            .apply()

        _deviceState.value = _deviceState.value.copy(
            isRegistered = true,
            deviceUuid = deviceUuid,
            deviceName = deviceName,
            registeredSinceTime = nowIso,
            lastError = null
        )
    }

    fun signOut(reason: String? = null) {
        securePrefs.edit()
            .remove("token")
            .remove("device_uuid")
            .remove("device_name")
            .apply()

        generalPrefs.edit()
            .remove("registered_since")
            .remove("last_heartbeat")
            .apply()

        _deviceState.value = _deviceState.value.copy(
            isRegistered = false,
            deviceUuid = null,
            deviceName = null,
            registeredSinceTime = null,
            lastHeartbeatTime = null,
            lastError = reason
        )
    }

    suspend fun sendHeartbeat(battery: Int, charging: Boolean, currentTaskUuid: String? = null, currentStep: String? = null): HeartbeatResult {
        val token = getSavedToken()
        if (token.isNullOrBlank()) {
            return HeartbeatResult.Unauthorized
        }

        val api = try {
            getApi(_deviceState.value.portalAddress)
        } catch (e: Exception) {
            val err = "Heartbeat failed (address error): ${e.message}"
            _deviceState.value = _deviceState.value.copy(lastError = err)
            return HeartbeatResult.Error(err)
        }

        val request = HeartbeatRequest(
            battery = battery.coerceIn(0, 100),
            charging = charging,
            appVersion = BuildConfig.VERSION_NAME,
            currentTaskUuid = currentTaskUuid,
            currentStep = currentStep
        )

        return try {
            val response = api.sendHeartbeat("Bearer $token", request)
            when (response.code()) {
                200 -> {
                    val body = response.body()
                    rememberPortalClock(body?.serverTime)
                    val serverTime = body?.serverTime ?: java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    generalPrefs.edit().putString("last_heartbeat", serverTime).apply()

                    val updatedName = body?.deviceName ?: _deviceState.value.deviceName
                    if (updatedName != null && updatedName != _deviceState.value.deviceName) {
                        securePrefs.edit().putString("device_name", updatedName).apply()
                    }

                    if (body?.stopCurrentTask == true) {
                        requestStopCurrentTask()
                    }

                    _deviceState.value = _deviceState.value.copy(
                        lastHeartbeatTime = serverTime,
                        deviceName = updatedName,
                        lastError = null
                    )
                    flushOutbox()
                    HeartbeatResult.Success(body ?: HeartbeatSuccessResponse(true, updatedName, serverTime))
                }
                401 -> {
                    signOut("This phone is not registered. Register it again with a new code.")
                    HeartbeatResult.Unauthorized
                }
                else -> {
                    val err = "Heartbeat failed (${response.code()}): ${response.message()}"
                    _deviceState.value = _deviceState.value.copy(lastError = err)
                    HeartbeatResult.Error(err)
                }
            }
        } catch (e: Exception) {
            val err = "Heartbeat network error: ${e.message ?: "Connection failed"}"
            _deviceState.value = _deviceState.value.copy(lastError = err)
            HeartbeatResult.Error(err)
        }
    }

    suspend fun scanAndReportVideos(): com.example.portal.model.ReportVideosResult {
        val scanner = ModelVideoScanner(context)
        val scanned = scanner.scanNewestModelVideos()

        _deviceState.value = _deviceState.value.copy(
            scannedVideos = scanned,
            isReportingVideos = true
        )

        val token = getSavedToken()
        if (token.isNullOrBlank()) {
            _deviceState.value = _deviceState.value.copy(isReportingVideos = false)
            return com.example.portal.model.ReportVideosResult.Unauthorized
        }

        val api = try {
            getApi(_deviceState.value.portalAddress)
        } catch (e: Exception) {
            val err = "Video report failed (address error): ${e.message}"
            _deviceState.value = _deviceState.value.copy(lastError = err, isReportingVideos = false)
            return com.example.portal.model.ReportVideosResult.Error(err)
        }

        val items = scanned.map {
            com.example.portal.model.ModelVideoItem(
                modelCode = it.modelCode,
                fileName = it.fileName,
                sizeBytes = it.sizeBytes,
                sha256 = it.sha256,
                fileDate = it.fileDateIso
            )
        }
        val request = com.example.portal.model.ReportVideosRequest(videos = items)

        return try {
            val response = api.reportVideos("Bearer $token", request)
            _deviceState.value = _deviceState.value.copy(isReportingVideos = false)

            when (response.code()) {
                200 -> {
                    val body = response.body() ?: com.example.portal.model.ReportVideosResponse(true, items.size, emptyList())
                    val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    generalPrefs.edit().putString("last_videos_report", nowStr).apply()

                    _deviceState.value = _deviceState.value.copy(
                        lastVideosReportTime = nowStr,
                        unknownModelCodes = body.unknownCodes,
                        lastError = null
                    )
                    com.example.portal.model.ReportVideosResult.Success(body)
                }
                401 -> {
                    signOut("This phone is not registered. Register it again with a new code.")
                    com.example.portal.model.ReportVideosResult.Unauthorized
                }
                422 -> {
                    val errJson = response.errorBody()?.string()
                    val parsed = parseEnrollError(errJson)
                    val msg = parsed?.message ?: "Validation error (${response.code()})"
                    _deviceState.value = _deviceState.value.copy(lastError = msg)
                    com.example.portal.model.ReportVideosResult.ValidationError(msg)
                }
                else -> {
                    val err = "Report videos failed (${response.code()}): ${response.message()}"
                    _deviceState.value = _deviceState.value.copy(lastError = err)
                    com.example.portal.model.ReportVideosResult.Error(err)
                }
            }
        } catch (e: Exception) {
            val err = "Report videos error: ${e.message ?: "Connection failed"}"
            _deviceState.value = _deviceState.value.copy(lastError = err, isReportingVideos = false)
            com.example.portal.model.ReportVideosResult.Error(err)
        }
    }

    // =========================================================================
    // Work & Script Management
    // =========================================================================

    suspend fun getWork(): com.example.portal.model.GetWorkResult {
        val token = getSavedToken()
        if (token.isNullOrBlank()) {
            return com.example.portal.model.GetWorkResult.Unauthorized
        }

        flushOutbox()
        _deviceState.value = _deviceState.value.copy(isLoadingWork = true)

        val api = try {
            getApi(_deviceState.value.portalAddress)
        } catch (e: Exception) {
            val err = "Get work failed (address error): ${e.message}"
            _deviceState.value = _deviceState.value.copy(lastError = err, isLoadingWork = false)
            return com.example.portal.model.GetWorkResult.Error(err)
        }

        return try {
            val response = api.getWork("Bearer $token")
            _deviceState.value = _deviceState.value.copy(isLoadingWork = false)

            when (response.code()) {
                200 -> {
                    val body = response.body()
                    val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    if (body != null) {
                        rememberPortalClock(body.serverTime)
                        body.script?.let { scriptPayload ->
                            // Save script & version to SharedPreferences
                            val stepsJson = try {
                                moshi.adapter(List::class.java).toJson(scriptPayload.steps)
                            } catch (e: Exception) {
                                "[]"
                            }
                            generalPrefs.edit()
                                .putInt("cached_script_version", scriptPayload.version)
                                .putString("cached_script_name", scriptPayload.name)
                                .putString("cached_script_steps_json", stepsJson)
                                .apply()
                        }

                        val scriptVer = body.script?.version ?: generalPrefs.getInt("cached_script_version", 1)
                        val scriptNm = body.script?.name ?: generalPrefs.getString("cached_script_name", null)

                        _deviceState.value = _deviceState.value.copy(
                            scriptVersion = scriptVer,
                            scriptName = scriptNm,
                            workSlots = body.slots,
                            lastWorkCheckTime = nowStr,
                            lastError = null
                        )
                        com.example.portal.model.GetWorkResult.Success(body)
                    } else {
                        val err = "Empty response from portal"
                        _deviceState.value = _deviceState.value.copy(lastError = err)
                        com.example.portal.model.GetWorkResult.Error(err)
                    }
                }
                401 -> {
                    signOut("This phone is not registered. Register it again with a new code.")
                    com.example.portal.model.GetWorkResult.Unauthorized
                }
                else -> {
                    val err = "Check for work failed (${response.code()}): ${response.message()}"
                    _deviceState.value = _deviceState.value.copy(lastError = err)
                    com.example.portal.model.GetWorkResult.Error(err)
                }
            }
        } catch (e: Exception) {
            val err = "Check for work error: ${e.message ?: "Connection failed"}"
            _deviceState.value = _deviceState.value.copy(lastError = err, isLoadingWork = false)
            com.example.portal.model.GetWorkResult.Error(err)
        }
    }

    fun getCachedScriptSteps(): List<com.example.data.model.ScriptStep> {
        val json = generalPrefs.getString("cached_script_steps_json", null) ?: return emptyList()
        return try {
            com.example.data.JsonCodec.jsonToSteps(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Call API with 3 retries (5s apart) on network error
    suspend fun <T> callWithRetry(block: suspend () -> retrofit2.Response<T>): retrofit2.Response<T>? {
        var lastResponse: retrofit2.Response<T>? = null
        for (attempt in 1..3) {
            try {
                val res = block()
                if (res.code() == 401) {
                    signOut("This phone is not registered. Register it again with a new code.")
                    return res
                }
                if (res.isSuccessful || res.code() == 409 || res.code() == 422) {
                    return res
                }
                lastResponse = res
            } catch (e: Exception) {
                // Network error, wait and retry
            }
            if (attempt < 3) {
                kotlinx.coroutines.delay(5000L)
            }
        }
        return lastResponse
    }

    suspend fun sendTaskStart(taskUuid: String): Pair<Boolean, String?> {
        val token = getSavedToken() ?: return Pair(false, "Not registered")
        val api = try { getApi(_deviceState.value.portalAddress) } catch (e: Exception) { return Pair(false, e.message) }

        val res = callWithRetry { api.startTask("Bearer $token", taskUuid) }
        if (res == null) return Pair(true, null) // Continue after retries fail per requirements

        if (res.code() == 200) {
            return Pair(true, null)
        }
        if (res.code() == 409) {
            val errBody = res.errorBody()?.string()
            val parsed = try {
                moshi.adapter(com.example.portal.model.StartTaskErrorResponse::class.java).fromJson(errBody ?: "")
            } catch (e: Exception) {
                null
            }
            val reason = parsed?.reason ?: parsed?.message ?: "Task already started or closed"
            return Pair(false, reason)
        }
        return Pair(true, null)
    }

    suspend fun sendReportEvents(taskUuid: String, stepText: String, message: String? = null): Boolean {
        val token = getSavedToken() ?: return false
        val api = try { getApi(_deviceState.value.portalAddress) } catch (e: Exception) { return false }

        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val req = com.example.portal.model.ReportEventsRequest(
            events = listOf(
                com.example.portal.model.TaskEvent(
                    step = stepText,
                    message = message,
                    at = nowIso
                )
            )
        )

        if (outboxSize() > 0) {
            // Keep the order: earlier reports are still waiting to be sent.
            enqueue("events", taskUuid, moshi.adapter(com.example.portal.model.ReportEventsRequest::class.java).toJson(req))
            flushOutbox()
            return isStopRequested()
        }

        val res = callWithRetry { api.sendTaskEvents("Bearer $token", taskUuid, req) }
        if (res == null || res.code() >= 500) {
            enqueue("events", taskUuid, moshi.adapter(com.example.portal.model.ReportEventsRequest::class.java).toJson(req))
        }
        val body = res?.body()
        val shouldStop = body?.stop == true
        if (shouldStop) {
            requestStopCurrentTask()
        }
        return shouldStop
    }

    suspend fun sendTaskFinish(
        taskUuid: String,
        result: String,
        step: String,
        error: String?,
        seenTexts: List<String>
    ) {
        val token = getSavedToken() ?: return
        val api = try { getApi(_deviceState.value.portalAddress) } catch (e: Exception) { return }

        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val last50 = if (seenTexts.size > 50) seenTexts.takeLast(50) else seenTexts

        val req = com.example.portal.model.FinishTaskRequest(
            result = result,
            step = step,
            error = error,
            seenTexts = last50,
            at = nowIso
        )

        val json = moshi.adapter(com.example.portal.model.FinishTaskRequest::class.java).toJson(req)
        if (outboxSize() > 0) {
            enqueue("finish", taskUuid, json)
            flushOutbox()
            return
        }
        val res = callWithRetry { api.finishTask("Bearer $token", taskUuid, req) }
        if (res == null || res.code() >= 500) {
            enqueue("finish", taskUuid, json)
        }
    }

    suspend fun uploadTaskScreenshot(taskUuid: String, file: java.io.File) {
        val token = getSavedToken() ?: return
        if (!file.exists()) return
        val api = try { getApi(_deviceState.value.portalAddress) } catch (e: Exception) { return }

        val mediaType = "image/png".toMediaTypeOrNull()
        val requestFile = file.asRequestBody(mediaType)
        val bodyPart = okhttp3.MultipartBody.Part.createFormData("screenshot", file.name, requestFile)

        callWithRetry { api.uploadScreenshot("Bearer $token", taskUuid, bodyPart) }
    }

    fun updateTaskLocalStatus(taskUuid: String, status: String, error: String? = null) {
        val nowTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val updatedSlots = _deviceState.value.workSlots.map { slot ->
            val updatedTasks = slot.tasks.map { task ->
                if (task.uuid == taskUuid) {
                    task.copy(
                        localStatus = status,
                        localError = error,
                        localCompletedTime = nowTime
                    )
                } else {
                    task
                }
            }
            slot.copy(tasks = updatedTasks)
        }
        _deviceState.value = _deviceState.value.copy(workSlots = updatedSlots)
    }

    // ---------------------------------------------------------------------------------------------
    // Portal clock: scheduled campaigns start by the portal's time, so a wrong phone clock does not matter.
    // ---------------------------------------------------------------------------------------------

    @Volatile
    private var portalClockOffsetMs: Long = generalPrefs.getLong("portal_clock_offset_ms", 0L)

    private fun rememberPortalClock(serverTime: String?) {
        val serverMs = PortalTime.parseMillis(serverTime) ?: return
        portalClockOffsetMs = serverMs - System.currentTimeMillis()
        generalPrefs.edit().putLong("portal_clock_offset_ms", portalClockOffsetMs).apply()
    }

    /** The portal's current time in milliseconds. */
    fun portalNowMillis(): Long = System.currentTimeMillis() + portalClockOffsetMs

    // ---------------------------------------------------------------------------------------------
    // Automatic posting switch
    // ---------------------------------------------------------------------------------------------

    fun isAutoPostEnabled(): Boolean = _deviceState.value.autoPostEnabled

    /** M2: the Facebook accounts (slots) are the numbered copies in Multi Space; the slot is the copy's number. */
    fun useMultiSpace(): Boolean = _deviceState.value.useMultiSpace

    fun accountsApp(): String = _deviceState.value.accountsApp

    /**
     * Where the Facebook accounts are: "slotbox" (ours), "multispace", or "off" (only slot 1, in the Facebook
     * app that is open).
     */
    fun setAccountsApp(value: String) {
        val app = if (value == "slotbox") "slotbox" else "multispace"
        val on = value != "off"
        generalPrefs.edit().putBoolean("use_multi_space", on).putString("accounts_app", app).apply()
        _deviceState.value = _deviceState.value.copy(useMultiSpace = on, accountsApp = app)
    }

    fun setUseMultiSpace(enabled: Boolean) {
        generalPrefs.edit().putBoolean("use_multi_space", enabled).apply()
        _deviceState.value = _deviceState.value.copy(useMultiSpace = enabled)
    }

    fun setAutoPostEnabled(enabled: Boolean) {
        generalPrefs.edit().putBoolean("auto_post_enabled", enabled).apply()
        _deviceState.value = _deviceState.value.copy(autoPostEnabled = enabled)
    }

    // ---------------------------------------------------------------------------------------------
    // Outbox: step reports and results that could not be sent (no internet) are kept, in order, and sent
    // as soon as the portal answers again. A result is never lost.
    // ---------------------------------------------------------------------------------------------

    private val outboxLock = Mutex()

    private fun readOutbox(): JSONArray = try {
        JSONArray(generalPrefs.getString("outbox", "[]") ?: "[]")
    } catch (e: Exception) {
        JSONArray()
    }

    private fun writeOutbox(items: JSONArray) {
        generalPrefs.edit().putString("outbox", items.toString()).apply()
        _deviceState.value = _deviceState.value.copy(pendingReports = items.length())
    }

    fun outboxSize(): Int = readOutbox().length()

    private fun enqueue(kind: String, taskUuid: String, bodyJson: String) {
        synchronized(this) {
            val items = readOutbox()
            if (items.length() >= 500) items.remove(0)
            items.put(JSONObject().put("kind", kind).put("task", taskUuid).put("body", bodyJson))
            writeOutbox(items)
        }
    }

    /**
     * Send waiting reports in order. Stops at the first one that can not be delivered yet (no internet or
     * portal error) and keeps it and the rest. Answers like 409 or 404 mean the portal no longer needs it.
     */
    suspend fun flushOutbox() {
        if (outboxSize() == 0) return
        val token = getSavedToken() ?: return
        val api = try { getApi(_deviceState.value.portalAddress) } catch (e: Exception) { return }

        outboxLock.withLock {
            while (true) {
                val items = readOutbox()
                if (items.length() == 0) return
                val item = items.getJSONObject(0)
                val task = item.getString("task")
                val body = item.getString("body")

                val code = try {
                    when (item.getString("kind")) {
                        "finish" -> {
                            val req = moshi.adapter(com.example.portal.model.FinishTaskRequest::class.java).fromJson(body)
                            if (req == null) 422 else api.finishTask("Bearer $token", task, req).code()
                        }
                        else -> {
                            val req = moshi.adapter(com.example.portal.model.ReportEventsRequest::class.java).fromJson(body)
                            if (req == null) 422 else api.sendTaskEvents("Bearer $token", task, req).code()
                        }
                    }
                } catch (e: Exception) {
                    -1 // still no internet
                }

                if (code == 401) {
                    signOut("This phone is not registered. Register it again with a new code.")
                    return
                }
                if (code == -1 || code >= 500) return

                synchronized(this) {
                    val current = readOutbox()
                    if (current.length() > 0) current.remove(0)
                    writeOutbox(current)
                }
            }
        }
    }

    fun setPostingWorkState(isPosting: Boolean, message: String? = null) {
        _deviceState.value = _deviceState.value.copy(
            isPostingWork = isPosting,
            postingStatusMessage = message
        )
    }
}

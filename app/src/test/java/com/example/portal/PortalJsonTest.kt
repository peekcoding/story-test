package com.example.portal

import com.example.portal.model.EnrollErrorResponse
import com.example.portal.model.EnrollRequest
import com.example.portal.model.EnrollSuccessResponse
import com.example.portal.model.HeartbeatRequest
import com.example.portal.model.HeartbeatSuccessResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalJsonTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testEnrollRequestBodyMatchesExactFieldNames() {
        // Required body:
        // {"enrollment_code": "<code>", "device_model": Build.MANUFACTURER + " " + Build.MODEL,
        //  "android_version": Build.VERSION.RELEASE, "app_version": BuildConfig.VERSION_NAME}
        val request = EnrollRequest(
            enrollmentCode = "ABC12345",
            deviceModel = "Google Pixel 8",
            androidVersion = "14",
            appVersion = "1.0"
        )

        val adapter = moshi.adapter(EnrollRequest::class.java)
        val json = adapter.toJson(request)
        val jsonObject = JSONObject(json)

        assertTrue(jsonObject.has("enrollment_code"))
        assertTrue(jsonObject.has("device_model"))
        assertTrue(jsonObject.has("android_version"))
        assertTrue(jsonObject.has("app_version"))

        assertEquals("ABC12345", jsonObject.getString("enrollment_code"))
        assertEquals("Google Pixel 8", jsonObject.getString("device_model"))
        assertEquals("14", jsonObject.getString("android_version"))
        assertEquals("1.0", jsonObject.getString("app_version"))

        // Ensure no extra camelCase fields leaked
        assertFalse(jsonObject.has("enrollmentCode"))
        assertFalse(jsonObject.has("deviceModel"))
        assertFalse(jsonObject.has("androidVersion"))
        assertFalse(jsonObject.has("appVersion"))
    }

    @Test
    fun testEnrollSuccessResponseMatchesExactFieldNames() {
        // Required 201 answer: {"device_uuid": "...", "device_name": "Phone 01", "token": "<48 characters>"}
        val rawJson = """
            {
                "device_uuid": "d3b07384-d113-40a2-9b55-879e60228d7a",
                "device_name": "Phone 01",
                "token": "tok_1234567890abcdef1234567890abcdef1234567890ab"
            }
        """.trimIndent()

        val adapter = moshi.adapter(EnrollSuccessResponse::class.java)
        val response = adapter.fromJson(rawJson)

        assertNotNull(response)
        assertEquals("d3b07384-d113-40a2-9b55-879e60228d7a", response?.deviceUuid)
        assertEquals("Phone 01", response?.deviceName)
        assertEquals("tok_1234567890abcdef1234567890abcdef1234567890ab", response?.token)
    }

    @Test
    fun testEnroll422ErrorResponseMatchesExactFieldNames() {
        // Required 422 answer: {"message": "...", "errors": {"enrollment_code": ["..."]}}
        val rawJson = """
            {
                "message": "The given data was invalid.",
                "errors": {
                    "enrollment_code": [
                        "The enrollment code is invalid or expired."
                    ]
                }
            }
        """.trimIndent()

        val adapter = moshi.adapter(EnrollErrorResponse::class.java)
        val errorResponse = adapter.fromJson(rawJson)

        assertNotNull(errorResponse)
        assertEquals("The given data was invalid.", errorResponse?.message)
        val codeErrors = errorResponse?.errors?.get("enrollment_code")
        assertNotNull(codeErrors)
        assertEquals(1, codeErrors?.size)
        assertEquals("The enrollment code is invalid or expired.", codeErrors?.first())
    }

    @Test
    fun testHeartbeatRequestBodyMatchesExactFieldNames() {
        // Required body:
        // {"battery": <0-100>, "charging": <true/false>, "app_version": "<version>",
        //  "current_task_uuid": null, "current_step": null}
        val request = HeartbeatRequest(
            battery = 85,
            charging = true,
            appVersion = "1.0",
            currentTaskUuid = "task-uuid-1234",
            currentStep = "SWIPE_UNTIL_TEXT"
        )

        val adapter = moshi.adapter(HeartbeatRequest::class.java)
        val json = adapter.toJson(request)
        val jsonObject = JSONObject(json)

        assertTrue(jsonObject.has("battery"))
        assertTrue(jsonObject.has("charging"))
        assertTrue(jsonObject.has("app_version"))
        assertTrue(jsonObject.has("current_task_uuid"))
        assertTrue(jsonObject.has("current_step"))

        assertEquals(85, jsonObject.getInt("battery"))
        assertEquals(true, jsonObject.getBoolean("charging"))
        assertEquals("1.0", jsonObject.getString("app_version"))
        assertEquals("task-uuid-1234", jsonObject.getString("current_task_uuid"))
        assertEquals("SWIPE_UNTIL_TEXT", jsonObject.getString("current_step"))

        // Nullable test
        val nullRequest = HeartbeatRequest(
            battery = 42,
            charging = false,
            appVersion = "1.0",
            currentTaskUuid = null,
            currentStep = null
        )
        val nullJson = adapter.toJson(nullRequest)
        val nullJsonObject = JSONObject(nullJson)
        assertEquals(42, nullJsonObject.getInt("battery"))
        assertFalse(nullJsonObject.getBoolean("charging"))
        assertTrue(nullJsonObject.isNull("current_task_uuid") || !nullJsonObject.has("current_task_uuid"))
    }

    @Test
    fun testHeartbeatSuccessResponseMatchesExactFieldNames() {
        // Required 200 answer: {"ok": true, "device_name": "Phone 01", "server_time": "<ISO time>",
        //                       "current_task_status": "cancelled", "stop_current_task": true}
        val rawJson = """
            {
                "ok": true,
                "device_name": "Phone 01",
                "server_time": "2026-11-07T10:30:00Z",
                "current_task_status": "cancelled",
                "stop_current_task": true
            }
        """.trimIndent()

        val adapter = moshi.adapter(HeartbeatSuccessResponse::class.java)
        val response = adapter.fromJson(rawJson)

        assertNotNull(response)
        assertTrue(response?.ok == true)
        assertEquals("Phone 01", response?.deviceName)
        assertEquals("2026-11-07T10:30:00Z", response?.serverTime)
        assertEquals("cancelled", response?.currentTaskStatus)
        assertTrue(response?.stopCurrentTask == true)
    }

    @Test
    fun testModelVideoScannerExtractModelCode() {
        // Required unit test:
        // "Movies/PageShare/ANGLINA/" and "Movies/PageShare/ANGLINA/old/" (both are ANGLINA)
        assertEquals("ANGLINA", ModelVideoScanner.extractModelCode("Movies/PageShare/ANGLINA/"))
        assertEquals("ANGLINA", ModelVideoScanner.extractModelCode("Movies/PageShare/ANGLINA/old/"))
        assertEquals("SCARLETT", ModelVideoScanner.extractModelCode("Movies/PageShare/SCARLETT/"))
        assertEquals("MODEL_99", ModelVideoScanner.extractModelCode("Movies/PageShare/MODEL_99/sub/nested/"))

        // Invalid or non-matching paths
        assertNull(ModelVideoScanner.extractModelCode("Movies/OtherFolder/ANGLINA/"))
        assertNull(ModelVideoScanner.extractModelCode("DCIM/Camera/"))
        assertNull(ModelVideoScanner.extractModelCode("Movies/PageShare/"))
        assertNull(ModelVideoScanner.extractModelCode(null))
        assertNull(ModelVideoScanner.extractModelCode(""))
    }

    @Test
    fun testReportVideosRequestBodyMatchesExactFieldNames() {
        // Required body:
        // {"videos": [
        //   {"model_code": "ANGLINA", "file_name": "anglina_1107.mp4", "size_bytes": 4200000,
        //    "sha256": "<64 hex characters>", "file_date": "<ISO 8601 time of DATE_MODIFIED>"}
        // ]}
        val items = listOf(
            com.example.portal.model.ModelVideoItem(
                modelCode = "ANGLINA",
                fileName = "anglina_1107.mp4",
                sizeBytes = 4200000L,
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                fileDate = "2026-11-07T10:30:00Z"
            )
        )
        val request = com.example.portal.model.ReportVideosRequest(videos = items)

        val adapter = moshi.adapter(com.example.portal.model.ReportVideosRequest::class.java)
        val json = adapter.toJson(request)
        val jsonObject = JSONObject(json)

        assertTrue(jsonObject.has("videos"))
        val videosArray = jsonObject.getJSONArray("videos")
        assertEquals(1, videosArray.length())

        val firstVideo = videosArray.getJSONObject(0)
        assertTrue(firstVideo.has("model_code"))
        assertTrue(firstVideo.has("file_name"))
        assertTrue(firstVideo.has("size_bytes"))
        assertTrue(firstVideo.has("sha256"))
        assertTrue(firstVideo.has("file_date"))

        assertEquals("ANGLINA", firstVideo.getString("model_code"))
        assertEquals("anglina_1107.mp4", firstVideo.getString("file_name"))
        assertEquals(4200000L, firstVideo.getLong("size_bytes"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", firstVideo.getString("sha256"))
        assertEquals("2026-11-07T10:30:00Z", firstVideo.getString("file_date"))

        // Empty list test
        val emptyRequest = com.example.portal.model.ReportVideosRequest(videos = emptyList())
        val emptyJson = adapter.toJson(emptyRequest)
        val emptyJsonObject = JSONObject(emptyJson)
        assertEquals(0, emptyJsonObject.getJSONArray("videos").length())
    }

    @Test
    fun testReportVideosSuccessResponseMatchesExactFieldNames() {
        // Required 200 answer: {"ok": true, "saved": 2, "unknown_codes": ["JOLIE"]}
        val rawJson = """
            {
                "ok": true,
                "saved": 2,
                "unknown_codes": ["JOLIE"]
            }
        """.trimIndent()

        val adapter = moshi.adapter(com.example.portal.model.ReportVideosResponse::class.java)
        val response = adapter.fromJson(rawJson)

        assertNotNull(response)
        assertTrue(response?.ok == true)
        assertEquals(2, response?.saved)
        assertEquals(1, response?.unknownCodes?.size)
        assertEquals("JOLIE", response?.unknownCodes?.first())
    }

    @Test
    fun testGetWorkResponseMatchesExactFieldNames() {
        val rawJson = """
            {
                "server_time": "2026-11-07T12:00:00Z",
                "device_name": "Phone 01",
                "script": {
                    "version": 1,
                    "name": "Standard Story Flow",
                    "steps": [
                        {"type": "REPORT", "text": "opening_facebook"},
                        {"type": "LAUNCH_APP", "packageName": "com.facebook.katana"}
                    ]
                },
                "slots": [
                    {
                        "slot_number": 1,
                        "account_label": "AQIB JAV",
                        "tasks": [
                            {
                                "uuid": "task-uuid-abc-123",
                                "campaign_uuid": "camp-uuid-999",
                                "status": "queued",
                                "slot_number": 1,
                                "account_label": "AQIB JAV",
                                "page_name": "Angelina Jolie Daily",
                                "model_code": "ANGLINA",
                                "link": "https://example.com/anglina",
                                "starts_at": null,
                                "attempt": 0
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(com.example.portal.model.GetWorkResponse::class.java)
        val response = adapter.fromJson(rawJson)

        assertNotNull(response)
        assertEquals("2026-11-07T12:00:00Z", response?.serverTime)
        assertEquals("Phone 01", response?.deviceName)
        assertNotNull(response?.script)
        assertEquals(1, response?.script?.version)
        assertEquals("Standard Story Flow", response?.script?.name)
        assertEquals(2, response?.script?.steps?.size)

        assertEquals(1, response?.slots?.size)
        val slot = response!!.slots[0]
        assertEquals(1, slot.slotNumber)
        assertEquals("AQIB JAV", slot.accountLabel)
        assertEquals(1, slot.tasks.size)

        val task = slot.tasks[0]
        assertEquals("task-uuid-abc-123", task.uuid)
        assertEquals("camp-uuid-999", task.campaignUuid)
        assertEquals("queued", task.status)
        assertEquals(1, task.slotNumber)
        assertEquals("AQIB JAV", task.accountLabel)
        assertEquals("Angelina Jolie Daily", task.pageName)
        assertEquals("ANGLINA", task.modelCode)
        assertEquals("https://example.com/anglina", task.link)
        assertNull(task.startsAt)
        assertEquals(0, task.attempt)
    }

    @Test
    fun testReportEventsRequestMatchesExactFieldNames() {
        val req = com.example.portal.model.ReportEventsRequest(
            events = listOf(
                com.example.portal.model.TaskEvent(
                    step = "opening_facebook",
                    message = null,
                    at = "2026-11-07T12:05:00Z"
                )
            )
        )

        val adapter = moshi.adapter(com.example.portal.model.ReportEventsRequest::class.java)
        val json = adapter.toJson(req)
        val jsonObject = JSONObject(json)

        assertTrue(jsonObject.has("events"))
        val array = jsonObject.getJSONArray("events")
        assertEquals(1, array.length())

        val event = array.getJSONObject(0)
        assertEquals("opening_facebook", event.getString("step"))
        assertEquals("2026-11-07T12:05:00Z", event.getString("at"))
    }

    @Test
    fun testFinishTaskRequestMatchesExactFieldNames() {
        val req = com.example.portal.model.FinishTaskRequest(
            result = "shared",
            step = "Share button tapped",
            error = null,
            seenTexts = listOf("Create story", "Share to story"),
            at = "2026-11-07T12:10:00Z"
        )

        val adapter = moshi.adapter(com.example.portal.model.FinishTaskRequest::class.java)
        val json = adapter.toJson(req)
        val jsonObject = JSONObject(json)

        assertEquals("shared", jsonObject.getString("result"))
        assertEquals("Share button tapped", jsonObject.getString("step"))
        assertEquals("2026-11-07T12:10:00Z", jsonObject.getString("at"))
        assertTrue(jsonObject.has("seen_texts"))
        assertEquals(2, jsonObject.getJSONArray("seen_texts").length())
    }
}

package com.example.portal

import com.example.portal.model.EnrollRequest
import com.example.portal.model.EnrollSuccessResponse
import com.example.portal.model.HeartbeatRequest
import com.example.portal.model.HeartbeatSuccessResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface PortalApi {

    @Headers("Accept: application/json")
    @POST("api/device/enroll")
    suspend fun enrollDevice(
        @Body request: EnrollRequest
    ): Response<EnrollSuccessResponse>

    @Headers("Accept: application/json")
    @POST("api/device/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") authHeader: String,
        @Body request: HeartbeatRequest
    ): Response<HeartbeatSuccessResponse>

    @Headers("Accept: application/json")
    @POST("api/device/videos")
    suspend fun reportVideos(
        @Header("Authorization") authHeader: String,
        @Body request: com.example.portal.model.ReportVideosRequest
    ): Response<com.example.portal.model.ReportVideosResponse>

    @Headers("Accept: application/json")
    @retrofit2.http.GET("api/device/work")
    suspend fun getWork(
        @Header("Authorization") authHeader: String
    ): Response<com.example.portal.model.GetWorkResponse>

    @Headers("Accept: application/json")
    @POST("api/device/tasks/{uuid}/start")
    suspend fun startTask(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("uuid") uuid: String
    ): Response<okhttp3.ResponseBody>

    @Headers("Accept: application/json")
    @POST("api/device/tasks/{uuid}/events")
    suspend fun sendTaskEvents(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("uuid") uuid: String,
        @Body request: com.example.portal.model.ReportEventsRequest
    ): Response<com.example.portal.model.ReportEventsResponse>

    @Headers("Accept: application/json")
    @POST("api/device/tasks/{uuid}/finish")
    suspend fun finishTask(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("uuid") uuid: String,
        @Body request: com.example.portal.model.FinishTaskRequest
    ): Response<okhttp3.ResponseBody>

    @retrofit2.http.Multipart
    @POST("api/device/tasks/{uuid}/screenshot")
    suspend fun uploadScreenshot(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("uuid") uuid: String,
        @retrofit2.http.Part screenshot: okhttp3.MultipartBody.Part
    ): Response<okhttp3.ResponseBody>

    @Headers("Accept: application/json")
    @POST("api/device/commands/{uuid}/result")
    suspend fun reportCommandResult(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("uuid") uuid: String,
        @Body request: com.example.portal.model.DeviceCommandResultRequest
    ): Response<okhttp3.ResponseBody>

    @Headers("Accept: application/json")
    @retrofit2.http.GET("api/device/story-videos")
    suspend fun getStoryVideos(
        @Header("Authorization") authHeader: String
    ): Response<com.example.portal.model.StoryVideosResponse>

    @Headers("Accept: application/json")
    @POST("api/device/story-videos/{uuid}/downloaded")
    suspend fun reportStoryVideoDownloaded(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("uuid") uuid: String,
        @Body request: com.example.portal.model.StoryVideoDownloadedRequest
    ): Response<okhttp3.ResponseBody>
}

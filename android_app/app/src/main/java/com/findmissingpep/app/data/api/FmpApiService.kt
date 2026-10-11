package com.findmissingpep.app.data.api

import com.findmissingpep.app.data.model.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface FmpApiService {

    // ─── Authentication & Users ─────────────────────────────────────────────

    @POST("api/auth/verify")
    suspend fun verifyToken(): UserResponseDto

    @GET("api/auth/me")
    suspend fun getAuthMe(): UserResponseDto

    @POST("api/users/")
    suspend fun createUser(@Body body: UserCreateDto): UserResponseDto

    @GET("api/users/me")
    suspend fun getUserMe(): UserResponseDto

    @PUT("api/users/me")
    suspend fun updateUser(@Body body: UserUpdateDto): UserResponseDto

    // ─── Missing Person Reports ─────────────────────────────────────────────

    @Multipart
    @POST("api/reports/")
    suspend fun submitReport(
        @Part("report_data") reportData: RequestBody,
        @Part photos: List<MultipartBody.Part>,
        @Part firDocument: MultipartBody.Part? = null
    ): MissingPersonResponseDto

    @GET("api/reports/")
    suspend fun getMyReports(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): MissingPersonListResponseDto

    @GET("api/reports/all")
    suspend fun getAllReports(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): MissingPersonListResponseDto

    @GET("api/reports/{id}")
    suspend fun getReport(@Path("id") id: String): MissingPersonResponseDto

    @PUT("api/reports/{id}")
    suspend fun updateReport(
        @Path("id") id: String,
        @Body body: MissingPersonUpdateDto
    ): MissingPersonResponseDto

    @DELETE("api/reports/{id}")
    suspend fun deleteReport(@Path("id") id: String): DeleteResponseDto

    @Multipart
    @POST("api/reports/{id}/photos")
    suspend fun uploadPhoto(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("is_primary") isPrimary: RequestBody
    ): PhotoResponseDto

    @GET("api/reports/{id}/sightings")
    suspend fun getReportSightings(@Path("id") id: String): List<SightingResponseDto>

    @GET("api/reports/{id}/timeline")
    suspend fun getReportTimeline(@Path("id") id: String): PersonTimelineDto

    // ─── Sightings ──────────────────────────────────────────────────────────

    @GET("api/sightings/")
    suspend fun getSightings(
        @Query("limit") limit: Int = 50,
        @Query("status_filter") statusFilter: String? = null
    ): List<SightingResponseDto>

    @GET("api/sightings/{id}")
    suspend fun getSighting(@Path("id") id: String): SightingResponseDto

    @PUT("api/sightings/{id}/confirm")
    suspend fun confirmSighting(
        @Path("id") id: String,
        @Body body: SightingReviewDto
    ): SightingResponseDto

    @PUT("api/sightings/{id}/reject")
    suspend fun rejectSighting(
        @Path("id") id: String,
        @Body body: SightingReviewDto
    ): SightingResponseDto

    // ─── FIR Validation & Review ────────────────────────────────────────────

    @GET("api/fir/validate/{fir_number}")
    suspend fun validateFir(@Path("fir_number") firNumber: String): FIRValidateResponseDto

    @GET("api/fir/pending")
    suspend fun getPendingFir(): List<MissingPersonResponseDto>

    @POST("api/fir/verify/{id}")
    suspend fun verifyFir(
        @Path("id") id: String,
        @Body body: FIRVerifyRequestDto
    ): MissingPersonResponseDto

    @POST("api/fir/reject/{id}")
    suspend fun rejectFir(
        @Path("id") id: String,
        @Body body: FIRRejectRequestDto
    ): MissingPersonResponseDto

    // ─── Notifications ──────────────────────────────────────────────────────

    @GET("api/notifications/")
    suspend fun getNotifications(
        @Query("unread_only") unreadOnly: Boolean = false,
        @Query("limit") limit: Int = 50
    ): NotificationListResponseDto

    @GET("api/notifications/unread-count")
    suspend fun getUnreadCount(): UnreadCountDto

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): NotificationResponseDto

    @PUT("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): MarkAllReadResponseDto
}

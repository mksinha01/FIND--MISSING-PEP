package com.findmissingpep.app.domain.repository

import com.findmissingpep.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Current authenticated user as a reactive stream */
    val currentUser: Flow<User?>

    /** Whether user is authenticated */
    val isAuthenticated: Flow<Boolean>

    /** Exchange Firebase ID token with backend and sync profile */
    suspend fun verifyToken(firebaseToken: String): Result<User>

    /** Get authenticated user profile from backend */
    suspend fun getProfile(): Result<User>

    /** Create / sync user profile on backend */
    suspend fun createProfile(
        firebaseUid: String,
        name: String,
        email: String?,
        phone: String?,
        language: String
    ): Result<User>

    /** Update user profile (name, phone, language, fcm_token) */
    suspend fun updateProfile(
        name: String? = null,
        phone: String? = null,
        language: String? = null,
        fcmToken: String? = null
    ): Result<User>

    /** Sign out from Firebase and clear local state */
    suspend fun signOut()

    /** Save Firebase token locally for auth interceptor */
    suspend fun saveFirebaseToken(token: String)

    /** Get stored Firebase token */
    suspend fun getFirebaseToken(): String?
}

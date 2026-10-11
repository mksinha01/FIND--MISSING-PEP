package com.findmissingpep.app.data.repository

import com.findmissingpep.app.data.api.FmpApiService
import com.findmissingpep.app.data.api.TokenProvider
import com.findmissingpep.app.data.model.dto.UserCreateDto
import com.findmissingpep.app.data.model.dto.UserUpdateDto
import com.findmissingpep.app.data.model.mapper.toDomain
import com.findmissingpep.app.domain.model.User
import com.findmissingpep.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: FmpApiService,
    private val tokenProvider: TokenProvider,
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser

    override val isAuthenticated: Flow<Boolean> = _currentUser.map { it != null }

    override suspend fun verifyToken(firebaseToken: String): Result<User> = runCatching {
        tokenProvider.saveToken(firebaseToken)
        val response = api.verifyToken()
        val user = response.toDomain()
        _currentUser.value = user
        user
    }

    override suspend fun getProfile(): Result<User> = runCatching {
        val response = api.getUserMe()
        val user = response.toDomain()
        _currentUser.value = user
        user
    }

    override suspend fun createProfile(
        firebaseUid: String,
        name: String,
        email: String?,
        phone: String?,
        language: String
    ): Result<User> = runCatching {
        val response = api.createUser(
            UserCreateDto(
                firebaseUid = firebaseUid,
                name = name,
                email = email,
                phone = phone,
                language = language
            )
        )
        val user = response.toDomain()
        _currentUser.value = user
        user
    }

    override suspend fun updateProfile(
        name: String?,
        phone: String?,
        language: String?,
        fcmToken: String?
    ): Result<User> = runCatching {
        val response = api.updateUser(
            UserUpdateDto(
                name = name,
                phone = phone,
                language = language,
                fcmToken = fcmToken
            )
        )
        val user = response.toDomain()
        _currentUser.value = user
        user
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        tokenProvider.clearToken()
        _currentUser.value = null
    }

    override suspend fun saveFirebaseToken(token: String) {
        tokenProvider.saveToken(token)
    }

    override suspend fun getFirebaseToken(): String? {
        return tokenProvider.getToken()
    }
}

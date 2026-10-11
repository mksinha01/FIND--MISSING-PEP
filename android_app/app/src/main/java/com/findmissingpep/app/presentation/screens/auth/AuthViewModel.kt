package com.findmissingpep.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.User
import com.findmissingpep.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: User? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Check if already logged in
        if (firebaseAuth.currentUser != null) {
            syncWithBackend()
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { handleFirebaseUser() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Login failed"
                )
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let { handleFirebaseUser(displayName = name) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Registration failed"
                )
            }
        }
    }

    fun signInWithGoogleCredential(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                handleFirebaseUser()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Google sign-in failed"
                )
            }
        }
    }

    private suspend fun handleFirebaseUser(displayName: String? = null) {
        val firebaseUser = firebaseAuth.currentUser ?: return
        val token = firebaseUser.getIdToken(true).await().token ?: return

        // 1. Verify token with backend
        val result = authRepository.verifyToken(token)

        result.fold(
            onSuccess = { user ->
                // 2. Register FCM token
                try {
                    val fcmToken = firebaseMessaging.token.await()
                    authRepository.updateProfile(fcmToken = fcmToken)
                } catch (_: Exception) { }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = user,
                    error = null
                )
            },
            onFailure = { e ->
                // First-time user: create profile
                val name = displayName ?: firebaseUser.displayName ?: "User"
                val createResult = authRepository.createProfile(
                    firebaseUid = firebaseUser.uid,
                    name = name,
                    email = firebaseUser.email,
                    phone = firebaseUser.phoneNumber,
                    language = "en"
                )
                createResult.fold(
                    onSuccess = { user ->
                        try {
                            val fcmToken = firebaseMessaging.token.await()
                            authRepository.updateProfile(fcmToken = fcmToken)
                        } catch (_: Exception) { }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            user = user,
                            error = null
                        )
                    },
                    onFailure = { err ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = err.localizedMessage ?: "Profile creation failed"
                        )
                    }
                )
            }
        )
    }

    private fun syncWithBackend() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val firebaseUser = firebaseAuth.currentUser ?: return@launch
                val token = firebaseUser.getIdToken(true).await().token ?: return@launch
                authRepository.saveFirebaseToken(token)

                val result = authRepository.getProfile()
                result.fold(
                    onSuccess = { user ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            user = user
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

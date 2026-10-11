package com.findmissingpep.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.User
import com.findmissingpep.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val selectedLanguage: String = "en",
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { u ->
                _uiState.update {
                    it.copy(
                        user = u,
                        selectedLanguage = u?.language ?: "en"
                    )
                }
            }
        }
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.getProfile().fold(
                onSuccess = { u ->
                    _uiState.update { it.copy(user = u, selectedLanguage = u.language, isLoading = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, selectedLanguage = languageCode) }
            authRepository.updateProfile(language = languageCode).fold(
                onSuccess = { updated ->
                    _uiState.update { it.copy(user = updated, isUpdating = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isUpdating = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }
}

package com.findmissingpep.app.presentation.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.BuildConfig
import com.findmissingpep.app.data.api.SseEventListener
import com.findmissingpep.app.data.model.dto.SseAlertData
import com.findmissingpep.app.data.model.mapper.toDomain
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.repository.AuthRepository
import com.findmissingpep.app.domain.repository.CaseRepository
import com.findmissingpep.app.domain.repository.NotificationRepository
import com.findmissingpep.app.domain.repository.SightingRepository
import com.findmissingpep.app.presentation.common.SnackbarManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSseConnected: Boolean = false,
    val activeCasesCount: Int = 0,
    val verifiedMatchesToday: Int = 0,
    val recentSightings: List<Sighting> = emptyList(),
    val myCases: List<MissingCase> = emptyList(),
    val unreadNotifications: Int = 0,
    val isOfficer: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val caseRepository: CaseRepository,
    private val sightingRepository: SightingRepository,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val sseEventListener: SseEventListener,
    private val json: Json,
    private val snackbarManager: SnackbarManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        observeUnreadCount()
        startSse()
        observeSseEvents()
        observeSseConnection()
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            notificationRepository.unreadCount.collect { count ->
                _uiState.update { it.copy(unreadNotifications = count) }
            }
        }
    }

    private fun startSse() {
        viewModelScope.launch {
            val token = authRepository.getFirebaseToken()
            if (!token.isNullOrBlank()) {
                sseEventListener.startListening(BuildConfig.API_BASE_URL, token)
            }
        }
    }

    private fun observeSseConnection() {
        viewModelScope.launch {
            sseEventListener.connectionState.collect { connState ->
                _uiState.update {
                    it.copy(isSseConnected = (connState == SseEventListener.ConnectionState.CONNECTED))
                }
            }
        }
    }

    private fun observeSseEvents() {
        viewModelScope.launch {
            sseEventListener.sightingAlerts.collect { rawJson ->
                try {
                    val alert = json.decodeFromString<SseAlertData>(rawJson)
                    val newSighting = alert.toDomain()
                    _uiState.update { state ->
                        val existingIds = state.recentSightings.map { it.id }.toSet()
                        if (newSighting.id in existingIds) return@update state

                        state.copy(
                            verifiedMatchesToday = state.verifiedMatchesToday + 1,
                            recentSightings = (listOf(newSighting) + state.recentSightings).take(10)
                        )
                    }
                    val personName = newSighting.personName ?: "person"
                    snackbarManager.showInfo("Live Alert: Sighting detected for $personName")
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Failed to decode SSE alert: ${e.message}", e)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadDashboardInternal()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadDashboardInternal()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadDashboardInternal() {
        // Fetch cases
        val casesResult = caseRepository.fetchMyCases(limit = 10)
        casesResult.onSuccess { paginated ->
            _uiState.update { state ->
                state.copy(
                    myCases = paginated.items,
                    activeCasesCount = paginated.items.count {
                        it.status.value == "ACTIVE" || it.status.value == "PROCESSING" || it.status.value == "PENDING_FIR_REVIEW"
                    }
                )
            }
        }

        // Fetch recent sightings
        val sightingsResult = sightingRepository.fetchSightings(limit = 5)
        sightingsResult.onSuccess { sightings ->
            _uiState.update { state ->
                state.copy(
                    recentSightings = sightings,
                    verifiedMatchesToday = sightings.count { it.status.value == "CONFIRMED" }
                )
            }
        }

        // Fetch unread count
        notificationRepository.fetchUnreadCount()

        // Check user role
        authRepository.currentUser.firstOrNull()?.let { user ->
            _uiState.update { it.copy(isOfficer = user.role.value != "citizen") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseEventListener.stopListening()
    }
}
